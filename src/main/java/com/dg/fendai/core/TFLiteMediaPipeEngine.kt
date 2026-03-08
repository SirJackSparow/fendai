//package com.dg.fendai.core
//
//import android.content.Context
////import com.google.mediapipe.tasks.genai.llminference.LlmInference
//import java.io.File
//import android.util.Log
//import com.dg.fendai.core.TFLiteMediaPipeEngine.Companion.DEFAULT_MAX_TOKENS
//import com.dg.fendai.core.TFLiteMediaPipeEngine.Companion.DEFAULT_TEMPERATURE
//import com.dg.fendai.core.TFLiteMediaPipeEngine.Companion.DEFAULT_TOP_K
//import com.dg.fendai.core.TFLiteMediaPipeEngine.Companion.DEFAULT_TOP_P
//
//class TFLiteMediaPipeEngine(private val context: Context) {
//    private var llm: LlmInference? = null
//    private val chatHistory: MutableList<ChatMessage> = mutableListOf()
//    var systemPrompt = "Helpfull AI !"
//
//    companion object Companion {
//        const val TAG = "TFLiteEngine"
//        const val DEFAULT_TEMPERATURE = 0.7f
//        const val DEFAULT_TOP_K = 40
//        const val DEFAULT_TOP_P = 0.95f
//        const val DEFAULT_MAX_TOKENS = 512
//        const val MAX_HISTORY = 20
//    }
//
//    fun loadModel(modelPath: String, params: InferenceTFParams) {
//        if (!File(modelPath).exists()) throw IllegalStateException("There's no model: $modelPath")
//
//        val options = LlmInference.LlmInferenceOptions.builder()
//            .setModelPath(modelPath)
//            .setMaxTopK(params.topK)
//            .setMaxTokens(params.maxTokens)
//            .build()
//
//        llm = LlmInference.createFromOptions(context, options)
//        Log.d("TFLiteEngine", "Model loaded: ${File(modelPath).name}")
//    }
//
//    fun addMessage(history: ChatMessage) {
//        chatHistory.add(history)
//        if (chatHistory.size > MAX_HISTORY) chatHistory.removeAt(0)
//    }
//
//    fun setPrompt(prompt: String) {
//        systemPrompt = prompt
//    }
//
//    fun buildPrompt(
//        onComplete: (String) -> Unit,
//        onError: (String) -> Unit
//    ) {
//
//        var prompt = StringBuilder()
//        val instance = llm ?: throw IllegalStateException("Model not loaded.")
//
//        prompt.append("<|system|>\n$systemPrompt<|end|>\n")
//        chatHistory.forEach { msg ->
//            when (msg.role) {
//                Role.USER -> prompt.append("<|user|>\n${msg.content}<|end|>\n")
//                Role.ASSISTANT -> prompt.append("<|assistant|>\n${msg.content}<|end|>\n")
//                Role.SYSTEM -> prompt.append("${msg.content}\n")
//            }
//        }
//        prompt.append("<|assistant|>\n")
//
//        instance.generateResponseAsync(prompt.toString())
//
//        // Set listener in options during loadModel for streaming
//        // If you need per-call listener, rebuild options and reload (expensive)
//        // For now, sync fallback or use listener from creation
//        // Actual streaming via ResultListener set at creation time
//
//        // Simplified sync version (use this if async not critical)
//        try {
//            val result = instance.generateResponse(prompt.toString())
//            val clean = result.removeSuffix("<|end|>").trim()
//            addMessage(ChatMessage(Role.ASSISTANT, clean))
//            onComplete(clean)
//        } catch (e: Throwable) {
//            Log.e(TAG, "Generation error", e)
//            onError(e.message ?: "Unknown error")
//        }
//    }
//}
//
//data class InferenceTFParams(
//    val topK: Int = DEFAULT_TOP_K,
//    val maxTokens: Int = DEFAULT_MAX_TOKENS,
//)
//
//enum class Role { USER, ASSISTANT, SYSTEM }
//
//data class ChatMessage(val role: Role, val content: String)