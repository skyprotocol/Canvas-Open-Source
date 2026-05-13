//
// Created by maks on 11.12.2022.
//

#include "fileselector.h"
#include "Canvas/Canvas.h"
#include "../../include/misc/visibility.h"
#include <jni.h>
#include <android/log.h>

jclass class_FileSelector;
jclass class_String;
jmethodID method_nselectFile;
jmethodID method_nselectFileMulti;
jmethodID method_nselectFiles;


PRIVATE_API void fsel_setup(JNIEnv* env) {
    class_FileSelector = (jclass)env->NewGlobalRef(env->FindClass("git/artdeell/skymodloader/FileSelector"));
    class_String       = (jclass)env->NewGlobalRef(env->FindClass("java/lang/String"));
    method_nselectFile      = env->GetStaticMethodID(class_FileSelector, "nselectFile",      "(Ljava/lang/String;JZ)Z");
    method_nselectFileMulti = env->GetStaticMethodID(class_FileSelector, "nselectFileMulti", "([Ljava/lang/String;JZ)Z");
    method_nselectFiles     = env->GetStaticMethodID(class_FileSelector, "nselectFiles",     "([Ljava/lang/String;J)Z");
}
extern "C"
JNIEXPORT void JNICALL
Java_git_artdeell_skymodloader_FileSelector_callbackFunctionCall(JNIEnv *env, jclass clazz,
                                                                 jlong cb, jint fd) {
    auto callbackFunction = (callback_function)cb;
    callbackFunction(fd);
}

extern "C"
JNIEXPORT void JNICALL
Java_git_artdeell_skymodloader_FileSelector_multiCallbackFunctionCall(JNIEnv *env, jclass clazz,
                                                                       jlong cb, jintArray fds) {
    auto callback = (callback_function_files)cb;
    if (!fds) {
        callback(0, nullptr);
        return;
    }
    const jsize n = env->GetArrayLength(fds);
    if (n == 0) {
        callback(0, nullptr);
        return;
    }
    jint* native = env->GetIntArrayElements(fds, nullptr);
    if (!native) {
        env->ExceptionClear();
        callback(0, nullptr);
        return;
    }
    callback(static_cast<size_t>(n),
             reinterpret_cast<const int*>(native));
    env->ReleaseIntArrayElements(fds, native, JNI_ABORT);
}

// Log + clear any pending Java exception; otherwise the next JNI call aborts.
static bool fsel_check_exception(JNIEnv* env, const char* where) {
    if (env->ExceptionCheck()) {
        __android_log_print(ANDROID_LOG_ERROR, "fileselector",
                            "JNI exception pending after %s; clearing", where);
        env->ExceptionDescribe();
        env->ExceptionClear();
        return true;
    }
    return false;
}

// Returns null on failure; any pending exception is already cleared.
static jobjectArray fsel_build_mime_array(JNIEnv* env,
                                          const char* const* mime_types,
                                          size_t count) {
    jobjectArray arr = env->NewObjectArray(
        static_cast<jsize>(count), class_String, nullptr);
    if (!arr || fsel_check_exception(env, "NewObjectArray")) return nullptr;

    for (size_t i = 0; i < count; ++i) {
        jstring s = env->NewStringUTF(mime_types[i]);
        if (!s || fsel_check_exception(env, "NewStringUTF")) {
            env->DeleteLocalRef(arr);
            return nullptr;
        }
        env->SetObjectArrayElement(arr, static_cast<jsize>(i), s);
        env->DeleteLocalRef(s);
        if (fsel_check_exception(env, "SetObjectArrayElement")) {
            env->DeleteLocalRef(arr);
            return nullptr;
        }
    }
    return arr;
}

bool requestFileMulti(const char* const* mime_types, size_t mime_type_count,
                      callback_function callback, bool save) {
    if (!mime_types || mime_type_count == 0) return false;

    JNIEnv* env;
    if (Canvas::javaVM->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
        __android_log_print(ANDROID_LOG_ERROR, "fileselector", "Not on ImGui UI thread????");
        abort();
    }

    jobjectArray mimeArr = fsel_build_mime_array(env, mime_types, mime_type_count);
    if (!mimeArr) return false;

    jboolean result = env->CallStaticBooleanMethod(
        class_FileSelector, method_nselectFileMulti,
        mimeArr, (jlong)callback, save);
    const bool exceptionRaised = fsel_check_exception(env, "CallStaticBooleanMethod");

    env->DeleteLocalRef(mimeArr);

    if (exceptionRaised) return false;
    return result == JNI_TRUE;
}

bool requestFiles(const char* const* mime_types, size_t mime_type_count,
                  callback_function_files callback) {
    if (!mime_types || mime_type_count == 0) return false;

    JNIEnv* env;
    if (Canvas::javaVM->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
        __android_log_print(ANDROID_LOG_ERROR, "fileselector", "Not on ImGui UI thread????");
        abort();
    }

    jobjectArray mimeArr = fsel_build_mime_array(env, mime_types, mime_type_count);
    if (!mimeArr) return false;

    jboolean result = env->CallStaticBooleanMethod(
        class_FileSelector, method_nselectFiles,
        mimeArr, (jlong)callback);
    const bool exceptionRaised = fsel_check_exception(env, "CallStaticBooleanMethod");

    env->DeleteLocalRef(mimeArr);

    if (exceptionRaised) return false;
    return result == JNI_TRUE;
}

bool requestFile(const char* mime_type, callback_function callback, bool save) {
    return requestFileMulti(&mime_type, 1, callback, save);
}