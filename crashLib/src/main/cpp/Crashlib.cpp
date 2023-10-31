#include <jni.h>
#include <string>
#include "CrashLogUtil.h"
#include "CrashHandler.h"


JavaVM *javaVm = NULL;
static jclass callClass;

void (*sig_func)(int, siginfo *, void *) = [](int sig_num, siginfo *info, void *ptr)  {
    LOGE("catch signal %d", sig_num);
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    javaVm = vm;
    jclass cls;

    if (NULL == vm) return -1;
    if (JNI_OK != (*vm).GetEnv((void **) &env, JNI_VERSION_1_6)) return -1;
    if (NULL == (cls = (*env).FindClass("com/iwatchme/crashlib/CrashLib"))) return -1;

    // 此时的cls仅仅是一个局部变量，如果错误引用会出现错误
    callClass = static_cast<jclass>((*env).NewGlobalRef(cls));


    return JNI_VERSION_1_6;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_iwatchme_crashlib_CrashLib_registerSignals(
        JNIEnv *env,
        jobject, jintArray signals) {
    LOGI("registerSignals");
    CrashHandler *handler = new CrashHandler();
    handler->init_with_signal(env, callClass, signals, sig_func);
    return true;
}



extern "C"
JNIEXPORT void JNICALL
Java_com_iwatchme_crashlib_CrashLib_raiseError(JNIEnv *env, jobject thiz) {
//    raise(SIGSEGV);
     LOGE("Start");
     *(int*)0 = 0;

}