#include "rag.h"
#include <jni.h>

using fendai::rag::RagEngine;

namespace {
	void throwJavaException(JNIEnv* env, const char* message) {
		if (env->ExceptionCheck()) return;
		jclass exceptionClass = env->FindClass("java/lang/IllegalStateException");
		if (exceptionClass != nullptr) env->ThrowNew(exceptionClass, message);
	}
}

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
Java_com_dg_fendai_core_RagEngine_loadPath(JNIEnv* env, jobject, jlong handle, jstring path,
		jint chunkWords, jint chunkOverlap) {
	const char* pathChars = env->GetStringUTFChars(path, nullptr);
	jint count = 0;
	try {
		count = reinterpret_cast<RagEngine*>(handle)->loadPath(pathChars,
				static_cast<size_t>(chunkWords), static_cast<size_t>(chunkOverlap));
	} catch (const std::exception& exception) {
		throwJavaException(env, exception.what());
	}
	env->ReleaseStringUTFChars(path, pathChars);
	return count;
}

extern "C" JNIEXPORT void JNICALL
Java_com_dg_fendai_core_RagEngine_loadEmbeddingModel(JNIEnv* env, jobject, jlong handle,
		jstring modelPath) {
	const char* pathChars = env->GetStringUTFChars(modelPath, nullptr);
	try {
		reinterpret_cast<RagEngine*>(handle)->loadEmbeddingModel(pathChars);
	} catch (const std::exception& exception) {
		throwJavaException(env, exception.what());
	}
	env->ReleaseStringUTFChars(modelPath, pathChars);
}

extern "C" JNIEXPORT void JNICALL
Java_com_dg_fendai_core_RagEngine_clearEmbeddingModel(JNIEnv*, jobject, jlong handle) {
	reinterpret_cast<RagEngine*>(handle)->clearEmbeddingModel();
}

extern "C" JNIEXPORT void JNICALL
Java_com_dg_fendai_core_RagEngine_setStopWords(JNIEnv* env, jobject, jlong handle,
		jobjectArray stopWords) {
	std::vector<std::string> values;
	if (stopWords != nullptr) {
		const jsize count = env->GetArrayLength(stopWords);
		values.reserve(static_cast<size_t>(count));
		for (jsize index = 0; index < count; ++index) {
			auto word = static_cast<jstring>(env->GetObjectArrayElement(stopWords, index));
			if (word == nullptr) continue;
			const char* chars = env->GetStringUTFChars(word, nullptr);
			if (chars == nullptr) {
				env->DeleteLocalRef(word);
				return;
			}
			values.emplace_back(chars);
			env->ReleaseStringUTFChars(word, chars);
			env->DeleteLocalRef(word);
		}
	}
	try {
		reinterpret_cast<RagEngine*>(handle)->setStopWords(values);
	} catch (const std::exception& exception) {
		throwJavaException(env, exception.what());
	}
}

extern "C" JNIEXPORT void JNICALL
Java_com_dg_fendai_core_RagEngine_resetStopWords(JNIEnv*, jobject, jlong handle) {
	reinterpret_cast<RagEngine*>(handle)->resetStopWords();
}

extern "C" JNIEXPORT void JNICALL
Java_com_dg_fendai_core_RagEngine_addDocument(JNIEnv* env, jobject, jlong handle,
		jstring source, jstring text) {
	const char* sourceChars = env->GetStringUTFChars(source, nullptr);
	const char* textChars = env->GetStringUTFChars(text, nullptr);
	try {
		reinterpret_cast<RagEngine*>(handle)->addDocument(sourceChars, textChars);
	} catch (const std::exception& exception) {
		throwJavaException(env, exception.what());
	}
	env->ReleaseStringUTFChars(source, sourceChars);
	env->ReleaseStringUTFChars(text, textChars);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_dg_fendai_core_RagEngine_search(JNIEnv* env, jobject, jlong handle, jstring query,
		jint topK) {
	const char* queryChars = env->GetStringUTFChars(query, nullptr);
	std::vector<fendai::rag::SearchResult> results;
	try {
		results = reinterpret_cast<RagEngine*>(handle)->search(queryChars, topK);
	} catch (const std::exception& exception) {
		throwJavaException(env, exception.what());
	}
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
		jint topK, jint maxContextCharacters, jboolean allowGeneralKnowledgeWhenNoMatch,
		jint answerMode) {
	const char* queryChars = env->GetStringUTFChars(query, nullptr);
	std::string prompt;
	try {
		const auto mode = answerMode == static_cast<jint>(
				fendai::rag::AnswerMode::DOCUMENTS_AND_MODEL_BACKGROUND)
				? fendai::rag::AnswerMode::DOCUMENTS_AND_MODEL_BACKGROUND
				: fendai::rag::AnswerMode::DOCUMENT_ONLY;
		prompt = reinterpret_cast<RagEngine*>(handle)->buildPrompt(queryChars, topK,
				maxContextCharacters, allowGeneralKnowledgeWhenNoMatch == JNI_TRUE, mode);
	} catch (const std::exception& exception) {
		throwJavaException(env, exception.what());
	}
	env->ReleaseStringUTFChars(query, queryChars);
	return env->NewStringUTF(prompt.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_dg_fendai_core_RagEngine_size(JNIEnv*, jobject, jlong handle) {
	return static_cast<jint>(reinterpret_cast<RagEngine*>(handle)->size());
}
