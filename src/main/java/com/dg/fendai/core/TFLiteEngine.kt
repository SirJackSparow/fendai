package com.dg.fendai.core

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import android.util.Log

class TFLiteEngine(private val context: Context)  {
    private var llm: LlmInference? = null
    private val chatHistory: MutableList<ChatMessage> = mutableListOf()

    companion object {
        private const val TAG = "TFLiteEngine"
        private const val DEFAULT_TEMPERATURE = 0.7f
        private const val DEFAULT_TOP_K = 40
        private const val DEFAULT_TOP_P = 0.95f
        private const val DEFAULT_MAX_TOKENS = 512
    }

    data class InferenceParams(
        val temperature: Float = DEFAULT_TEMPERATURE,
        val topK: Int = DEFAULT_TOP_K,
        val topP: Float = DEFAULT_TOP_P,
        val maxTokens: Int = DEFAULT_MAX_TOKENS,
        val stopSequences: List<String> = listOf("<|end|>")
    )

    private fun privateModelMessage(message: ChatMessage) : String {
        return when(message.role) {
            Role.USER -> "<|user|>\n${message.content}<|end|>\n"
            Role.ASSISTANT -> "<|assistant|>\n${message.content}<|end|>\n"
            Role.SYSTEM -> message.content + "\n"
        }
    }

    fun loadModel(modelPath: String) {
        if (!File(modelPath).exists()) throw IllegalStateException("There's no model: $modelPath")

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .build()

        llm = LlmInference.createFromOptions(context, options)
        Log.d("TFLiteEngine", "Model loaded: ${File(modelPath).name}")
    }

}

enum class Role { USER, ASSISTANT, SYSTEM }

data class ChatMessage(val role: Role, val content: String)