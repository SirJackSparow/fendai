package com.dg.fendai.implement

import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.findAnnotation

class DefineImpl : DefineInterface {

    private val routes = mutableMapOf<String, Pair<Any, String>>()

    override fun promptRegister(vararg prompt: KClass<*>) {
        try {
            prompt.forEach { kClass ->
                val prompt = kClass.findAnnotation<Prompt>() ?: return@forEach
                val instance = kClass.createInstance()

                kClass.declaredFunctions.forEach { function ->
                    val routePath = "${prompt.prompt}/"
                    routes[routePath] = instance to function.name
                }
            }
            println("Prompt: Successfully prompt")
        } catch (e: Exception) {
            println("Error: ${e.message}")
        }
    }

    override fun call(path: String, vararg args: Any?): Any? {
        val (instance, methodName) = routes[path] ?: return  "404 Not found"

        val function = instance::class.declaredFunctions.first{ it.name == methodName}
        return function.call(instance, *args)
    }
}