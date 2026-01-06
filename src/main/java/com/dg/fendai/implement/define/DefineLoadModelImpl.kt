package com.dg.fendai.implement.define

import com.dg.fendai.implement.Prompt
import kotlin.collections.forEach
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.findAnnotation

/**
 * An implementation of the [DefineLoadModelInterface] that provides a routing mechanism
 * for dynamically loading and calling functions from classes annotated with [com.dg.fendai.implement.Prompt].
 *
 * This class uses reflection to discover functions within annotated classes and registers them
 * as callable routes. A route is constructed by combining the prompt value from the `@Prompt`
 * annotation and the function name (e.g., "promptValue/functionName"). These routes can then
 * be invoked using the [call] method.
 *
 * The primary use case is to create a simple, string-based API for a set of related functions,
 * allowing them to be called dynamically without direct compile-time dependencies on the caller's side.
 *
 * Example Usage:
 * ```
 * @Prompt("myApi")
 * class MyApi {
 *     fun greet(name: String): String = "Hello, $name!"
 * }
 *
 * val loader = DefineLoadModelImpl()
 * loader.promptRegister(MyApi::class) // Registers the route "myApi/greet"
 *
 * val result = loader.call("myApi/greet", "World")
 * println(result) // Output: "Hello, World!"
 * ```
 *
 * @see DefineLoadModelInterface
 * @see com.dg.fendai.implement.Prompt
 */
class DefineLoadModelImpl() : DefineLoadModelInterface {

    private val routes = mutableMapOf<String, Pair<Any, KFunction<*>>>()

    private var promptValue :  String? = null

    private var path : MutableList<String> = mutableListOf()

    override var prompt: String? get() = promptValue
        set(value) { promptValue = value}

    override fun call(route: String, vararg args: Any?): Any? {
        val entry = routes[route] ?: return "Route not found: $route"
        val (instance, function) = entry
        return try {
            function.call(instance, *args)
        } catch (e: Exception) {
            "Error calling $route: ${e.message}"
        }
    }

    override var pathFunction: MutableList<String>
        get() = path
        set(value) { path = value }

    /**
     * Registers one or more classes to make their functions available as callable routes.
     *
     * This method iterates through the provided `KClass` objects. For each class, it looks for
     * a `@Prompt` annotation. If found, it creates an instance of the class and registers all of
     * its declared functions as routes.
     *
     * A route path is constructed by combining the value from the `@Prompt` annotation and the
     * function's name, in the format `"promptValue/functionName"`. Each registered route is
     * associated with the class instance and the specific `KFunction`.
     *
     * Any class that does not have a `@Prompt` annotation will be silently ignored.
     * Errors during instantiation or reflection are caught and printed to the console.
     *
     * @param prompt A vararg of `KClass<*>` objects to be scanned and registered.
     *               These classes are expected to be annotated with `@Prompt`.
     */
    override fun promptRegister(vararg prompt: KClass<*>) {
        try {
            prompt.forEach { kClass ->
                val annotation = kClass.findAnnotation<Prompt>() ?: return@forEach
                val instance = kClass.createInstance()
                promptValue = annotation.prompt

                kClass.declaredFunctions.forEach { function ->
                    path.add("${annotation.prompt}/${function.name}")
                    val routePath = "${annotation.prompt}/${function.name}"
                    routes[routePath] = instance to function
                    println("*Route registered: $routePath")
                }
            }
            println("Prompt Successfully Registered")
        } catch (e: Exception) {
            println("Error: ${e.message}")
        }
    }

    override fun clear() {
        routes.clear()
    }
}
