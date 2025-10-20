package com.dg.fendai.implement

import kotlin.reflect.KClass

interface DefineInterface {
    fun promptRegister(vararg prompt: KClass<*>)
    fun prompt(prompting: String, vararg args: Any?): Any?
}