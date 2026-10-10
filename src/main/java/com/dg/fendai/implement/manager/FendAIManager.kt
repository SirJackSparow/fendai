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

    /** Adds one in-memory document to the current index. */
    fun addKnowledge(source: String, text: String)

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