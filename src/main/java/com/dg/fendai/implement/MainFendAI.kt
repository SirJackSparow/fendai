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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

object MainFendAI {
    private var initialized = false
    private val router: DefineLoadModelInterface = DefineLoadModelImpl()
    private val fendAIManager: FendAIManager = FendAIManagerImplementation()

    var responseMessage = ""

    fun init() {
        if (initialized) return
        initialized = true
        println("Init FendAI")
    }

    fun uploadModelFile(uri:Uri, context: Context, onSuccess: (String) -> Unit, onError: (Boolean, String) -> Unit)  {
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
             ggufReader.load(File(context.filesDir,fileName).absolutePath)
                contextSizes = ggufReader.getContextSize() ?:  1024L
                chatTemplates = ggufReader.getChatTemplate() ?:  "{% for message in messages %}{% if loop.first and messages[0]['role'] != 'system' %}{{ '<|im_start|>system You are a helpful AI assistant named John Doe<|im_end|> ' }}{% endif %}{{'<|im_start|>' + message['role'] + ' ' + message['content'] + '<|im_end|>' + ' '}}{% endfor %}{% if add_generation_prompt %}{{ '<|im_start|>assistant ' }}{% endif %}"
                loadModel(modelPath = File(context.filesDir, fileName).absolutePath,
                    chatTemplate = chatTemplates,
                    contextSize = contextSizes,
                    onSuccess = { i ->
                        onSuccess("success to lod model !")
                    }, onError = {i ->
                        onError(true,if (fileName.isEmpty()) "empty model" else "UnKnown" )
                    } )
            }
        }
        onError(true,if (fileName.isEmpty()) "empty model" else "UnKnown" )
    }

    private fun loadModel(modelPath: String,chatTemplate: String, contextSize: Long , onSuccess: (Boolean) -> Unit, onError: (Boolean) -> Unit) {
        val params = LlamaEngine.InferenceParams(
            temperature = (router.call(router.pathFunction[0]) as? String)?.toFloatOrNull() ?: 0.0f,
            contextSize = (router.call(router.pathFunction[1]) as? String)?.toLongOrNull() ?: contextSize,
            chatTemplate = router.call(router.pathFunction[2])?.toString() ?: chatTemplate,
            //topK = router.call(router.pathFunction[3]) as Int? ?: 0,
            //topP = router.call(router.pathFunction[4]) as Float? ?: 0.0f
        )

        Log.e("t","Temperature: ${params.temperature}")
        Log.e("t","Context Size: ${params.contextSize}")
        Log.e("t","Chat Template: ${params.chatTemplate}")

        fendAIManager.loadModel(
            modelPath = modelPath,
            params = params,
            onError = {
                onError(true)
            },
            onSuccess = { success ->
                onSuccess(success)
            })
    }

    fun isInitialized() = initialized

    fun registerPrompt(vararg controllers: KClass<*>) {
        ensureInitialized()
        router.promptRegister(*controllers)
    }

    suspend fun sendMessage(message: String): String = coroutineScope {
        try {
            responseMessage = fendAIManager.sendMessage(message)
            return@coroutineScope responseMessage
        } catch (e: Exception) {
            return@coroutineScope e.message ?: "Empty Error"
        }
    }

    fun addUserMessage(message: String) {
        fendAIManager.addUserMessage(message)
    }

    private fun ensureInitialized() {
        if (!initialized) throw IllegalStateException("MyAI not initialized! Call MyAI.init(context) first.")
    }

    fun clear() {
        router.clear()
        initialized = false
    }
}
