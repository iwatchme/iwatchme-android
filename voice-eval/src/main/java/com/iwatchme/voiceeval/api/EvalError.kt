package com.iwatchme.voiceeval.api

/**
 * 评测引擎可能抛出的所有错误类型（密封类）。
 * 设计目标：把分散的 Throwable 收敛到固定几类，便于 UI 写穷举 when 分支。
 *
 * Sealed hierarchy of every error the evaluation engine can surface.
 * Goal: collapse arbitrary Throwables into a fixed set so UI code can pattern-match exhaustively.
 */
sealed class EvalError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    // 未授予 RECORD_AUDIO 权限。
    // RECORD_AUDIO permission was not granted.
    class PermissionDenied : EvalError("RECORD_AUDIO permission not granted")

    // AudioRecord 初始化失败（设备占用、参数不支持等）。
    // AudioRecord could not be initialized (device busy, unsupported params, …).
    class AudioInitFailed(cause: Throwable? = null) :
        EvalError("AudioRecord initialization failed", cause)

    // 录音 / 写文件期间的 IO 异常。
    // I/O exception during capture or file writing.
    class IoFailure(cause: Throwable) : EvalError("I/O failure during capture", cause)

    // 打分后端拒绝了本次请求（凭证错误、入参非法等）。
    // The scoring backend rejected the request (auth error, bad params, …).
    class ScorerFailure(cause: Throwable) : EvalError("Scorer rejected the request", cause)

    // 调用方主动取消。
    // Caller cancelled the evaluation explicitly.
    class Cancelled : EvalError("Evaluation was cancelled by caller")
}
