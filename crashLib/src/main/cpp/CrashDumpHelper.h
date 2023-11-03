//
// Created by iwatchme on 2023/11/3.
//



#ifndef JETPACKSTARTER_CRASHDUMPHELPER_H
#define JETPACKSTARTER_CRASHDUMPHELPER_H

#include <csignal>
#include <jni.h>


class CrashDumpHelper {


public:

    void dumpStacks(int sigNum, siginfo *siginfo, void *context);

    CrashDumpHelper(JavaVM *jvm, JNIEnv *env, jclass callClass) {
        this->env = env;
        this->callClass = callClass;
        this->javaVm = jvm;
    }

private:
    JNIEnv *env;
    JavaVM *javaVm;
    jclass callClass;
};


#endif //JETPACKSTARTER_CRASHDUMPHELPER_H
