package com.dg.fendai.implement.manager

import android.content.Context
import android.net.Uri
import com.dg.fendai.core.LlamaEngine
import com.dg.fendai.core.RagEngine

interface FendAIManager {
    suspend fun sendMessage(message: String): String

    /** Backends may opt into a general-knowledge answer when retrieval returns no chunks. */
    suspend fun sendMessage(message: String, allowGeneralKnowledgeWhenNoMatch: Boolean): String =
        sendMessage(message)

    /** Sends a message with an explicit RAG answer policy. */
    suspend fun sendMessage(message: String, answerMode: RagEngine.AnswerMode): String =
        sendMessage(message)

    /** Sends a message with answer policy and legacy no-match fallback control. */
    suspend fun sendMessage(
        message: String,
        answerMode: RagEngine.AnswerMode,
        allowGeneralKnowledgeWhenNoMatch: Boolean,
    ): String = sendMessage(message, answerMode)

    fun addUserMessage(message: String)
    fun loadModel(modelPath: String,
             params: LlamaEngine.InferenceParams = LlamaEngine.InferenceParams(),
             onError: (Exception) -> Unit,
             onSuccess: (Boolean) -> Unit,
             )
    fun addPrompt(prompt: String)
    fun addMessageHistory(message: String)
    fun checkGGUFFile(
        uri: Uri,
        context: Context,
    ): Boolean

    fun clearHistoryChat()

    fun clearMemoryOnly()

    fun removeFromLastChat(count: Int)

    fun getCountChat() : Int

    fun logAllMessageHistory()

    fun tokensUsed()

    // RAG retrieval layer

    /**
     * Turns the per-message retrieval grounding on or off. When on, [sendMessage] replaces the
     * user question with a grounded prompt built from the indexed knowledge.
     */
    fun setRagEnabled(enabled: Boolean)

    fun isRagEnabled(): Boolean

    /**
     * Indexes one file or a directory tree. Loading clears whatever was indexed before, so call
     * [addKnowledge] after this to add in-memory material to the same index.
     *
     * @return The number of indexed chunks, delivered through [onSuccess].
     */
    fun loadKnowledge(path: String, onSuccess: (Int) -> Unit, onError: (Exception) -> Unit)

    /** Loads knowledge with an optional context required for PDF extraction. */
    fun loadKnowledge(
        path: String,
        context: Context?,
        onSuccess: (Int) -> Unit,
        onError: (Exception) -> Unit,
    ) = loadKnowledge(path, onSuccess, onError)

    /** Loads knowledge using the requested word-based chunk size and overlap. */
    fun loadKnowledge(
        path: String,
        context: Context?,
        chunkingOptions: RagEngine.ChunkingOptions,
        onSuccess: (Int) -> Unit,
        onError: (Exception) -> Unit,
    ) = loadKnowledge(path, context, onSuccess, onError)

    /** Loads non-PDF knowledge using custom word-based chunking options. */
    fun loadKnowledge(
        path: String,
        chunkingOptions: RagEngine.ChunkingOptions,
        onSuccess: (Int) -> Unit,
        onError: (Exception) -> Unit,
    ) = loadKnowledge(path, onSuccess, onError)

    /** Adds one in-memory document to the current index. */
    fun addKnowledge(source: String, text: String)

    /** Loads a local llama.cpp embedding GGUF model for hybrid semantic and BM25 search. */
    fun setRagEmbeddingModel(
        modelPath: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit,
    )

    /** Disables semantic search and returns the current index to BM25 retrieval. */
    fun clearRagEmbeddingModel()

    /** Replaces built-in English stopwords for retrieval; an empty set disables filtering. */
    fun setRagStopWords(stopWords: Set<String>)

    /** Restores the built-in English stopword list for retrieval. */
    fun resetRagStopWords()

    /** Returns the best matching chunks, strongest first. Empty when nothing is indexed. */
    fun searchKnowledge(query: String, topK: Int = 4): List<RagEngine.Result>

    /** Returns the number of indexed chunks. */
    fun knowledgeChunkCount(): Int

    /** Drops the index and releases the native retriever. Grounding reverts to the raw question. */
    fun releaseKnowledge()

    /**
     * unload model
     */
    fun unLoadModel()
}