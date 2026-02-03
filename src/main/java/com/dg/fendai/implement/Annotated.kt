package com.dg.fendai.implement

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Prompt(
    val prompt: String
)

/**
 * Marks FendAI APIs as experimental, indicating that they are not yet stable and may change.
 */
@RequiresOptIn(
    message = "This API is experimental and may change in the future. Use with caution. The count here is every alphabet",
    level = RequiresOptIn.Level.WARNING
)
@Retention(AnnotationRetention.BINARY)
annotation class FendAIExperimental