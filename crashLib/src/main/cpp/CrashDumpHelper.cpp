//
// Created by iwatchme on 2023/11/3.
//

#include "CrashDumpHelper.h"
#include "CrashLogUtil.h"
#include <csignal>
#include <cstdlib>
#include <dlfcn.h>
#include <iomanip>
#include <sstream>
#include <string>
#include <ucontext.h>
#include "xunwind.h"

void dumpSignalInfo(siginfo *info) {
    if (info == nullptr) {
        LOGE("signal info is null");
        return;
    }

    switch (info->si_signo) {
        case SIGILL:
            LOGE("signal SIGILL caught");
            switch (info->si_code) {
                case ILL_ILLOPC:
                    LOGE("illegal opcode");
                    break;
                case ILL_ILLOPN:
                    LOGE("illegal operand");
                    break;
                case ILL_ILLADR:
                    LOGE("illegal addressing mode");
                    break;
                case ILL_ILLTRP:
                    LOGE("illegal trap");
                    break;
                case ILL_PRVOPC:
                    LOGE("privileged opcode");
                    break;
                case ILL_PRVREG:
                    LOGE("privileged register");
                    break;
                case ILL_COPROC:
                    LOGE("coprocessor error");
                    break;
                case ILL_BADSTK:
                    LOGE("internal stack error");
                    break;
                default:
                    LOGE("code = %d", info->si_code);
                    break;
            }
            break;
        case SIGFPE:
            LOGE("signal SIGFPE caught");
            switch (info->si_code) {
                case FPE_INTDIV:
                    LOGE("integer divide by zero");
                    break;
                case FPE_INTOVF:
                    LOGE("integer overflow");
                    break;
                case FPE_FLTDIV:
                    LOGE("floating-point divide by zero");
                    break;
                case FPE_FLTOVF:
                    LOGE("floating-point overflow");
                    break;
                case FPE_FLTUND:
                    LOGE("floating-point underflow");
                    break;
                case FPE_FLTRES:
                    LOGE("floating-point inexact result");
                    break;
                case FPE_FLTINV:
                    LOGE("invalid floating-point operation");
                    break;
                case FPE_FLTSUB:
                    LOGE("subscript out of range");
                    break;
                default:
                    LOGE("code = %d", info->si_code);
                    break;
            }
            break;
        case SIGSEGV:
            LOGE("signal SIGSEGV caught");
            switch (info->si_code) {
                case SEGV_MAPERR:
                    LOGE("address not mapped to object");
                    break;
                case SEGV_ACCERR:
                    LOGE("invalid permissions for mapped object");
                    break;
                default:
                    LOGE("code = %d", info->si_code);
                    break;
            }
            break;
        case SIGBUS:
            LOGE("signal SIGBUS caught");
            switch (info->si_code) {
                case BUS_ADRALN:
                    LOGE("invalid address alignment");
                    break;
                case BUS_ADRERR:
                    LOGE("nonexistent physical address");
                    break;
                case BUS_OBJERR:
                    LOGE("object-specific hardware error");
                    break;
                default:
                    LOGE("code = %d", info->si_code);
                    break;
            }
            break;
        case SIGABRT:
            LOGE("signal SIGABRT caught");
            break;
        case SIGPIPE:
            LOGE("signal SIGPIPE caught");
            break;
        default:
            LOGE("signo %d caught", info->si_signo);
            LOGE("code = %d", info->si_code);
            break;
    }
    LOGE("errno = %d", info->si_errno);
}

static uintptr_t getProgramCounter(void *context) {
    if (context == nullptr) {
        return 0;
    }

    auto *ucontext = reinterpret_cast<ucontext_t *>(context);
#if defined(__aarch64__)
    return static_cast<uintptr_t>(ucontext->uc_mcontext.pc);
#elif defined(__arm__)
    return static_cast<uintptr_t>(ucontext->uc_mcontext.arm_pc);
#elif defined(__x86_64__)
    return static_cast<uintptr_t>(ucontext->uc_mcontext.gregs[REG_RIP]);
#elif defined(__i386__)
    return static_cast<uintptr_t>(ucontext->uc_mcontext.gregs[REG_EIP]);
#else
    return 0;
#endif
}

static std::string buildCrashPcSummary(void *context) {
    uintptr_t pc = getProgramCounter(context);
    if (pc == 0) {
        return "pc=<unknown>";
    }

    Dl_info info{};
    if (dladdr(reinterpret_cast<void *>(pc), &info) == 0) {
        std::ostringstream fallback;
        fallback << "pc=0x" << std::hex << pc << " (dladdr failed)";
        return fallback.str();
    }

    uintptr_t imageBase = reinterpret_cast<uintptr_t>(info.dli_fbase);
    uintptr_t symbolAddr = reinterpret_cast<uintptr_t>(info.dli_saddr);

    std::ostringstream oss;
    oss << "pc=0x" << std::hex << pc;
    if (info.dli_fname != nullptr) {
        oss << " module=" << info.dli_fname;
        if (imageBase != 0) {
            oss << "+0x" << std::hex << (pc - imageBase);
        }
    }
    if (info.dli_sname != nullptr && symbolAddr != 0) {
        oss << " symbol=" << info.dli_sname << "+0x" << std::hex << (pc - symbolAddr);
    }
    return oss.str();
}

static std::string formatFrameLine(int index, uintptr_t pc) {
    std::ostringstream oss;
    oss << "#" << std::setw(2) << std::setfill('0') << index << " pc=0x" << std::hex << pc;

    Dl_info info{};
    if (dladdr(reinterpret_cast<void *>(pc), &info) != 0) {
        uintptr_t imageBase = reinterpret_cast<uintptr_t>(info.dli_fbase);
        uintptr_t symbolAddr = reinterpret_cast<uintptr_t>(info.dli_saddr);
        if (info.dli_fname != nullptr) {
            oss << " module=" << info.dli_fname;
            if (imageBase != 0) {
                oss << "+0x" << std::hex << (pc - imageBase);
            }
        }
        if (info.dli_sname != nullptr && symbolAddr != 0) {
            oss << " symbol=" << info.dli_sname << "+0x" << std::hex << (pc - symbolAddr);
        }
    }
    return oss.str();
}

static std::string buildFpFallbackTrace(void *context, size_t maxDepth = 16) {
#if defined(__aarch64__)
    if (context == nullptr) {
        return "";
    }

    auto *ucontext = reinterpret_cast<ucontext_t *>(context);
    uintptr_t fp = static_cast<uintptr_t>(ucontext->uc_mcontext.regs[29]);
    uintptr_t sp = static_cast<uintptr_t>(ucontext->uc_mcontext.sp);
    uintptr_t pc = static_cast<uintptr_t>(ucontext->uc_mcontext.pc);
    uintptr_t lr = static_cast<uintptr_t>(ucontext->uc_mcontext.regs[30]);

    std::ostringstream frames;
    size_t index = 0;

    if (pc != 0) {
        frames << formatFrameLine(static_cast<int>(index++), pc);
    }
    if (lr != 0 && index < maxDepth) {
        frames << "\n" << formatFrameLine(static_cast<int>(index++), lr);
    }

    while (fp != 0 && index < maxDepth) {
        if ((fp & 0xF) != 0) {
            break;
        }
        if (fp < sp || fp - sp > 8 * 1024 * 1024UL) {
            break;
        }

        auto *record = reinterpret_cast<uintptr_t *>(fp);
        uintptr_t nextFp = record[0];
        uintptr_t retAddr = record[1];
        if (retAddr == 0 || nextFp <= fp) {
            break;
        }

        frames << "\n" << formatFrameLine(static_cast<int>(index++), retAddr);
        fp = nextFp;
    }

    return frames.str();
#else
    (void) context;
    (void) maxDepth;
    return "";
#endif
}

static std::string buildXunwindTrace(pid_t pid, pid_t tid, void *context) {
    std::string traceSections;

    char *cfiTrace = xunwind_cfi_get(pid, tid, context, LOG_TAG);
    if (cfiTrace != nullptr && cfiTrace[0] != '\0') {
        traceSections.append("[xunwind-cfi-context]\n").append(cfiTrace);
    } else {
        LOGW("xunwind_cfi_get with ucontext failed");
    }
    if (cfiTrace != nullptr) {
        free(cfiTrace);
    }

    if (traceSections.empty()) {
        char *cfiNoContextTrace = xunwind_cfi_get(pid, tid, nullptr, LOG_TAG);
        if (cfiNoContextTrace != nullptr && cfiNoContextTrace[0] != '\0') {
            traceSections.append("[xunwind-cfi-no-context]\n").append(cfiNoContextTrace);
        } else {
            LOGW("xunwind_cfi_get without context failed");
        }
        if (cfiNoContextTrace != nullptr) {
            free(cfiNoContextTrace);
        }
    }

    uintptr_t frames[64] = {0};
    size_t ehFrames = xunwind_eh_unwind(frames, 64, context);
    if (ehFrames > 0) {
        char *ehTrace = xunwind_frames_get(frames, ehFrames, LOG_TAG);
        if (ehTrace != nullptr && ehTrace[0] != '\0') {
            if (!traceSections.empty()) {
                traceSections.append("\n");
            }
            traceSections.append("[xunwind-eh]\n").append(ehTrace);
        }
        if (ehTrace != nullptr) {
            free(ehTrace);
        }
    }

    size_t fpFrames = xunwind_fp_unwind(frames, 64, context);
    if (fpFrames > 0) {
        char *fpTrace = xunwind_frames_get(frames, fpFrames, LOG_TAG);
        if (fpTrace != nullptr && fpTrace[0] != '\0') {
            if (!traceSections.empty()) {
                traceSections.append("\n");
            }
            traceSections.append("[xunwind-fp]\n").append(fpTrace);
        }
        if (fpTrace != nullptr) {
            free(fpTrace);
        }
    }

    return traceSections;
}

static void logNativeStackTrace(int sigNum, pid_t pid, pid_t tid, const char *trace) {
    const char *safeTrace = trace != nullptr ? trace : "<xunwind returned null trace>";
    LOGE("native crash captured. signal=%d pid=%d tid=%d", sigNum, pid, tid);
    LOGE("native trace begin");
    LOGE("%s", safeTrace);
    LOGE("native trace end");
}

static void callJavaMethod(JavaVM *javaVm, jclass callClass, int sigNum, const char *trace) {
    if (javaVm == nullptr || callClass == nullptr) {
        LOGE("skip java callback because javaVm or callClass is null");
        return;
    }

    JNIEnv *env = nullptr;
    bool attachedInThisCall = false;

    jint getEnvResult = javaVm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (getEnvResult == JNI_EDETACHED) {
        if (javaVm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            LOGE("AttachCurrentThread failed");
            return;
        }
        attachedInThisCall = true;
    } else if (getEnvResult != JNI_OK || env == nullptr) {
        LOGE("GetEnv failed with code=%d", getEnvResult);
        return;
    }

    jmethodID methodId = env->GetStaticMethodID(callClass, "callFromNative", "(ILjava/lang/String;)V");
    if (methodId == nullptr) {
        LOGE("GetStaticMethodID callFromNative failed");
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
        if (attachedInThisCall) {
            javaVm->DetachCurrentThread();
        }
        return;
    }

    const char *safeTrace = trace != nullptr ? trace : "<xunwind returned null trace>";
    jstring nativeStackTrace = env->NewStringUTF(safeTrace);
    if (nativeStackTrace == nullptr) {
        LOGE("NewStringUTF failed");
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
        if (attachedInThisCall) {
            javaVm->DetachCurrentThread();
        }
        return;
    }

    env->CallStaticVoidMethod(callClass, methodId, static_cast<jint>(sigNum), nativeStackTrace);
    if (env->ExceptionCheck()) {
        LOGE("Exception thrown while calling callFromNative");
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    env->DeleteLocalRef(nativeStackTrace);

    if (attachedInThisCall) {
        javaVm->DetachCurrentThread();
    }
}

void CrashDumpHelper::dumpStacks(pid_t pid, pid_t tid, int sigNum, siginfo *siginfo, void *context) {
    dumpSignalInfo(siginfo);

    std::string pcSummary = buildCrashPcSummary(context);
    std::string xunwindTrace = buildXunwindTrace(pid, tid, context);
    std::string fpFallbackTrace = buildFpFallbackTrace(context);

    std::string finalTrace = pcSummary;
    if (!xunwindTrace.empty()) {
        finalTrace.append("\n").append(xunwindTrace);
    }
    if (!fpFallbackTrace.empty()) {
        finalTrace.append("\n[fp-fallback]\n").append(fpFallbackTrace);
    }
    if (xunwindTrace.empty() && fpFallbackTrace.empty()) {
        finalTrace.append("\n<xunwind returned null trace>");
    }

    logNativeStackTrace(sigNum, pid, tid, finalTrace.c_str());
    callJavaMethod(this->javaVm, this->callClass, sigNum, finalTrace.c_str());
}
