//
// Created by iwatchme on 2023/11/3.
//

#include "CrashDumpHelper.h"
#include "CrashLogUtil.h"
#include <csignal>


void dumpSignalInfo(siginfo *info) {
    switch (info->si_signo) {
        case SIGILL:
            LOGI("signal SIGILL caught");
            switch (info->si_code) {
                case ILL_ILLOPC:
                    LOGI("illegal opcode");
                    break;
                case ILL_ILLOPN:
                    LOGI("illegal operand");
                    break;
                case ILL_ILLADR:
                    LOGI("illegal addressing mode");
                    break;
                case ILL_ILLTRP:
                    LOGI("illegal trap");
                    break;
                case ILL_PRVOPC:
                    LOGI("privileged opcode");
                    break;
                case ILL_PRVREG:
                    LOGI("privileged register");
                    break;
                case ILL_COPROC:
                    LOGI("coprocessor error");
                    break;
                case ILL_BADSTK:
                    LOGI("internal stack error");
                    break;
                default:
                    LOGI("code = %d", info->si_code);
                    break;
            }
            break;
        case SIGFPE:
            LOGI("signal SIGFPE caught");
            switch (info->si_code) {
                case FPE_INTDIV:
                    LOGI("integer divide by zero");
                    break;
                case FPE_INTOVF:
                    LOGI("integer overflow");
                    break;
                case FPE_FLTDIV:
                    LOGI("floating-point divide by zero");
                    break;
                case FPE_FLTOVF:
                    LOGI("floating-point overflow");
                    break;
                case FPE_FLTUND:
                    LOGI("floating-point underflow");
                    break;
                case FPE_FLTRES:
                    LOGI("floating-point inexact result");
                    break;
                case FPE_FLTINV:
                    LOGI("invalid floating-point operation");
                    break;
                case FPE_FLTSUB:
                    LOGI("subscript out of range");
                    break;
                default:
                    LOGI("code = %d", info->si_code);
                    break;
            }
            break;
        case SIGSEGV:
            LOGI("signal SIGSEGV caught");
            switch (info->si_code) {
                case SEGV_MAPERR:
                    LOGI("address not mapped to object");
                    break;
                case SEGV_ACCERR:
                    LOGI("invalid permissions for mapped object");
                    break;
                default:
                    LOGI("code = %d", info->si_code);
                    break;
            }
            break;
        case SIGBUS:
            LOGI("signal SIGBUS caught");
            switch (info->si_code) {
                case BUS_ADRALN:
                    LOGI("invalid address alignment");
                    break;
                case BUS_ADRERR:
                    LOGI("nonexistent physical address");
                    break;
                case BUS_OBJERR:
                    LOGI("object-specific hardware error");
                    break;
                default:
                    LOGI("code = %d", info->si_code);
                    break;
            }
            break;
        case SIGABRT:
            LOGI("signal SIGABRT caught");
            break;
        case SIGPIPE:
            LOGI("signal SIGPIPE caught");
            break;
        default:
            LOGI("signo %d caught", info->si_signo);
            LOGI("code = %d", info->si_code);
    }
    LOGI("errno = %d", info->si_errno);
}

void callJavaMethod(JavaVM *javaVm, JNIEnv *env, jclass callClass, int sigNum, const char *trace) {
    jint result = javaVm->AttachCurrentThread(&env, nullptr);
    if (result != JNI_OK) {
        return;
    }
    jmethodID methodId = env->GetStaticMethodID(callClass, "callFromNative",
                                                "(ILjava/lang/String;)V");
    jstring nativeStackTrace = env->NewStringUTF(trace);
    jint signal_tag = sigNum;
    env->CallStaticVoidMethod(callClass, methodId, signal_tag, nativeStackTrace);
    env->DeleteLocalRef(nativeStackTrace);
}


void CrashDumpHelper::dumpStacks(int sigNum, siginfo *siginfo, void *context) {
       dumpSignalInfo(siginfo);
       callJavaMethod(this->javaVm, this->env, this->callClass, sigNum, "trace");
}
