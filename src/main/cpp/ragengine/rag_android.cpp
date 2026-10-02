#include "rag.h"
#include <jni.h>

using fendai::rag::RagEngine;

extern "C" JNIEXPORT jlong JNICALL
Java_com_dg_fendai_core_RagEngine_create(JNIEnv*, jobject) {
	return reinterpret_cast<jlong>(new RagEngine());
}

extern "C" JNIEXPORT void JNICALL
Java_com_dg_fendai_core_RagEngine_destroy(JNIEnv*, jobject, jlong handle) {
	delete reinterpret_cast<RagEngine*>(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_dg_fendai_core_RagEngine_clear(JNIEnv*, jobject, jlong handle) {
	reinterpret_cast<RagEngine*>(handle)->clear();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_dg_fendai_core_RagEngine_loadPath(JNIEnv* env, jobject, jlong handle, jstring path) {
	const char* pathChars = env->GetStringUTFChars(path, nullptr);
	const jint count = reinterpret_cast<RagEngine*>(handle)->loadPath(pathChars);
	env->ReleaseStringUTFChars(path, pathChars);
	return count;
}

extern "C" JNIEXPORT void JNICALL
Java_com_dg_fendai_core_RagEngine_addDocument(JNIEnv* env, jobject, jlong handle,
		jstring source, jstring text) {
	const char* sourceChars = env->GetStringUTFChars(source, nullptr);
	const char* textChars = env->GetStringUTFChars(text, nullptr);
	reinterpret_cast<RagEngine*>(handle)->addDocument(sourceChars, textChars);
	env->ReleaseStringUTFChars(source, sourceChars);
	env->ReleaseStringUTFChars(text, textChars);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_dg_fendai_core_RagEngine_search(JNIEnv* env, jobject, jlong handle, jstring query,
		jint topK) {
	const char* queryChars = env->GetStringUTFChars(query, nullptr);
	const auto results = reinterpret_cast<RagEngine*>(handle)->search(queryChars, topK);
	env->ReleaseStringUTFChars(query, queryChars);
	jclass stringClass = env->FindClass("java/lang/String");
	jobjectArray output = env->NewObjectArray(static_cast<jsize>(results.size()), stringClass, nullptr);
	for (jsize index = 0; index < static_cast<jsize>(results.size()); ++index) {
		const std::string value = results[index].source + "\t" + std::to_string(results[index].score)
				+ "\t" + results[index].text;
		env->SetObjectArrayElement(output, index, env->NewStringUTF(value.c_str()));
	}
	return output;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_dg_fendai_core_RagEngine_buildPrompt(JNIEnv* env, jobject, jlong handle, jstring query,
		jint topK, jint maxContextCharacters, jboolean allowGeneralKnowledgeWhenNoMatch) {
	const char* queryChars = env->GetStringUTFChars(query, nullptr);
	const auto prompt = reinterpret_cast<RagEngine*>(handle)->buildPrompt(queryChars, topK,
			maxContextCharacters, allowGeneralKnowledgeWhenNoMatch == JNI_TRUE);
	env->ReleaseStringUTFChars(query, queryChars);
	return env->NewStringUTF(prompt.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_dg_fendai_core_RagEngine_size(JNIEnv*, jobject, jlong handle) {
	return static_cast<jint>(reinterpret_cast<RagEngine*>(handle)->size());
}
