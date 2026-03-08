package com.dg.fendai.implement.manager

import android.content.Context
import android.net.Uri
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

    /**
     * Sends a message to the Llama model and retrieves the complete response.
     *
     * This function communicates with the underlying [LlamaEngine] to get a response for the given
     * message. It collects the response as a stream of text pieces and concatenates them into a
     * single string. The operation is cancellable and handles exceptions gracefully by returning
     * any partial result collected before the interruption or error.
     *
     * @param message The message string to be sent to the model.
     * @return A [String] containing the full response from the model. If an error or cancellation
     * occurs, it may return a partial response or an error message appended to the result.
     */
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
        onSuccess: (Boolean) -> Unit
    ) {
        try {
            modelInitJob =
                CoroutineScope(Dispatchers.IO).launch {
                    if (isInstanceLoaded) {
                        instance.free()
                        isInstanceLoaded = false
                    }

                    instance.load(modelPath, params)

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

    override fun addPrompt(prompt: String) {
        instance.addSystemPrompt(prompt)
    }

    override fun addMessageHistory(message: String) {
        instance.addAssistantMessage(message)
    }

    override fun checkGGUFFile(
        uri: Uri,
        context: Context,
    ): Boolean {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val gguMagicNumberBytes = ByteArray(4)
            inputStream.read(gguMagicNumberBytes)
            return gguMagicNumberBytes.contentEquals(byteArrayOf(71, 71, 85, 70))
        }
        return false
    }

    override fun clearHistoryChat() {
        instance.clearAllHistory()
    }

    override fun clearMemoryOnly() {
       instance.clearMemory()
    }

    override fun removeFromLastChat(count: Int) {
       instance.undoLastMessages(count)
    }

    override fun getCountChat() : Int {
       return instance.getTotalMessages()
    }

    override fun logAllMessageHistory() {
        instance.logAllMessageHistory()
    }

    override fun tokensUsed() {
        instance.getContextLengthUsed()
    }
}