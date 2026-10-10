//
// Created by Bima Maulana Effendy on 01/10/25.
//
#include "LLMInference.h"
#include "mtmd-helper.h"
#include <jni.h>
#include <thread>
#include <chrono>

/**
 * Loads a local LLM model and creates a native LlamaEngine instance.
 *
 * Called from Java/Kotlin to initialize the underlying LLMInference object with model
 * parameters such as an inference context size, sampling settings, chat template, and
 * GPU layer configuration. Returns the native pointer that will be used by the rest of
 * the JNI bridge methods.
 */
extern "C" JNIEXPORT jlong JNICALL
Java_com_dg_fendai_core_LlamaEngine_loadModel(JNIEnv *env, jobject thiz, jstring modelPath,
        jfloat minP,
        jfloat temperature, jboolean storeChats,
        jlong contextSize,
        jstring chatTemplate, jint nThreads, jboolean useMmap,
        jboolean useMlock, jint topK, jfloat topP, jboolean usePenalties, jint maxResponseTokens,
        jint nGpuLayers) {

    jboolean isCopy = true;
    const char *modelPathCstr = env->GetStringUTFChars(modelPath, &isCopy);
    auto *llmInference = new LLMInference();
    const char *chatTemplateCstr = env->GetStringUTFChars(chatTemplate, &isCopy);

    try {
        llmInference->loadModel(modelPathCstr, minP, temperature, storeChats, contextSize,
                chatTemplateCstr, nThreads, useMmap, useMlock, topK, topP, usePenalties,maxResponseTokens,
                nGpuLayers);
    } catch (std::runtime_error &error) {
        delete llmInference;
        llmInference = nullptr;
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
    }

    env->ReleaseStringUTFChars(modelPath, modelPathCstr);
    env->ReleaseStringUTFChars(chatTemplate, chatTemplateCstr);
    return reinterpret_cast<jlong>(llmInference);
}


/**
 * Releases the native model object and stops generation before deletion.
 *
 * This is the counterpart to loadModel(). It tells the inference engine to stop any
 * ongoing generation, waits briefly for the thread to exit, and destroys the native
 * object to free memory.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_dg_fendai_core_LlamaEngine_freeModel(JNIEnv *env, jobject thiz, jlong nativePtr) {
    if (nativePtr != 0) {
        auto *llmInference = reinterpret_cast<LLMInference *>(nativePtr);
        llmInference->_stopGeneration.store(true);
        std::this_thread::sleep_for(std::chrono::milliseconds(200));
        delete llmInference;
    }
}

/**
 * Adds a single message to the model's chat history.
 *
 * This bridges a Java/Kotlin user or assistant message into the native chat buffer so
 * later completion calls can consider the conversation context.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_dg_fendai_core_LlamaEngine_addChatMessage(JNIEnv *env, jobject thiz, jlong modelPtr,
        jstring message,
        jstring role) {

    jboolean isCopy = true;
    const char *messageCstr = env->GetStringUTFChars(message, &isCopy);
    const char *roleCstr = env->GetStringUTFChars(role, &isCopy);
    auto *llmInference = reinterpret_cast<LLMInference *>(modelPtr);
    llmInference->addChatMessage(messageCstr, roleCstr);
    env->ReleaseStringUTFChars(message, messageCstr);
    env->ReleaseStringUTFChars(role, roleCstr);

}

/**
 * Returns the model's measured response generation speed.
 *
 * Used by the Android app to display generation throughput or analyze performance.
 */
extern "C" JNIEXPORT jfloat JNICALL
Java_com_dg_fendai_core_LlamaEngine_getResponseGenerationSpeed(JNIEnv *env, jobject thiz,
        jlong modelPtr) {
    auto *llmInference = reinterpret_cast<LLMInference *>(modelPtr);
    return llmInference->getResponseGenerationTime();
}

/**
 * Returns the number of context tokens currently in use.
 *
 * This lets the app monitor how much of the model's context window is occupied by
 * prompts and conversation history.
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_dg_fendai_core_LlamaEngine_getContextSizeUsed(JNIEnv *env, jobject thiz, jlong modelPtr) {
    auto *llmInference = reinterpret_cast<LLMInference *>(modelPtr);
    return llmInference->getContextSizeUsed();
}

/**
 * Closes the model handle and destroys the native inference object.
 *
 * This is a simpler cleanup method used by the Java layer when it wants to release the
 * model without the extra stop-generation wait used by freeModel().
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_dg_fendai_core_LlamaEngine_close(JNIEnv *env, jobject thiz, jlong modelPtr) {
    auto *llmInference = reinterpret_cast<LLMInference *>(modelPtr);
    delete llmInference;
    return 0;
}

/**
 * Starts a text generation run for a prompt.
 *
 * The native model receives the UTF-8 prompt string and begins generating output using
 * the current model state and chat settings.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_dg_fendai_core_LlamaEngine_startCompletion(JNIEnv *env, jobject thiz, jlong modelPtr,
        jstring prompt) {
    jboolean isCopy = true;
    const char *promptCstr = env->GetStringUTFChars(prompt, &isCopy);
    auto *llmInference = reinterpret_cast<LLMInference *>(modelPtr);
    llmInference->startCompletion(promptCstr);
    env->ReleaseStringUTFChars(prompt, promptCstr);
}

/**
 * Waits for the running generation to finish and returns the generated text.
 *
 * This is the blocking read step that the Java layer uses to receive the model output
 * after startCompletion() has kicked off generation.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_dg_fendai_core_LlamaEngine_completionLoop(JNIEnv *env, jobject thiz, jlong modelPtr) {
    auto *llmInference = reinterpret_cast<LLMInference *>(modelPtr);
    try {
        std::string response = llmInference->completionLoop();
        return env->NewStringUTF(response.c_str());
    } catch (std::runtime_error &error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
        return nullptr;
    }
}

/**
 * Requests an immediate stop to the active generation.
 *
 * Used when the app cancels the current generation or the user wants to interrupt the
 * model before it finishes the response.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_dg_fendai_core_LlamaEngine_stopCompletion(JNIEnv *env, jobject thiz, jlong modelPtr) {
    auto *llmInference = reinterpret_cast<LLMInference *>(modelPtr);
    llmInference->stopCompletion();
}

/**
 * Clears the entire conversation history stored in the model instance.
 *
 * This resets the chat context so the next generation starts from a clean conversation
 * instead of the previous messages.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_dg_fendai_core_LlamaEngine_clearChatHistory(JNIEnv *env, jobject thiz, jlong context_pointer) {
    auto *inference = reinterpret_cast<LLMInference *>(context_pointer);
    if (inference != nullptr) {
        inference->clearChatHistory();
    }
}

/**
 * Clears only the memory state while preserving the rest of the model configuration.
 *
 * This is useful when the app wants to reset the active conversation memory without
 * reloading the whole model or reinitializing the engine.
 */
extern "C"
JNIEXPORT void JNICALL
Java_com_dg_fendai_core_LlamaEngine_clearMemoryOnly(
        JNIEnv *env,
        jobject thiz,
        jlong context_pointer
) {
    auto* inference = reinterpret_cast<LLMInference*>(context_pointer);
    if (inference != nullptr) {
        inference->clearMemoryOnly();
    }
}

/**
 * Removes the most recent chat messages from the conversation history.
 *
 * The Java layer can use this to trim earlier conversation turns and keep the prompt
 * within the model context window.
 */
extern "C"
JNIEXPORT void JNICALL
Java_com_dg_fendai_core_LlamaEngine_removeLastMessages(
        JNIEnv *env,
        jobject thiz,
        jlong context_pointer,
        jint count
) {
    auto* inference = reinterpret_cast<LLMInference*>(context_pointer);
    if (inference != nullptr) {
        inference->removeLastMessages(count);
    }
}

/**
 * Returns how many messages are currently stored in the chat history.
 *
 * This helps the app decide whether the conversation is empty, whether it should trim
 * old messages, or whether it can safely continue generation.
 */
extern "C"
JNIEXPORT jint JNICALL
Java_com_dg_fendai_core_LlamaEngine_getMessageCount(
        JNIEnv *env,
        jobject thiz,
        jlong context_pointer
) {
    auto* inference = reinterpret_cast<LLMInference*>(context_pointer);
    if (inference != nullptr) {
        return inference->getMessageCount();
    }
    return 0;
}

/**
 * Prints or logs the full conversation history for debugging.
 *
 * This is a diagnostic helper used by native code to inspect the model's stored chat
 * messages when troubleshooting generation or context issues.
 */
extern "C"
JNIEXPORT void JNICALL
Java_com_dg_fendai_core_LlamaEngine_getLogAllHistory(
        JNIEnv *env,
        jobject thiz,
        jlong contextPointer
) {
    auto *llmInference = reinterpret_cast<LLMInference *>(contextPointer);
    llmInference->getLogAllHistory();
}

/**
 * Loads a multimodal projector (.mmproj) model for vision-aware inference.
 *
 * This enables the engine to accept image inputs and combine them with text prompts for
 * multimodal generation support.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_dg_fendai_core_LlamaEngine_loadMmproj(JNIEnv *env, jobject thiz, jlong modelPtr,
        jstring mmprojPath) {
    jboolean isCopy = true;
    const char *mmprojPathCstr = env->GetStringUTFChars(mmprojPath, &isCopy);
    auto *llmInference = reinterpret_cast<LLMInference *>(modelPtr);
    try {
        llmInference->loadMmproj(mmprojPathCstr);
    } catch (std::runtime_error &error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
    }
    env->ReleaseStringUTFChars(mmprojPath, mmprojPathCstr);
}

/**
 * Starts generation using both a text prompt and an image payload.
 *
 * This is the multimodal entry point for vision-capable models: the app passes the
 * prompt text and raw image bytes, and the engine interprets them as a combined input.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_dg_fendai_core_LlamaEngine_startCompletionWithImage(JNIEnv *env, jobject thiz,
        jlong modelPtr, jstring prompt, jbyteArray imageBytes) {
    jboolean isCopy = true;
    const char *promptCstr = env->GetStringUTFChars(prompt, &isCopy);
    jsize imageSize = env->GetArrayLength(imageBytes);
    jbyte *imageBytesPtr = env->GetByteArrayElements(imageBytes, &isCopy);
    auto *llmInference = reinterpret_cast<LLMInference *>(modelPtr);
    try {
        llmInference->startCompletionWithImage(
                promptCstr,
                reinterpret_cast<const unsigned char *>(imageBytesPtr),
                (int) imageSize
        );
    } catch (std::runtime_error &error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
    }
    env->ReleaseStringUTFChars(prompt, promptCstr);
    env->ReleaseByteArrayElements(imageBytes, imageBytesPtr, JNI_ABORT);
}

/**
 * Releases the multimodal projector resources loaded for vision support.
 *
 * This frees memory used by the mmproj model once the app no longer needs image-based
 * generation for the current engine instance.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_dg_fendai_core_LlamaEngine_freeMmproj(JNIEnv *env, jobject thiz, jlong modelPtr) {
    auto *llmInference = reinterpret_cast<LLMInference *>(modelPtr);
    llmInference->freeMmproj();
}
