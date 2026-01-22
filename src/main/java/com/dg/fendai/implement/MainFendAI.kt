package com.dg.fendai.implement

import android.content.Context
import android.util.Log
import com.dg.fendai.core.LlamaEngine
import com.dg.fendai.implement.manager.FendAIManager
import com.dg.fendai.implement.manager.FendAIManagerImplementation
import kotlinx.coroutines.coroutineScope
import kotlin.reflect.KClass
import com.dg.fendai.core.GGUFReader
import android.net.Uri
import android.provider.OpenableColumns
import com.dg.fendai.core.InferenceTFParams
import com.dg.fendai.implement.define.DefineLoadModelImpl
import com.dg.fendai.implement.define.DefineLoadModelInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import com.dg.fendai.implement.Utils.DEFAULT_TEMPLATE_MESSAGE
import com.dg.fendai.implement.manager.FendAITFLiteManager
import com.dg.fendai.implement.manager.FendAITFLiteManagerImplementation
import com.dg.fendai.core.ChatMessage
import com.dg.fendai.core.Role

/**
 * The main entry point and singleton controller for the FendAI library.
 *
 * `MainFendAI` orchestrates the interaction between the application layer and the underlying
 * AI engine (LlamaEngine). It handles lifecycle management tasks such as:
 * - Registering configuration controllers and prompts.
 * - Loading GGUF model files from storage.
 * - Managing chat sessions and history.
 * - Routing inference parameters (temperature, top-K, etc.) dynamically.
 *
 * Usage typically involves:
 * 1. Calling [registerPrompt] to initialize the system with configuration classes.
 * 2. Calling [uploadModelFile] to load a model and prepare the engine.
 * 3. Calling [sendMessage] to interact with the AI.
 */
object MainFendAI {

    private var initialized = false

    /**
     * An internal interface implementation used to manage and route model configuration parameters and prompt registrations.
     * It handles dynamic retrieval of settings like temperature, context size, and top-k/top-p values
     * through its registered path functions.
     */
    private val router: DefineLoadModelInterface = DefineLoadModelImpl()

    /**
     * Manages the interaction with the underlying AI engine (LlamaEngine), handling model loading,
     * message processing, and history management. It delegates implementation details to [FendAIManagerImplementation].
     */
    private val fendAIManager: FendAIManager = FendAIManagerImplementation()

    var responseMessage = ""

    /**
     * Uploads a GGUF model file from a given URI, saves it to the app's internal file storage, extracts metadata, and loads the model into the engine.
     *
     * This function performs the following operations:
     * 1. Resolves the file name from the provided content [uri].
     * 2. Copies the file content from the URI to the application's internal `filesDir` on an IO dispatcher.
     * 3. Uses [GGUFReader] to parse the saved file and extract the context size and chat template.
     *    - If context size is missing, defaults to 1024L.
     *    - If the chat template is missing, defaults to a generic ChatML-like template.
     * 4. Calls the internal `loadModel` function to initialize the Llama engine with the extracted parameters.
     *
     * @param uri The [Uri] of the model file to be uploaded (usually selected via a file picker).
     * @param context The Android [Context] used to resolve the content URI and access file storage.
     * @param onSuccess A callback invoked with a success message when the model is successfully loaded.
     * @param onError A callback invoked with a boolean flag and an error message if the upload or loading fails.
     *
     * @throws IllegalStateException via [ensureInitialized] if prompts have not been registered prior to calling this method.
     */
    fun uploadModelFile(
        uri: Uri,
        context: Context,
        onSuccess: (String) -> Unit,
        onError: (Boolean, String) -> Unit
    ) {
        ensureInitialized()
        if (!fendAIManager.checkGGUFFile(uri, context)) {
            onError(true, "File is not GGUF")
            return
        }
        var fileName = ""
        var chatTemplates = ""
        var contextSizes = 0L
        context.contentResolver.query(uri, null, null, null, null).use { cursor ->
            val nameIndex = cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor?.moveToFirst()
            fileName = nameIndex?.let { cursor.getString(it) }.toString()
        }

        if (fileName.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                context.contentResolver.openInputStream(uri).use { inputStream ->
                    FileOutputStream(File(context.filesDir, fileName)).use { outputStream ->
                        inputStream?.copyTo(outputStream)
                    }
                }

                //take the contextsize and chat template from the model
                val ggufReader = GGUFReader()
                ggufReader.load(File(context.filesDir, fileName).absolutePath)
                contextSizes = ggufReader.getContextSize() ?: 1024L
                chatTemplates = ggufReader.getChatTemplate()
                    ?: DEFAULT_TEMPLATE_MESSAGE

                loadModel(
                    modelPath = File(context.filesDir, fileName).absolutePath,
                    chatTemplate = chatTemplates,
                    contextSize = contextSizes,
                    onSuccess = { i ->
                        onSuccess("success to lod model !")
                    }, onError = { i ->
                        onError(true, if (fileName.isEmpty()) "empty model" else "UnKnown")
                    })
            }
        }
    }

    /**
     * Loads the specified AI model using the provided configuration and callback handlers.
     *
     * This internal function initializes the inference parameters (like temperature, context size, and chat template),
     * often retrieving defaults or overrides via the `router` mechanism. It then delegates the actual model loading
     * to the `fendAIManager`.
     *
     * @param modelPath The absolute file path to the model file (e.g., a GGUF file).
     * @param chatTemplate The string template used to structure the chat conversation for the model.
     * @param contextSize The default context size (token limit) to be used if not overridden by the router.
     * @param onSuccess A callback function invoked when the model is successfully loaded. Receives a boolean indicating success status.
     * @param onError A callback function invoked when an error occurs during the loading process. Receives a boolean flag.
     */
    private fun loadModel(
        modelPath: String,
        chatTemplate: String,
        contextSize: Long,
        onSuccess: (Boolean) -> Unit,
        onError: (Boolean) -> Unit
    ) {
        val params = LlamaEngine.InferenceParams(

            //router.call(router.pathFunction[5] this is for type_of_file
            temperature = (router.call(router.pathFunction[2])) as Float? ?: 0.9f,
            contextSize = (router.call(router.pathFunction[1])) as Long? ?: contextSize,
            chatTemplate = router.call(router.pathFunction[0]) as String? ?: chatTemplate,
            topK = router.call(router.pathFunction[3]) as Int? ?: 0,
            topP = router.call(router.pathFunction[4]) as Float? ?: 0.0f,
            usePenalties = router.call(router.pathFunction[6]) as Boolean? ?: false
        )

        router.pathFunction.forEachIndexed { i, v ->
            Log.d(v.uppercase(), ": ${router.pathFunction[i].lowercase()}")
        }

        fendAIManager.loadModel(
            modelPath = modelPath,
            params = params,
            onError = {
                onError(true)
            },
            onSuccess = { success ->
                onSuccess(success)
                addPrompt(router.prompt)
            })
    }

    /**
     * Checks if the [MainFendAI] engine has been initialized.
     *
     * Initialization occurs when [registerPrompt] is called. This flag is used to ensure
     * that necessary setup steps (like registering prompt controllers) are completed
     * before attempting operations like uploading models or sending messages.
     *
     * @return `true` if the engine is initialized, `false` otherwise.
     */
    fun isInitialized() = initialized

    /**
     * Registers the specified controller classes that define the prompts and configuration for the FendAI engine.
     *
     * This method initializes the [MainFendAI] object, marking it as ready for use. It delegates the
     * registration logic to the underlying router, allowing it to parse and store prompt definitions
     * found within the provided [controllers].
     *
     * Note: This function must be called before attempting to load models or send messages, as other
     * methods check the initialization state via [ensureInitialized].
     *
     * @param controllers A variable number of [KClass] arguments representing the classes that contain
     * prompt definitions or configurations (e.g., annotated classes or configuration objects).
     */
    fun registerPrompt(vararg controllers: KClass<*>) {
        initialized = true
        router.promptRegister(*controllers)
    }

    /**
     * Sends a user message to the AI model and awaits the response.
     *
     * This function ensures the FendAI engine is initialized before attempting to process
     * the message. It delegates the actual message processing to the underlying
     * [FendAIManager] and returns the generated text response.
     *
     * @param message The text message string to be sent to the AI model.
     * @return The response string generated by the AI model, or the exception message if an error occurs.
     * @throws IllegalStateException If the [MainFendAI] has not been initialized (e.g., [registerPrompt] hasn't been called).
     */
    suspend fun sendMessage(message: String): String = coroutineScope {
        ensureInitialized()
        try {
            responseMessage = fendAIManager.sendMessage(message)
            return@coroutineScope responseMessage
        } catch (e: Exception) {
            return@coroutineScope e.message ?: "Empty Error"
        }
    }

    /**
     * Adds a user message to the underlying AI manager without immediately triggering a response.
     *
     * This function is typically used to append the user's input to the conversation history
     * or context before further processing. It ensures the system is initialized before
     * delegation.
     *
     * @param message The message string input by the user.
     * @throws IllegalStateException if the [MainFendAI] has not been initialized via [registerPrompt].
     */
    fun addUserMessage(message: String) {
        ensureInitialized()
        fendAIManager.addUserMessage(message)
    }

    /**
     * Appends a message string to the conversation history within the AI manager.
     *
     * This function ensures that the main FendAI component is initialized before delegating the
     * task of updating the message history to the underlying [FendAIManager].
     *
     * @param message The message content to be added to the history.
     * @throws IllegalStateException If [registerPrompt] has not been called prior to this function.
     */
    fun addMessageHistory(message: String) {
        ensureInitialized()
        fendAIManager.addMessageHistory(message)
    }

    /**
     * Adds an initial system prompt or instruction to the AI model manager after a model has been successfully loaded.
     *
     * This method ensures that the module is initialized before attempting to add the prompt.
     * If the provided [message] is null or empty, the operation is skipped.
     *
     * @param message The prompt string to be added (e.g., a system instruction). Can be null.
     * @throws IllegalStateException If the `MainFendAI` module has not been initialized via `registerPrompt`.
     */
    private fun addPrompt(message: String?) {
        ensureInitialized()
        if (message != null && message.isNotEmpty()) {
            fendAIManager.addPrompt(message)
        }
    }

    /**
     * Checks if the MainFendAI singleton has been properly initialized.
     *
     * This method verifies that [initialized] flag is set to true. If the library
     * has not been initialized via [registerPrompt], it throws an exception to prevent
     * operations on an unconfigured state.
     *
     * @throws IllegalStateException if the [registerPrompt] function has not been called yet.
     */
    private fun ensureInitialized() {
        if (!initialized) throw IllegalStateException("Register prompt first")
    }

    /**
     * Clears the current configuration and state of the [MainFendAI] object.
     *
     * This function performs the following actions:
     * 1. Resets the internal router configuration via `router.clear()`.
     * 2. Marks the [MainFendAI] object as uninitialized.
     *
     * After calling this method, [registerPrompt] must be called again before performing
     * any operations that require initialization (such as sending messages or uploading models).
     */
    fun clear() {
        router.clear()
        initialized = false
    }

    fun changePrompt(prompt: String) {
        addPrompt(prompt)
    }

    /**
     * TFLite manager
     */

    fun uploadFileTFLite(modelPath: String, context: Context, params: InferenceTFParams) {
        val fendAITFLiteManager: FendAITFLiteManager = FendAITFLiteManagerImplementation(context)
        fendAITFLiteManager.uploadFileTFLite(
            router.prompt ?: "",
            model = modelPath,
            params = params
        )
    }

    fun addUserMessageTfLite(context: Context, message: String, onResponse: (String) -> Unit) {
        val fendAITFLiteManager: FendAITFLiteManager =
            FendAITFLiteManagerImplementation(context = context)
        fendAITFLiteManager.sendMessage(
            message = ChatMessage(role = Role.USER, message),
            onComplete = onResponse
        )

    }
}
