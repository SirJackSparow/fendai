package com.dg.fendai.implement


/**
 * Defines a contract for configuring and registering an AI model within the FendAI system.
 *
 * Implementations of this interface serve as configuration providers, allowing the
 * application to specify fine-grained inference parameters for the underlying Large Language Model (LLM).
 * These settings control aspects of text generation such as creativity, context memory limits,
 * response formatting, and sampling strategies.
 * and define what types do you want to use (ex: gguf)
 *
 * This interface is typically used when initializing a model engine or loading a specific model file
 * to ensure it behaves according to the desired use case (e.g., creative writing vs. strict instruction following).
 */
interface RegisterFendAI {
    /**
     * Specifies the sampling temperature for the AI model's generation process.
     *
     * Temperature controls the randomness of the output. Higher values (e.g., 0.8 or above) make
     * the output more random and creative, while lower values (e.g., 0.2 or below) make it more
     * focused, deterministic, and conservative.
     *
     * @return A [Float] representing the temperature value, or null/0.0f to use the default.
     */
    fun temperature(): Float?

    /**
     * Defines the maximum number of tokens the model can process in a single context window.
     *
     * This value represents the limit for the combined length of the input prompt and the generated output.
     * A larger context size allows the model to "remember" more conversation history or process larger
     * blocks of text, but requires more memory.
     *
     * @return The context size limit as a [Long], or 0L if not specifically configured (which may imply using the model's default).
     */
    fun contextSize(): Long?

    /**
     * Returns the chat template string used to format the conversation history for the AI model.
     *
     * This template dictates how messages (system, user, assistant) are structured and delimited
     * before being fed into the model. It follows a Jinja2-like syntax often compatible with
     * tokenizer libraries.
     *
     * The default implementation provides a standard ChatML-style format:
     * - It automatically prepends a default system prompt ("You are a helpful AI assistant named John Doe")
     *   if the first message in the history is not a system message.
     * - It wraps messages in `<|im_start|>` and `<|im_end|>` tags.
     * - If `add_generation_prompt` is true, it appends the start of an assistant response.
     *
     * @return A string containing the Jinja2 template, or null to use the model's internal default.
     */
    fun chatTemplate(): String?

    /**
     * Specifies the "Top-K" sampling parameter for token generation.
     *
     * Top-K sampling restricts the model's choice of the next token to the K most probable
     * tokens. This helps cut off low-probability, nonsensical predictions.
     *
     * A value of 0 (or less) typically disables Top-K sampling. Higher values allow for more
     * diversity, while lower values make the output more deterministic and focused.
     *
     * @return An [Int] representing the number of top tokens to consider, or null/0 to use the default behavior.
     */
    fun topK(): Int?

    /**
     * Specifies the Top-P (Nucleus) sampling probability for the AI model's generation.
     *
     * Top-P sampling restricts the model's token selection to the smallest set of most probable
     * tokens whose cumulative probability exceeds the threshold [P]. For example, if P is 0.9,
     * the model considers only the top tokens that make up 90% of the probability mass.
     *
     * This serves as an alternative or complement to temperature for controlling randomness.
     *
     * @return A [Float] representing the probability threshold (0.0 to 1.0), or null/0.0f to use the default.
     */
    fun topP(): Float?

    /**
     * Determines whether repetition or presence penalties should be applied during text generation.
     *
     * When enabled (`true`), the model is penalized for repeating tokens that have already appeared
     * in the context or generated output. This helps reduce repetitive loops and encourages more diverse
     * vocabulary usage.
     *
     * - **Repetition Penalty:** penalizes tokens based on how many times they have appeared.
     * - **Presence/Frequency Penalty:** penalizes tokens simply for existing in the text, regardless of frequency.
     *
     * @return `true` to enable penalties, or `false` to disable them (default).
     */
    fun usePinalties(): Boolean

    /**
     * Defines the file format or model architecture type required for this configuration.
     *
     * This method specifies the expected extension or internal structure of the model file
     * (e.g., GGUF, TFLite) that this configuration is intended to support. This allows
     * the system to validate that the loaded model file matches the implementation's expectations
     * before attempting to initialize the engine.
     *
     * Note: The function signature currently returns [Unit] (void), implying this might be intended
     * to perform a side-effect (like validation) or is a placeholder for a return type (e.g., an Enum or String)
     * that identifies the file type.
     *
     * for noe only 2 gguf and tflite
     */
    fun typeOfFile(): TYPE_OF_FILE


    fun maxResponseTokens() : Int?
}

/**
 * Enumerates the supported file formats and model architectures for the FendAI system.
 *
 * This enum is used by [RegisterFendAI.typeOfFile] to specify which underlying engine
 * or loader should be used for a given model configuration.
 *
 * @property GGUF Represents the GGUF (GPT-Generated Unified Format) file format, commonly used with llama.cpp.
 * @property TFLITE Represents the TensorFlow Lite file format, optimized for on-device machine learning.
 */
enum class TYPE_OF_FILE {
    GGUF,
    TFLITE
}