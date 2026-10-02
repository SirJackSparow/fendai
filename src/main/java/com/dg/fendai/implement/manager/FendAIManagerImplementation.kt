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
    private var knowledge: RagEngine? = null
    private var ragEnabled = false
    private var ragTopK = DEFAULT_RAG_TOP_K
    private var ragMaxContextCharacters = DEFAULT_RAG_MAX_CONTEXT_CHARACTERS

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
    override suspend fun sendMessage(message: String): String = sendMessage(message, false)

    /**
     * Sends the original question when flexible fallback is enabled and no chunks match.
     *
     * @param message The message string to be sent to the model.
     * @param allowGeneralKnowledgeWhenNoMatch Whether an unmatched query may use model knowledge.
     */
    override suspend fun sendMessage(
        message: String,
        allowGeneralKnowledgeWhenNoMatch: Boolean,
    ): String = coroutineScope {
        val result = StringBuilder()
        val prompt = groundedPrompt(message, allowGeneralKnowledgeWhenNoMatch) ?: message

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
        result.toString()
    }

    /**
     * Returns the grounded prompt for [query], or null when retrieval is off, nothing is indexed,
     * or retrieval failed. Native calls are blocking, so keep this off the main thread.
     */
    private fun groundedPrompt(query: String, allowGeneralKnowledgeWhenNoMatch: Boolean): String? =
        synchronized(knowledgeLock) {
        if (!ragEnabled) return@synchronized null
        val engine = knowledge ?: return@synchronized null
        if (ragTopK <= 0) return@synchronized null
        try {
            engine.buildPrompt(
                query,
                ragTopK,
                ragMaxContextCharacters,
                allowGeneralKnowledgeWhenNoMatch,
            )
        } catch (e: Exception) {
            Log.e("FendAIManager", "RAG grounding failed, answering without context", e)
            null
        }
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
        modelScope.launch {
            try {
                // loadPath clears the old index, so swap in a fresh engine instead of reusing this.
                val engine = RagEngine()
                val count = engine.load(path)
                synchronized(knowledgeLock) {
                    knowledge?.close()
                    knowledge = engine
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
        val engine = knowledge ?: RagEngine().also { knowledge = it }
        engine.addDocument(source, text)
    }

    override fun searchKnowledge(query: String, topK: Int): List<RagEngine.Result> =
        synchronized(knowledgeLock) { knowledge?.search(query, topK) ?: emptyList() }

    override fun knowledgeChunkCount(): Int = synchronized(knowledgeLock) { knowledge?.chunkCount ?: 0 }

    override fun releaseKnowledge() = synchronized(knowledgeLock) {
        knowledge?.close()
        knowledge = null
        ragEnabled = false
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