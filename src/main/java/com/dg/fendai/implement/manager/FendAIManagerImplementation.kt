package com.dg.fendai.implement.manager

import android.content.Context
import android.net.Uri
import android.util.Log
import com.dg.fendai.core.LlamaEngine
import com.dg.fendai.core.RagEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext


class FendAIManagerImplementation : FendAIManager {

    private val instance = LlamaEngine()
    private val modelScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var modelInitJob: Job? = null
    private var isInstanceLoaded = false

    /**
     * Native retriever plus its configuration. Held inside [knowledgeLock] because the native
     * index has no internal synchronization and [sendMessage] reads it from the caller's thread.
     */
    private val knowledgeLock = Any()
    private val knowledgeLoadLock = Any()
    private var knowledge: RagEngine? = null
    private var ragEnabled = false
    private var ragTopK = DEFAULT_RAG_TOP_K
    private var ragMaxContextCharacters = DEFAULT_RAG_MAX_CONTEXT_CHARACTERS
    private var ragEmbeddingModelPath: String? = null
    private var ragStopWords: Set<String>? = null

    private data class GroundedRequest(
        val prompt: String,
        val allowedCitationIds: Set<Int>,
    )

    /**
     * Sends a message to the Llama model and retrieves the complete response.
     *
     * This function communicates with the underlying [LlamaEngine] to get a response for the given
     * message. It collects the response as a stream of text pieces and concatenates them into a
     * single string. The operation is cancellable and handles exceptions gracefully by returning
     * any partial result collected before the interruption or error.
     *
     * When retrieval grounding is on and the query matches something, the prompt sent to the model
     * is [RagEngine.buildPrompt] output instead of [message]. If no chunks match, the optional
     * general-knowledge fallback sends the original question. Retrieval failures also fall back
     * to [message].
     *
     * @param message The message string to be sent to the model.
     * @return A [String] containing the full response from the model. If an error or cancellation
     * occurs, it may return a partial response or an error message appended to the result.
     */
    override suspend fun sendMessage(message: String): String =
        sendMessage(message, RagEngine.AnswerMode.DOCUMENT_ONLY, false)

    override suspend fun sendMessage(
        message: String,
        allowGeneralKnowledgeWhenNoMatch: Boolean,
    ): String = sendMessage(
        message,
        RagEngine.AnswerMode.DOCUMENT_ONLY,
        allowGeneralKnowledgeWhenNoMatch,
    )

    override suspend fun sendMessage(
        message: String,
        answerMode: RagEngine.AnswerMode,
    ): String = sendMessage(message, answerMode, false)

    override suspend fun sendMessage(
        message: String,
        answerMode: RagEngine.AnswerMode,
        allowGeneralKnowledgeWhenNoMatch: Boolean,
    ): String = coroutineScope {
        val result = StringBuilder()
        val grounded = groundedPrompt(message, allowGeneralKnowledgeWhenNoMatch, answerMode)
        val prompt = grounded?.prompt ?: message

        try {
            instance.getResponseAsFlow(prompt)
                .onEach { piece ->
                    ensureActive()
                    result.append(piece)
                }
                .catch {  e -> result.append(e.toString()) }
                .collect()

        } catch (cancel: CancellationException) {
            println(cancel)
            return@coroutineScope result.toString()
        } catch (e: Exception) {
            println(e)
            return@coroutineScope result.toString()
        }
        grounded?.let { validateCitations(result.toString(), it.allowedCitationIds) }
            ?: result.toString()
    }

    /**
     * Returns the grounded prompt for [query], or null when retrieval is off, nothing is indexed,
     * or retrieval failed. Native calls are blocking, so keep this off the main thread.
     */
    private fun groundedPrompt(
        query: String,
        allowGeneralKnowledgeWhenNoMatch: Boolean,
        answerMode: RagEngine.AnswerMode,
    ): GroundedRequest? =
        synchronized(knowledgeLock) {
        if (!ragEnabled) return@synchronized null
        val engine = knowledge ?: return@synchronized null
        if (ragTopK <= 0) return@synchronized null
        try {
            val prompt = engine.buildPrompt(
                query,
                ragTopK,
                ragMaxContextCharacters,
                allowGeneralKnowledgeWhenNoMatch,
                answerMode,
            )
            val allowedCitationIds = Regex("(?m)^\\[(\\d+)] \\[Source:")
                .findAll(prompt)
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .toSet()
            GroundedRequest(prompt, allowedCitationIds)
        } catch (e: Exception) {
            Log.e("FendAIManager", "RAG grounding failed, answering without context", e)
            if (ragEmbeddingModelPath != null) throw e
            null
        }
    }

    /**
     * The helper method validateCitations in  FendAIManagerImplementation.kt is responsible for verifying and post-processing citations generated during RAG (Retrieval-Augmented Generation) grounding.
     * 1. **Validation Check**:
     *    It delegates the actual validation logic to [`RagCitationValidator.validate(...)`](method://com.dg.fendai.implement.manager.RagCitationValidator#validate), which extracts citation brackets (e.g. `[1]`, `[2]`) from the generated model answer (`answer`) and checks them against the set of valid/allowed citation IDs (`allowedCitationIds`).
     *
     * 2. **Handling Warnings**:
     *    - **No Warning (`warning == null`)**: If all citations are valid (or none needed when empty), it returns the original answer unmodified (`validation.answer`).
     *    - **With Warning**: If there are invalid citation IDs or missing required citations:
     *      - It logs the warning using `Log.w(...)` under the `"FendAIManager"` tag.
     *      - If the model's answer is completely blank, it returns the warning text directly.
     *      - Otherwise, it appends the grounding warning to the bottom of the answer enclosed in brackets (`\n\n[$warning]`).
     */
    private fun validateCitations(answer: String, allowedCitationIds: Set<Int>): String {
        val validation = RagCitationValidator.validate(answer, allowedCitationIds)
        val warning = validation.warning ?: return validation.answer
        Log.w("FendAIManager", warning)
        return if (validation.answer.isBlank()) warning else "${validation.answer}\n\n[$warning]"
    }

    /**
     * Adds a user message to the conversation history. This message will be considered
     * as part of the context for the next call to `sendMessage`.
     *
     * @param message The user's message content as a `String`.
     */
    override fun addUserMessage(message: String) {
        instance.addUserMessage(message)
    }

    /**
     * Loads the Llama model from the specified path with the given parameters.
     * This operation is performed asynchronously on a background thread.
     *
     * @param modelPath The file path to the GGUF model to be loaded.
     * @param params The inference parameters for the model.
     * @param onError A callback function to be invoked if an error occurs during model loading.
     * @param systemPrompt The system prompt to be used (currently unused in the implementation).
     * @param onSuccess A callback function to be invoked upon successful completion (currently unused in the implementation).
     * @throws Exception if an error occurs during the model loading process.
     */
    override fun loadModel(
        modelPath: String,
        params: LlamaEngine.InferenceParams,
        onError: (Exception) -> Unit,
        onSuccess: (Boolean) -> Unit
    ) {
        modelInitJob?.cancel()
        modelInitJob =
            modelScope.launch {
                try {
                    if (isInstanceLoaded) {
                        instance.free()
                        isInstanceLoaded = false
                    }

                    instance.load(modelPath, params)

                    withContext(Dispatchers.Main) {
                        isInstanceLoaded = true
                        onSuccess(true)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onError(e)
                    }
                }
            }
    }

    override fun addPrompt(prompt: String) {
        instance.addSystemPrompt(prompt)
    }

    override fun addMessageHistory(message: String) {
        instance.addAssistantMessage(message)
    }

    override fun checkGGUFFile(
        uri: Uri,
        context: Context,
    ): Boolean {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val gguMagicNumberBytes = ByteArray(4)
            inputStream.read(gguMagicNumberBytes)
            return gguMagicNumberBytes.contentEquals(byteArrayOf(71, 71, 85, 70))
        }
        return false
    }

    override fun clearHistoryChat() {
        instance.clearAllHistory()
    }

    override fun clearMemoryOnly() {
       instance.clearMemory()
    }

    override fun removeFromLastChat(count: Int) {
       instance.undoLastMessages(count)
    }

    override fun getCountChat() : Int {
       return instance.getTotalMessages()
    }

    override fun logAllMessageHistory() {
        instance.logAllMessageHistory()
    }

    override fun tokensUsed() {
        instance.getContextLengthUsed()
    }

    override fun setRagEnabled(enabled: Boolean) = synchronized(knowledgeLock) {
        ragEnabled = enabled
    }

    override fun isRagEnabled(): Boolean = synchronized(knowledgeLock) { ragEnabled }

    /**
     * Indexes knowledge on a background thread: reading a directory tree and scoring a query are
     * both native and blocking, so neither belongs on the caller's thread.
     */
    override fun loadKnowledge(path: String, onSuccess: (Int) -> Unit, onError: (Exception) -> Unit) {
        loadKnowledge(path, null, RagEngine.ChunkingOptions(), onSuccess, onError)
    }

    override fun loadKnowledge(
        path: String,
        chunkingOptions: RagEngine.ChunkingOptions,
        onSuccess: (Int) -> Unit,
        onError: (Exception) -> Unit,
    ) {
        loadKnowledge(path, null, chunkingOptions, onSuccess, onError)
    }

    override fun loadKnowledge(
        path: String,
        context: Context?,
        onSuccess: (Int) -> Unit,
        onError: (Exception) -> Unit,
    ) {
        loadKnowledge(path, context, RagEngine.ChunkingOptions(), onSuccess, onError)
    }

    override fun loadKnowledge(
        path: String,
        context: Context?,
        chunkingOptions: RagEngine.ChunkingOptions,
        onSuccess: (Int) -> Unit,
        onError: (Exception) -> Unit,
    ) {
        modelScope.launch {
            try {
                val count = synchronized(knowledgeLoadLock) {
                    val previous = synchronized(knowledgeLock) {
                        knowledge.also { knowledge = null }
                    }
                    previous?.close()

                    val engine = RagEngine()
                    try {
                        val embeddingModelPath = synchronized(knowledgeLock) {
                            ragEmbeddingModelPath
                        }
                        embeddingModelPath?.let(engine::loadEmbeddingModel)
                        val stopWords = synchronized(knowledgeLock) { ragStopWords }
                        stopWords?.let(engine::setStopWords)
                        val loadedChunks = engine.load(path, context, chunkingOptions)
                        synchronized(knowledgeLock) {
                            knowledge?.close()
                            knowledge = engine
                        }
                        loadedChunks
                    } catch (e: Exception) {
                        engine.close()
                        throw e
                    }
                }
                withContext(Dispatchers.Main) { onSuccess(count) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("FendAIManager", "Knowledge load failed for $path", e)
                withContext(Dispatchers.Main) { onError(e) }
            }
        }
    }

    override fun addKnowledge(source: String, text: String) = synchronized(knowledgeLock) {
        val engine = knowledge ?: RagEngine().also { created ->
            try {
                ragEmbeddingModelPath?.let(created::loadEmbeddingModel)
                ragStopWords?.let(created::setStopWords)
                knowledge = created
            } catch (e: Exception) {
                created.close()
                throw e
            }
        }
        engine.addDocument(source, text)
    }

    override fun setRagEmbeddingModel(
        modelPath: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit,
    ) {
        modelScope.launch {
            try {
                synchronized(knowledgeLoadLock) {
                    synchronized(knowledgeLock) {
                        val engine = knowledge ?: RagEngine()
                        try {
                            engine.loadEmbeddingModel(modelPath)
                            knowledge = engine
                            ragEmbeddingModelPath = modelPath
                        } catch (e: Exception) {
                            if (knowledge !== engine) engine.close()
                            throw e
                        }
                    }
                }
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("FendAIManager", "Failed to load RAG embedding model: $modelPath", e)
                withContext(Dispatchers.Main) { onError(e) }
            }
        }
    }

    override fun clearRagEmbeddingModel() {
        synchronized(knowledgeLoadLock) {
            synchronized(knowledgeLock) {
                knowledge?.clearEmbeddingModel()
                ragEmbeddingModelPath = null
            }
        }
    }

    override fun setRagStopWords(stopWords: Set<String>) {
        synchronized(knowledgeLoadLock) {
            synchronized(knowledgeLock) {
                knowledge?.setStopWords(stopWords)
                ragStopWords = stopWords.toSet()
            }
        }
    }

    override fun resetRagStopWords() {
        synchronized(knowledgeLoadLock) {
            synchronized(knowledgeLock) {
                knowledge?.resetStopWords()
                ragStopWords = null
            }
        }
    }

    override fun searchKnowledge(query: String, topK: Int): List<RagEngine.Result> =
        synchronized(knowledgeLock) { knowledge?.search(query, topK) ?: emptyList() }

    override fun knowledgeChunkCount(): Int = synchronized(knowledgeLock) { knowledge?.chunkCount ?: 0 }

    override fun releaseKnowledge() = synchronized(knowledgeLock) {
        knowledge?.close()
        knowledge = null
        ragEnabled = false
    }

    override fun unLoadModel() {
        instance.unloadModel()
    }

    companion object {
        /** Chunks retrieved per question when the caller does not set its own topK. */
        const val DEFAULT_RAG_TOP_K = 4

        /**
         * Ceiling on injected context. Raise it for a long-context model; at 12000 characters this
         * is roughly 3000 tokens, which fits a typical 4096-token window once the question and
         * system prompt are added.
         */
        const val DEFAULT_RAG_MAX_CONTEXT_CHARACTERS = 12_000
    }
}