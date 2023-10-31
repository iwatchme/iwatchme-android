//
// Created by iwatchme on 2023/10/31.
//

#include <cstdlib>
#include "CrashHandler.h"
#include "signal.h"
#include "CrashLogUtil.h"

void CrashHandler::init_with_signal(JNIEnv *env, jclass klass, jintArray signals,
                                    void (*handler)(int, struct siginfo *, void *)) {

    jint *signalsFromJava = env->GetIntArrayElements(signals, 0);
    int size = env->GetArrayLength(signals);
    bool needMask;

    for (int i = 0; i < size; ++i) {
        if (signalsFromJava[i] == SIGQUIT) {
            needMask = true;
        }
    }

    do {
        sigset_t mask;
        sigset_t old;
        stack_t ss;
        if (NULL == (ss.ss_sp = calloc(1, SIGNAL_CRASH_STACK_SIZE))) {
            break;
        }
        ss.ss_size = SIGNAL_CRASH_STACK_SIZE;
        ss.ss_flags = 0;
        if (0 != sigaltstack(&ss, NULL)) {
            handleException(env);
            break;
        }

        if (needMask) {
            sigemptyset(&mask);
            sigaddset(&mask, SIGQUIT);
            if (0 != pthread_sigmask(SIG_UNBLOCK, &mask, &old)) {
                break;
            }
        }

        struct sigaction sigc;
        sigc.sa_sigaction = handler;
        sigfillset(&sigc.sa_mask);
        sigc.sa_flags = SA_SIGINFO | SA_ONSTACK | SA_RESTART;

        for (int i = 0; i < size; ++i) {
            LOGI("start register signal %d", signalsFromJava[i]);
            if (0 != sigaction(signalsFromJava[i], &sigc, NULL)) {
                LOGE("register signal %d failed", signalsFromJava[i]);
                handleException(env);
                // 失败后需要恢复原样
                if (needMask) {
                    pthread_sigmask(SIG_SETMASK, &old, NULL);
                }
                break;
            }
        }
    } while (false);

    env->ReleaseIntArrayElements(signals, signalsFromJava, 0);
}

void CrashHandler::handleException(JNIEnv *env) {

}
