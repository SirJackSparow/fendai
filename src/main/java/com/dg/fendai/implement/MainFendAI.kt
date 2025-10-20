package com.dg.fendai.implement

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlin.reflect.KClass

object MainFendAI {
    private var initialized = false
    private lateinit var appContext: Context
    private val router: DefineInterface = DefineImpl()

    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        initialized = true

        println("Init FendAI")
    }

    fun isInitialized() = initialized

    fun register(vararg controllers: KClass<*>) {
        ensureInitialized()
        router.promptRegister(*controllers)
    }

    suspend fun sendMessage(message: String) = coroutineScope {

        val result = StringBuilder()

        try {


        } catch (e: CancellationException) {

        } catch (e: Exception) {

        }
    }

    fun addUserMessage(message: String) {

    }

    fun prompt(prompting: String, vararg args: Any?) : Any?{
        ensureInitialized()
        return router.prompt(prompting, *args)
    }

    private fun ensureInitialized() {
        if (!initialized) throw IllegalStateException("MyAI not initialized! Call MyAI.init(context) first.")
    }

}