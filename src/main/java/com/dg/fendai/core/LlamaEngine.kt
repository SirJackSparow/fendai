package com.dg.fendai.core

import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

class LlamaEngine {
    companion object Companion {
        init {
            val logTag = LlamaEngine::class.java.simpleName

            // check if the following CPU features are available,
            // and load the native library accordingly
            val cpuFeatures = getCPUFeatures()
            val hasFp16 = cpuFeatures.contains("fp16") || cpuFeatures.contains("fphp")
            val hasDotProd = cpuFeatures.contains("dotprod") || cpuFeatures.contains("asimddp")
            val hasSve = cpuFeatures.contains("sve")
            val hasI8mm = cpuFeatures.contains("i8mm")
            val isAtLeastArmV82 =
                cpuFeatures.contains("asimd") &&
                        cpuFeatures.contains("crc32") &&
                        cpuFeatures.contains(
                            "aes",
                        )
            val isAtLeastArmV84 = cpuFeatures.contains("dcpop") && cpuFeatures.contains("uscat")

            Log.d(logTag, "CPU features: $cpuFeatures")
            Log.d(logTag, "- hasFp16: $hasFp16")
            Log.d(logTag, "- hasDotProd: $hasDotProd")
            Log.d(logTag, "- hasSve: $hasSve")
            Log.d(logTag, "- hasI8mm: $hasI8mm")
            Log.d(logTag, "- isAtLeastArmV82: $isAtLeastArmV82")
            Log.d(logTag, "- isAtLeastArmV84: $isAtLeastArmV84")

            // This block of code checks if the application is running on an Android emulator
            // rather than a physical device.
            // It's important to note that the method used here is not the officially
            // recommended way to detect emulators, as it relies on specific hardware
            // identifiers ("goldfish" or "ranchu") that are commonly used by emulators
            // but could potentially change or be inaccurate in some scenarios.
            val isEmulated =
                (Build.HARDWARE.contains("goldfish") || Build.HARDWARE.contains("ranchu"))
            Log.d(logTag, "isEmulated: $isEmulated")

            // If the app is NOT running on an emulator, it proceeds to load a native library
            // optimized for the specific CPU architecture of the device.
            if (!isEmulated) {
                if (supportsArm64V8a()) {
                    if (isAtLeastArmV84 && hasSve && hasI8mm && hasFp16 && hasDotProd) {
                        Log.d(logTag, "Loading chatandroimedax_v8_4_fp16_dotprod_i8mm_sve.so")
                        System.loadLibrary("llama_android_v8_4_fp16_dotprod_i8mm_sve")
                    } else if (isAtLeastArmV84 && hasSve && hasFp16 && hasDotProd) {
                        Log.d(logTag, "Loading chatandroimedax_v8_4_fp16_dotprod_sve.so")
                        System.loadLibrary("llama_android_v8_4_fp16_dotprod_sve")
                    } else if (isAtLeastArmV84 && hasI8mm && hasFp16 && hasDotProd) {
                        Log.d(logTag, "Loading chatandroimedax_v8_4_fp16_dotprod_i8mm.so")
                        System.loadLibrary("llama_android_v8_4_fp16_dotprod_i8mm")
                    } else if (isAtLeastArmV84 && hasFp16 && hasDotProd) {
                        Log.d(logTag, "Loading chatandroimedax_v8_4_fp16_dotprod.so")
                        System.loadLibrary("llama_android_v8_4_fp16_dotprod")
                    } else if (isAtLeastArmV82 && hasFp16 && hasDotProd) {
                        Log.d(logTag, "Loading chatandroimedax_v8_2_fp16_dotprod.so")
                        System.loadLibrary("llama_android_v8_2_fp16_dotprod")
                    } else if (isAtLeastArmV82 && hasFp16) {
                        Log.d(logTag, "Loading chatandroimedax_v8_2_fp16.so")
                        System.loadLibrary("llama_android_v8_2_fp16")
                    } else {
                        Log.d(logTag, "Loading chatandroimedax_v8.so")
                        System.loadLibrary("llama_android_v8")
                    }
                } else if (Build.SUPPORTED_32_BIT_ABIS[0]?.equals("armeabi-v7a") == true) {
                    // armv7a (32bit) device
                    Log.d(logTag, "Loading chatandroimedax_v7a.so")
                    System.loadLibrary("llama_android_v7a")
                } else {
                    Log.d(logTag, "Loading default chatandroimedax.so")
                    System.loadLibrary("llama_android")
                }
            // If the app IS running on an emulator, it loads a default, non-optimized version
            // of the native library. This is likely because emulators might not accurately
            // report or support all specific CPU features, or for simplicity.
            } else {
                // load the default native library with no ARM
                // specific instructions
                Log.d(logTag, "Loading default chatandroimedax.so")
                System.loadLibrary("llama_android")
            }
        }

        /**
         * Reads the /proc/cpuinfo file and returns the line
         * starting with 'Features :' that containing the available
         * CPU features
         */
        private fun getCPUFeatures(): String {
            val cpuInfo =
                try {
                    File("/proc/cpuinfo").readText()
                } catch (e: FileNotFoundException) {
                    ""
                }
            val cpuFeatures =
                cpuInfo
                    .substringAfter("Features")
                    .substringAfter(":")
                    .substringBefore("\n")
                    .trim()
            return cpuFeatures
        }

        private fun supportsArm64V8a(): Boolean = Build.SUPPORTED_ABIS[0].equals("arm64-v8a")
    }

    private var nativePtr = 0L

    /**
     * Provides default values for inference parameters.
     * These values are used when the corresponding parameters are not provided
     * by the user or are not available in the GGUF model file.
     */
    object DefaultInferenceParams {
        val contextSize: Long = 1024L
        val chatTemplate: String =
            "{% for message in messages %}{% if loop.first and messages[0]['role'] != 'system' %}{{ '<|im_start|>system You are a helpful AI assistant named Model, trained by Hugging Face<|im_end|> ' }}{% endif %}{{'<|im_start|>' + message['role'] + ' ' + message['content'] + '<|im_end|>' + ' '}}{% endfor %}{% if add_generation_prompt %}{{ '<|im_start|>assistant ' }}{% endif %}"
    }

    /**
     * Configuration parameters for the Llama inference engine.
     *
     * @property minP Minimum probability threshold for sampling (Min-P sampling). Default is 0.1f.
     * @property temperature Controls the randomness of predictions. Higher values (e.g., 1.0) make output more random, lower values (e.g., 0.1) make it more deterministic. Default is 0.8f.
     * @property storeChats If true, the engine maintains a conversational context. Default is true.
     * @property contextSize Maximum number of tokens in the context window. If null, the model's default is used.
     * @property chatTemplate The template string used to format chat messages. If null, the model's default is used.
     * @property numThreads Number of CPU threads to use for computation. Default is 4.
     * @property useMmap Whether to use memory mapping for the model file. Faster load times but may increase memory usage. Default is true.
     * @property useMlock Whether to lock the model in memory to prevent swapping. Default is false.
     * @property topP Nucleus sampling probability threshold. Default is 0.9f.
     * @property topK Top-K sampling limit. Default is 40.
     * @property usePenalties Whether to apply repetition penalties. Default is false.
     * @property xtcP Probability threshold for XTC sampler. Default is 0.0f (disabled).
     * @property xtcT Threshold for XTC sampler. Default is 1.0f.
     */
    data class InferenceParams(
        val minP: Float = 0.1f,
        val temperature: Float = 0.8f,
        val storeChats: Boolean = true,
        val contextSize: Long? = null,
        val chatTemplate: String? = null,
        val numThreads: Int = 4,
        val useMmap: Boolean = true,
        val useMlock: Boolean = false,
        val topP: Float = 0.9f,
        val topK: Int = 40,
        val usePenalties: Boolean = false,
        val xtcP: Float = 0.0f,
        val xtcT: Float = 1.0f,
    )


    /**
     * Loads the GGUF model from the given path using the specified parameters.
     *
     * This function initializes a [GGUFReader] to extract model metadata (like context size and chat template)
     * directly from the model file. It prioritizes values in the following order:
     * 1. Explicit values provided in [params].
     * 2. Metadata found within the GGUF file.
     * 3. Default fallback values defined in [DefaultInferenceParams].
     *
     * The loading process occurs on the [Dispatchers.IO] context to avoid blocking the main thread.
     *
     * @param modelPath The absolute file path to the GGUF model on the device.
     * @param params Configuration options for the inference engine (e.g., temperature, threads, sampling settings).
     *               Defaults to a standard set of parameters if not specified.
     * @return A native pointer (Long) representing the loaded model context, which is stored internally
     *         in `nativePtr`.
     */
    suspend fun load(
        modelPath: String,
        params: InferenceParams = InferenceParams(),
    ) = withContext(Dispatchers.IO) {
        val ggufReader = GGUFReader()
        ggufReader.load(modelPath)
        val modelContextSize = ggufReader.getContextSize() ?: DefaultInferenceParams.contextSize
        val modelChatTemplate =
            ggufReader.getChatTemplate() ?: DefaultInferenceParams.chatTemplate
        nativePtr =
            loadModel(
                modelPath,
                params.minP,
                params.temperature,
                params.storeChats,
                params.contextSize ?: modelContextSize,
                params.chatTemplate ?: modelChatTemplate,
                params.numThreads,
                params.useMmap,
                params.useMlock,
                params.topK,
                params.topP,
                params.usePenalties
            )
    }

    /**
     * Adds a user message to the chat history.
     * This message will be considered as part of the conversation
     * when generating the next response.
     *
     * @param message The user's message.
     * @throws IllegalStateException if the model is not loaded.
     */
    fun addUserMessage(message: String) {
        verifyHandle()
        addChatMessage(nativePtr, message, "user")
    }

    /**
     * Adds the system prompt for the LLM
     */
    fun addSystemPrompt(prompt: String) {
        verifyHandle()
        addChatMessage(nativePtr, prompt, "system")
    }


    /**
     * Adds an assistant message to the chat history.
     *
     * This represents a response generated by the LLM or a pre-filled answer
     * inserted into the conversation context to guide future generation.
     * This message will be included in the conversation context for subsequent queries.
     *
     * @param message The content of the assistant's message.
     * @throws IllegalStateException if the model is not loaded.
     */
    fun addAssistantMessage(message: String) {
        verifyHandle()
        addChatMessage(nativePtr, message, "assistant")
    }

    /**
     * Returns the rate (in tokens per second) at which the
     * LLM generated its last response via `getResponse()`
     */
    fun getResponseGenerationSpeed(): Float {
        verifyHandle()
        return getResponseGenerationSpeed(nativePtr)
    }

    /**
     * Returns the number of tokens consumed by the LLM's context
     * window
     * The context of the LLM is roughly the output of,
     * tokenize(apply_chat_template(messages_in_conversation))
     */
    fun getContextLengthUsed(): Int {
        verifyHandle()
        return getContextSizeUsed(nativePtr)
    }

    /**
     * Return the LLM response to the given query as an
     * async Flow. This is useful for streaming the response
     * as it is generated by the LLM.
     *
     * @param message The query to ask the LLM.
     * @return A Flow of Strings, where each String is a piece of the response.
     *         The flow completes when the LLM has finished generating the response.
     *         The special token "[EOG]" (End Of Generation) indicates the end of the response.
     * @throws IllegalStateException if the model is not loaded.
     */

    fun getResponseAsFlow(message: String): Flow<String> =
        flow {
            verifyHandle()                 // throws if model not loaded
            startCompletion(nativePtr, message)

            try {
                while (true) {
                    // Call into JNI which now throws Java exceptions on failure.
                    val piece = completionLoop(nativePtr)
                        ?: throw RuntimeException("native completionLoop returned null")

                    // end-of-generation token from native
                    if (piece == "[EOG]") break

                    // native may return "" for partial token accumulation; ignore it
                    if (piece.isNotEmpty()) emit(piece)
                }
            } finally {
                // ensure native cleanup always runs
                try {
                    stopCompletion(nativePtr)
                } catch (t: Throwable) {
                    // Log but do not throw (we are in finally)
                    Log.e("LlamaEngine", "stopCompletion failed", t)
                }
            }
        }
            .flowOn(Dispatchers.IO) // run native work on IO threads
            .catch { e ->
                // Option A: emit a friendly error message into the stream then complete
                emit("[ERROR] ${e.message ?: "native failure"}")
                // Option B: rethrow to let collector handle the exception instead:
                // throw e
            }

    /**
     * Returns the LLM response to the given query as a String.
     * This function is blocking and will return the complete response.
     *
     * @param query The user's query/prompt for the LLM.
     * @return The complete response from the LLM.
     * @throws IllegalStateException if the model is not loaded.
     */
    fun getResponse(query: String): String {
        verifyHandle()
        startCompletion(nativePtr, query)
        var piece = completionLoop(nativePtr)
        var response = ""
        while (piece != "[EOG]") {
            response += piece
            piece = completionLoop(nativePtr)
        }
        stopCompletion(nativePtr)
        return response
    }

    /**
     * Unloads the LLM model and releases resources.
     * This method should be called when the Model instance is no longer needed
     * to prevent memory leaks.
     */
    fun close() {
        if (nativePtr != 0L) {
            close(nativePtr)
            nativePtr = 0L
        }
    }

    private fun verifyHandle() {
        assert(nativePtr != 0L) { "Model is not loaded. Use Model.create to load the model" }
    }

    private external fun loadModel(
        modelPath: String,
        minP: Float,
        temperature: Float,
        storeChats: Boolean,
        contextSize: Long,
        chatTemplate: String,
        nThreads: Int,
        useMmap: Boolean,
        useMlock: Boolean,
        topK: Int,
        topP: Float,
        usePenalties: Boolean = false
    ): Long

    private external fun addChatMessage(
        modelPtr: Long,
        message: String,
        role: String,
    )

    private external fun getResponseGenerationSpeed(modelPtr: Long): Float

    private external fun getContextSizeUsed(modelPtr: Long): Int

    private external fun close(modelPtr: Long)

    private external fun startCompletion(
        modelPtr: Long,
        prompt: String,
    )

    private external fun completionLoop(modelPtr: Long): String

    private external fun stopCompletion(modelPtr: Long)
}
