package com.dg.fendai.core.manager

import com.dg.fendai.core.LlamaEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

class FendAIManagerImplementation : FendAIManager {

    private val instance = LlamaEngine()
    private var modelInitJob: Job? = null
    private var isInstanceLoaded = false

    override suspend fun sendMessage(message: String): String = coroutineScope {
        val result = StringBuilder()

        try {
            instance.getResponseAsFlow(message)
                .onEach { piece ->
                    ensureActive()
                    result.append(piece)
                }
                .catch {  e -> result.append(e.toString()) }
                .collect()

        } catch (cancel: CancellationException) {
            println(cancel)
            return@coroutineScope result.toString()
        } catch (e: Exception) {
            println(e)
            return@coroutineScope result.toString()
        }
        result.toString()
    }

    /**
     * Adds a user message to the conversation history. This message will be considered
     * as part of the context for the next call to `sendMessage`.
     *
     * @param message The user's message content as a `String`.
     */
    override fun addUserMessage(message: String) {
        instance.addUserMessage(message)
    }

    /**
     * Loads the Llama model from the specified path with the given parameters.
     * This operation is performed asynchronously on a background thread.
     *
     * @param modelPath The file path to the GGUF model to be loaded.
     * @param params The inference parameters for the model.
     * @param onError A callback function to be invoked if an error occurs during model loading.
     * @param systemPrompt The system prompt to be used (currently unused in the implementation).
     * @param onSuccess A callback function to be invoked upon successful completion (currently unused in the implementation).
     * @throws Exception if an error occurs during the model loading process.
     */
    override fun loadModel(
        modelPath: String,
        params: LlamaEngine.InferenceParams,
        onError: (Exception) -> Unit,
        systemPrompt: String,
        onSuccess: (Boolean) -> Unit
    ) {
        try {
            modelInitJob =
                CoroutineScope(Dispatchers.IO).launch {
                    if (isInstanceLoaded) {
                        return@launch
                    }

                    instance.load(modelPath, params)
                    instance.addSystemPrompt(prompt = systemPrompt)

                    withContext(Dispatchers.Main) {
                        isInstanceLoaded = true
                        onSuccess(true)
                    }
                }
        } catch (e: Exception) {
            onError(e)
            throw Exception(e)
        }
    }
}