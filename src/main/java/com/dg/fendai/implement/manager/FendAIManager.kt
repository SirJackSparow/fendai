package com.dg.fendai.implement.manager

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
}