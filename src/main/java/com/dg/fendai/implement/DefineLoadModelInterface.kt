package com.dg.fendai.implement

import kotlin.reflect.KClass

interface DefineLoadModelInterface {
    fun promptRegister(vararg prompt: KClass<*>)
    fun clear()
    var prompt : String?
    fun call(route: String, vararg args: Any?) : Any?
    var pathFunction: MutableList<String>
}