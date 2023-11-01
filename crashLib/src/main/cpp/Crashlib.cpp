#include <jni.h>
#include <string>
#include "CrashLogUtil.h"
#include "CrashHandler.h"
#include "sys/eventfd.h"
#include <unistd.h>
#include <thread>
#include <mutex>
#include <condition_variable>


JavaVM *javaVm = nullptr;
static jclass callClass;
static int eventId = -1;
uint64_t data;
std::mutex mutex;
std::condition_variable cv;

void (*sig_func)(int, siginfo *, void *) = [](int sig_num, siginfo *info, void *ptr) {
//    uint64_t data = sig_num;
//    if (eventId > 0) {
//       write(eventId, &data, sizeof(data));
//    }
    // Acquire lock
    std::unique_lock<std::mutex> lock(mutex);

    // Write data
    data = sig_num;

    // Notify waiting thread
    cv.notify_one();

};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    javaVm = vm;
    jclass cls;

    if (nullptr == vm) return -1;
    if (JNI_OK != (*vm).GetEnv((void **) &env, JNI_VERSION_1_6)) return -1;
    if (nullptr == (cls = (*env).FindClass("com/iwatchme/crashlib/CrashLib"))) return -1;

    // 此时的cls仅仅是一个局部变量，如果错误引用会出现错误
    callClass = static_cast<jclass>((*env).NewGlobalRef(cls));


    return JNI_VERSION_1_6;
}

void* execute_in_thread(void *argvs) {
    LOGI("start execute: %d %d" , getpid(), (unsigned int)pthread_self());
    JNIEnv *env;
    {
        jint result = javaVm->AttachCurrentThread(&env, nullptr);
        if (result != JNI_OK) {
            return nullptr;
        }
    }


    std::unique_lock<std::mutex> lock(mutex);

    // Wait for signal
    cv.wait(lock);

//    uint64_t data;
//    read(eventId, &data, sizeof(data));
//
//    LOGE("env2: %d, %d", data, eventId);



    jmethodID methodId = env->GetStaticMethodID(callClass, "callFromNative",
                                                "(ILjava/lang/String;)V");
    jstring nativeStackTrace = env->NewStringUTF("");
    jint signal_tag = data;
    env->CallStaticVoidMethod(callClass, methodId, signal_tag, nativeStackTrace);
    env->DeleteLocalRef(nativeStackTrace);
    return nullptr;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_iwatchme_crashlib_CrashLib_registerSignals(
        JNIEnv *env,
        jobject, jintArray signals) {
    LOGI("registerSignals %d, %d", getpid(), (unsigned int)pthread_self());
    CrashHandler *handler = new CrashHandler();
    handler->init_with_signal(env, callClass, signals, sig_func);
//    eventId = eventfd(0, EFD_CLOEXEC);
//    LOGI("init eventId: %d", eventId);

    pthread_t thread;
    int result = pthread_create(&thread, nullptr, execute_in_thread, nullptr);
    if (0 != result) {
     //TODO
    }
    return true;
}



extern "C"
JNIEXPORT void JNICALL
Java_com_iwatchme_crashlib_CrashLib_raiseError(JNIEnv *env, jobject thiz) {
//    raise(SIGSEGV);
    LOGE("Start");
    *(int *) 0 = 0;

}