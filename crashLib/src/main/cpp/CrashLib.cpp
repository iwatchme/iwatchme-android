#include <jni.h>
#include <string>
#include "CrashLogUtil.h"
#include "CrashHandler.h"
#include "sys/eventfd.h"
#include <unistd.h>
#include <thread>
#include <mutex>
#include <condition_variable>
#include "CrashDumpHelper.h"


static jclass callClass;
auto *crashHandler = new CrashHandler();
JavaVM *javaVm;


void test2();

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    jclass cls;
    javaVm = vm;

    if (nullptr == vm) return -1;
    if (JNI_OK != (*vm).GetEnv((void **) &env, JNI_VERSION_1_6)) return -1;
    if (nullptr == (cls = (*env).FindClass("com/iwatchme/crashlib/CrashLib"))) return -1;

    // 此时的cls仅仅是一个局部变量，如果错误引用会出现错误
    callClass = static_cast<jclass>((*env).NewGlobalRef(cls));


    return JNI_VERSION_1_6;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_iwatchme_crashlib_CrashLib_registerSignals(
        JNIEnv *env,
        jobject, jintArray signals) {
    LOGI("registerSignals %d, %d", getpid(), (unsigned int) pthread_self());
    crashHandler->initWithSignal(javaVm, env, callClass, signals);
    return true;
}







extern "C"
JNIEXPORT void JNICALL
Java_com_iwatchme_crashlib_CrashLib_raiseError(JNIEnv *env, jobject thiz) {
//    raise(SIGSEGV);
    LOGE("Start");
    *(int *) 0 = 0;
}