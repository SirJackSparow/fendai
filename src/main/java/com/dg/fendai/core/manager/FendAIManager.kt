package com.dg.fendai.core.manager

import com.dg.fendai.core.LlamaEngine

interface FendAIManager {
    suspend fun sendMessage(message: String): String
    fun addUserMessage(message: String)
    fun loadModel(modelPath: String,
             params: LlamaEngine.InferenceParams = LlamaEngine.InferenceParams(),
             onError: (Exception) -> Unit,
             systemPrompt: String,
             onSuccess: (Boolean) -> Unit,
             )

}