package com.dg.fendai.core

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference

class TFLiteEngine(private val context: Context) {
    private val llm: LlmInference? = null
}

data class Configuration(
    val temperature: Float = 0.8f,           // 0.0 = greedy, >1.0 = lebih random
    val topK: Int = 40,                      // Top-K sampling
    val repetitionPenalty: Float = 1.1f,     // Penalti pengulangan token (1.0 = no penalty)
    val maxTokens: Int = 512,                // Maksimal token yang di-generate
    val seed: Long? = null                   // Optional seed untuk hasil reproducible
)