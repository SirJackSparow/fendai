package com.dg.fendai.core

import android.content.Context
import com.dg.fendai.implement.DefineImpl
import com.dg.fendai.implement.DefineInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlin.reflect.KClass

object FendAI {
    private var initialized = false
    private lateinit var appContext: Context
    private val router: DefineInterface = DefineImpl()

    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        initialized = true
    }

    fun isInitialized() = initialized

    fun register(vararg controllers: KClass<*>) {

    }

    suspend fun sendMessage(message: String) = coroutineScope {

        val result = StringBuilder()

        try {

        }catch (e: CancellationException) {

        } catch (e: Exception) {

        }
    }

    fun addUserMessage(message: String) {

    }

}