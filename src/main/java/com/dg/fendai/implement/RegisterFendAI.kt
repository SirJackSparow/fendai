package com.dg.fendai.implement

interface RegisterFendAI {
    fun temperature() : Float? = 0.0f
    fun contextSize() : Long? = 0L
    fun chatTemplate (): String? = "{% for message in messages %}{% if loop.first and messages[0]['role'] != 'system' %}{{ '<|im_start|>system You are a helpful AI assistant named John Doe<|im_end|> ' }}{% endif %}{{'<|im_start|>' + message['role'] + ' ' + message['content'] + '<|im_end|>' + ' '}}{% endfor %}{% if add_generation_prompt %}{{ '<|im_start|>assistant ' }}{% endif %}"
    fun topK (): Int? = 0
    fun topP (): Float? = 0.0f

}