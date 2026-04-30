//
// Created by iwatchme on 2023/10/31.
//

#ifndef IWATCHME_ANDROID_CRASHHANDLER_H
#define IWATCHME_ANDROID_CRASHHANDLER_H

#include <jni.h>
#include <asm-generic/siginfo.h>


class CrashHandler {


public:
    void initWithSignal(JavaVM * javaVm, JNIEnv *env, jclass klass, jintArray signals);


private:
    void handleException(JNIEnv *env);

    int SIGNAL_CRASH_STACK_SIZE = 1024 * 128;
};


#endif //IWATCHME_ANDROID_CRASHHANDLER_H
