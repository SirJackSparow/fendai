package com.dg.fendai.implement.define

import kotlin.reflect.KClass

interface DefineLoadModelInterface {
    /**
     * Registers one or more prompt classes to be used by the model loader.
     *
     * This function allows you to define which Kotlin classes (representing prompts or
     * interaction models) should be recognized and handled.
     *
     * @param prompt A variable number of [KClass] arguments, representing the classes to register.
     */
    fun promptRegister(vararg prompt: KClass<*>)
    /**
     * Clears all registered prompts and resets the state of this load model interface.
     *
     * This function should be called to release resources or prepare the interface
     * for a fresh set of prompt registrations.
     */
    fun clear()
    /**
     * The prompt or identifier for the current model or operation.
     * It can be used to specify which model to use or to pass a specific instruction.
     * This property is nullable, allowing it to be unset when not needed.
     */
    var prompt : String?
    /**
     * Executes a specific function or operation identified by the given route.
     *
     * This method acts as a dynamic invocation mechanism, allowing callers to trigger logic
     * associated with a string-based route identifier. It supports passing a variable number
     * of arguments to the underlying implementation.
     *
     * @param route The unique string identifier for the operation to be called.
     * @param args A variable number of arguments to be passed to the invoked operation.
     * @return The result of the operation, or null if the operation returns nothing or fails.
     */
    fun call(route: String, vararg args: Any?) : Any?
    /**
     * A mutable list of strings representing the path or identifier for the function call mechanism.
     * This collection stores the sequence of function names or routing keys used to navigate to or identify the specific functionality being invoked.
     */
    var pathFunction: MutableList<String>
}