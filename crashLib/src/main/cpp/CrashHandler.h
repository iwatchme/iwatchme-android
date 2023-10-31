//
// Created by iwatchme on 2023/10/31.
//

#ifndef JETPACKSTARTER_CRASHHANDLER_H
#define JETPACKSTARTER_CRASHHANDLER_H

#include <jni.h>


class CrashHandler {


public:
    void init_with_signal(JNIEnv *env, jclass klass,
                          jintArray signals, void (*handler)(int, struct siginfo *, void *));


private:
    void handleException(JNIEnv *env);

    int SIGNAL_CRASH_STACK_SIZE = 1024 * 128;
};


#endif //JETPACKSTARTER_CRASHHANDLER_H
