//
// Created by iwatchme on 2023/11/3.
//



#ifndef IWATCHME_ANDROID_CRASHDUMPHELPER_H
#define IWATCHME_ANDROID_CRASHDUMPHELPER_H

#include <csignal>
#include <jni.h>


class CrashDumpHelper {


public:

    void dumpStacks(pid_t pid, pid_t tid, int sigNum, siginfo *siginfo, void *context);

    CrashDumpHelper(JavaVM *jvm, JNIEnv *, jclass callClass) {
        this->callClass = callClass;
        this->javaVm = jvm;
    }

private:
    JavaVM *javaVm;
    jclass callClass;

    void dumpStacks(int sigNum, siginfo *siginfo, void *context);
};


#endif //IWATCHME_ANDROID_CRASHDUMPHELPER_H
