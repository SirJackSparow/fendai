//
// Created by Bima Maulana Effendy on 01/10/25.
//
#include "LLMInference.h"
#include "mtmd-helper.h"
#include <jni.h>
#include <thread>
#include <chrono>

extern "C" JNIEXPORT jlong JNICALL
Java_com_dg_fendai_core_LlamaEngine_loadModel(JNIEnv *env, jobject thiz, jstring modelPath,
        jfloat minP,
        jfloat temperature, jboolean storeChats,
        jlong contextSize,
        jstring chatTemplate, jint nThreads, jboolean useMmap,
        jboolean useMlock, jint topK, jfloat topP, jboolean usePenalties, jint maxResponseTokens) {

    jboolean isCopy = true;
    const char *modelPathCstr = env->GetStringUTFChars(modelPath, &isCopy);
    auto *llmInference = new LLMInference();
    const char *chatTemplateCstr = env->GetStringUTFChars(chatTemplate, &isCopy);

    try {
        llmInference->loadModel(modelPathCstr, minP, temperature, storeChats, contextSize,
                chatTemplateCstr, nThreads, useMmap, useMlock, topK, topP, usePenalties,maxResponseTokens);
    } catch (std::runtime_error &error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
    }

    env->ReleaseStringUTFChars(modelPath, modelPathCstr);
    env->ReleaseStringUTFChars(chatTemplate, chatTemplateCstr);
    return reinterpret_cast<jlong>(llmInference);
}


extern "C" JNIEXPORT void JNICALL
Java_com_dg_fendai_core_LlamaEngine_freeModel(JNIEnv *env, jobject thiz, jlong nativePtr) {
    if (nativePtr != 0) {
        auto *llmInference = reinterpret_cast<LLMInference *>(nativePtr);
        llmInference->_stopGeneration.store(true);
        std::this_thread::sleep_for(std::chrono::milliseconds(200));
        delete llmInference;
    }
}

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

extern "C" JNIEXPORT jfloat JNICALL
Java_com_dg_fendai_core_LlamaEngine_getResponseGenerationSpeed(JNIEnv *env, jobject thiz,
        jlong modelPtr) {
    auto *llmInference = reinterpret_cast<LLMInference *>(modelPtr);
    return llmInference->getResponseGenerationTime();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_dg_fendai_core_LlamaEngine_getContextSizeUsed(JNIEnv *env, jobject thiz, jlong modelPtr) {
    auto *llmInference = reinterpret_cast<LLMInference *>(modelPtr);
    return llmInference->getContextSizeUsed();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_dg_fendai_core_LlamaEngine_close(JNIEnv *env, jobject thiz, jlong modelPtr) {
    auto *llmInference = reinterpret_cast<LLMInference *>(modelPtr);
    delete llmInference;
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_dg_fendai_core_LlamaEngine_startCompletion(JNIEnv *env, jobject thiz, jlong modelPtr,
        jstring prompt) {
    jboolean isCopy = true;
    const char *promptCstr = env->GetStringUTFChars(prompt, &isCopy);
    auto *llmInference = reinterpret_cast<LLMInference *>(modelPtr);
    llmInference->startCompletion(promptCstr);
    env->ReleaseStringUTFChars(prompt, promptCstr);
}

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

extern "C" JNIEXPORT void JNICALL
Java_com_dg_fendai_core_LlamaEngine_stopCompletion(JNIEnv *env, jobject thiz, jlong modelPtr) {
    auto *llmInference = reinterpret_cast<LLMInference *>(modelPtr);
    llmInference->stopCompletion();
}

extern "C" JNIEXPORT void JNICALL
Java_com_dg_fendai_core_LlamaEngine_clearChatHistory(JNIEnv *env, jobject thiz, jlong context_pointer) {
    auto *inference = reinterpret_cast<LLMInference *>(context_pointer);
    if (inference != nullptr) {
        inference->clearChatHistory();
    }
}

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

// ← ADD: load mmproj for vision support 4 mart
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

// ← ADD: start completion with image 4 mart
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

// ← ADD: free mmproj 4 mart
extern "C" JNIEXPORT void JNICALL
Java_com_dg_fendai_core_LlamaEngine_freeMmproj(JNIEnv *env, jobject thiz, jlong modelPtr) {
    auto *llmInference = reinterpret_cast<LLMInference *>(modelPtr);
    llmInference->freeMmproj();
}
