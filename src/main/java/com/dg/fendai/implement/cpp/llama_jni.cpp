//
// Created by Bima Maulana Effendy on 01/10/25.
//
#include "LLMInference.h"
#include <jni>

extern "C" JNIEXPORT jlong JNICALL
Java_com_dg_fendai_core_LlamaEngine_loadModel(JNIEnv* env, jobject thiz, jstring modelPath, jfloat minP,
                                              jfloat temperature, jboolean storeChats, jlong contextSize,
                                              jstring chatTemplate, jint nThreads, jboolean useMmap, jboolean useMlock){

    jboolean isCopy = true;
    const char* modelPathCstr = env->GetStringUTFChars(modelPath, &isCopy);
    auto* llmInference = new LLMInference();
    const char* chatTemplateCstr = env->GetStringUTFChars(chatTemplate, &isCopy);

    try {
        llmInference->loadModel(modelPathCstr, minP, temperature, storeChats, contextSize, chatTemplateCstr, nThreads, useMmap, useMlock);
    } catch (std::runtime_error& error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
    }

    env->ReleaseStringUTFChars(modelPath, modelPathCstr);
    env->ReleaseStringUTFChars(chatTemplate, chatTemplateCstr);
    return reinterpret_cast<jlong>(llmInference);
}

extern "C" JNIEXPORT void JNICALL
Java_com_dg_fendai_core_LlamaEngine_addChatMessage(JNIEnv* env, jobject thiz, jlong modelPtr, jstring message,
                                                   jstring role) {

    jboolean isCopy = true;
    const char* messageCstr = env->GetStringUTFChars(message, &isCopy);
    const char* roleCstr = env->GetStringUTFChars(role, &isCopy);
    auto* llmInference = reinterpret_cast<LLMInference*>(modelPtr);
    llmInference->addChatMessage(messageCstr, roleCstr);
    env->ReleaseStringUTFChars(message, messageCstr);
    env->ReleaseStringUTFChars(role, roleCstr);

}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_dg_fendai_core_LlamaEngine_getResponseGenerationSpeed(JNIEnv* env, jobject thiz, jlong modelPtr) {
    auto* llmInference = reinterpret_cast<LLMInference*>(modelPtr);
    return llmInference->getResponseGenerationTime();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_dg_fendai_core_LlamaEngine_getContextSizeUsed(JNIEnv* env, jobject thiz, jlong modelPtr) {
    auto* llmInference = reinterpret_cast<LLMInference*>(modelPtr);
    return llmInference->getContextSizeUsed();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_dg_fendai_core_LlamaEngine_close(JNIEnv* env,jobject thiz, jlong modelPtr){
    auto* llmInference = reinterpret_cast<LLMInference*>(modelPtr);
    delete llmInference;
}

extern "C" JNIEXPORT void JNICALL
Java_com_dg_fendai_core_LlamaEngine_startCompletion(JNIEnv* env, jobject thiz, jlong modelPtr, jstring prompt) {
    jboolean    isCopy       = true;
    const char* promptCstr   = env->GetStringUTFChars(prompt, &isCopy);
    auto*       llmInference = reinterpret_cast<LLMInference*>(modelPtr);
    llmInference->startCompletion(promptCstr);
    env->ReleaseStringUTFChars(prompt, promptCstr);
}

