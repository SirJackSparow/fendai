package com.dg.fendai.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A reader for GGUF (GPT-Generated Unified Format) model files.
 *
 * This class provides a high-level API to interact with GGUF model files using a native
 * backend library (`ggufreader`). It allows for loading a model and extracting key metadata,
 * such as the context size and the chat template.
 *
 * Before using any methods, the [load] function must be called to initialize the native context.
 *
 * Example usage:
 * ```kotlin
 * val reader = GGUFReader()
 * try {
 *     reader.load("/path/to/your/model.gguf")
 *     val contextSize = reader.getContextSize()
 *     val chatTemplate = reader.getChatTemplate()
 *     // Use the retrieved metadata...
 * } catch (e: Exception) {
 *     // Handle loading errors
 * }
 * ```
 */
class GGUFReader {
    companion object {
        init {
            System.loadLibrary("ggufreader")
        }
    }

    private var nativeHandle: Long = 0L

    suspend fun load(modelPath: String) =
        withContext(Dispatchers.IO) {
            nativeHandle = getGGUFContextNativeHandle(modelPath)
        }

    fun getContextSize() : Long? {
        assert(nativeHandle != 0L) { "Use GGUFReader.load() to initialize the reader" }
        val contextSize = getContextSize(nativeHandle)
        return if (contextSize == -1L) {
            null
        } else {
            contextSize
        }
    }

    fun getChatTemplate(): String? {
        assert(nativeHandle != 0L) { "Use GGUFReader.load() to initialize the reader" }
        val chatTemplate = getChatTemplate(nativeHandle)
        return chatTemplate.ifEmpty {
            null
        }
    }

    private external fun getGGUFContextNativeHandle(modelPath: String): Long
    private external fun getContextSize(nativeHandle: Long): Long
    private external fun getChatTemplate(nativeHandle: Long): String
}