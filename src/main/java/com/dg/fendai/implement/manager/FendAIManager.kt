package com.dg.fendai.implement.manager

import android.content.Context
import android.net.Uri
import com.dg.fendai.core.LlamaEngine

interface FendAIManager {
    suspend fun sendMessage(message: String): String
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
}