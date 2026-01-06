package com.dg.fendai.implement.manager

import android.content.Context
import com.dg.fendai.core.ChatMessage
import com.dg.fendai.core.InferenceTFParams
import com.dg.fendai.core.TFLiteMediaPipeEngine

class FendAITFLiteManagerImplementation(val context: Context) : FendAITFLiteManager {

    val engine = TFLiteMediaPipeEngine(context)

    override fun uploadFileTFLite(prompt: String,model: String, params: InferenceTFParams) {
        engine.systemPrompt = prompt.ifEmpty { "Helpful AI" }
        engine.loadModel(model, params)
    }

    override fun sendMessage(
        message: ChatMessage,
        onToken: (String) -> Unit,
        onComplete: (String) -> Unit
    ) {
        engine.addMessage(message)
        engine.buildPrompt(onComplete = {
            onComplete(it)

        }, onError = {})
    }

    override fun setPrompt(prompt: String) {
        engine.setPrompt(prompt)
    }

}
