//
// Created by iwatchme on 2023/10/31.
//

#include <cstdlib>
#include "CrashHandler.h"
#include <csignal>
#include "CrashLogUtil.h"
#include "CrashDumpHelper.h"
#include <map>
#include "mutex"
#include <map>
#include <memory>
#include <mutex>
#include <new>
#include <string>
#include <sstream>
#include <thread>
#include <jni.h>
#include <unistd.h>

static std::map<int, struct sigaction> sOldHandlers;
static pid_t sTidToDump;    // guarded by sMutex
static pid_t sPidToDump;
static void *context;
static int sigNum;
static siginfo *sigInfo;
static std::mutex sMutex;
static std::condition_variable sCondition;


CrashDumpHelper *dumpHelper;

void CrashHandler::initWithSignal(JavaVM *jvm, JNIEnv *env, jclass kclass, jintArray signals) {

    dumpHelper = new CrashDumpHelper(jvm, env, kclass);

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
        if (nullptr == (ss.ss_sp = calloc(1, SIGNAL_CRASH_STACK_SIZE))) {
            break;
        }
        ss.ss_size = SIGNAL_CRASH_STACK_SIZE;
        ss.ss_flags = 0;
        if (0 != sigaltstack(&ss, nullptr)) {
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

        struct sigaction sigc{};
        sigc.sa_sigaction = [](int sig_num, siginfo *info, void *ctx) {
            std::unique_lock<std::mutex> lock{sMutex};
            sTidToDump = gettid();
            sPidToDump = getpid();
            sigNum = sig_num;
            context = ctx;
            sigInfo = info;

            LOGW("signal %d caught", sig_num);
            sCondition.notify_one();
            sCondition.wait(lock, [] { return sTidToDump == 0; });
            LOGI("resume old handlers: %d", sOldHandlers.size());
            auto it = sOldHandlers.find(sig_num);
            if (it != sOldHandlers.end()) {
                if (it->second.sa_flags & SA_SIGINFO) {
                    it->second.sa_sigaction(sig_num, info, ctx);
                } else {
                    it->second.sa_handler(sig_num);
                }
            }

        };
        sigfillset(&sigc.sa_mask);
        sigc.sa_flags = SA_SIGINFO | SA_ONSTACK | SA_RESTART;

        struct sigaction old_action;
        for (int i = 0; i < size; ++i) {
            LOGI("start register signal %d", signalsFromJava[i]);
            if (0 != sigaction(signalsFromJava[i], &sigc, &old_action)) {
                LOGE("register signal %d failed", signalsFromJava[i]);
                if (old_action.sa_handler != SIG_DFL && old_action.sa_handler != SIG_IGN) {
                    sOldHandlers[signalsFromJava[i]] = old_action;
                }
                handleException(env);
                // 失败后需要恢复原样
                if (needMask) {
                    pthread_sigmask(SIG_SETMASK, &old, nullptr);
                }
                break;
            }
        }
    } while (false);

    env->ReleaseIntArrayElements(signals, signalsFromJava, 0);
    std::thread{
            [] {
                std::unique_lock<std::mutex> lock{sMutex};
                sCondition.wait(lock, [] { return sTidToDump > 0; });
                LOGI("start dump stack");
                dumpHelper->dumpStacks(sPidToDump, sTidToDump, sigNum, sigInfo, context);
                sTidToDump = 0;
                LOGI("dump stack finished");
                sCondition.notify_one();
            }
    }.detach();

}

void CrashHandler::handleException(JNIEnv *env) {

}
