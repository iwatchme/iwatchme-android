package com.iwatchme.voiceeval.api

/**
 * [com.iwatchme.voiceeval.VoiceEvalEngine.evaluate] 输出的状态流（密封类）。
 *
 * 引擎本身是一个状态机：Idle → Preparing → Recording* → Scoring →
 * (Completed | Failed)。Recording 状态会被持续发射并携带实时分贝值，
 * 这样 UI 不必另开通道也能驱动音量条。
 *
 * Sealed state stream emitted by [com.iwatchme.voiceeval.VoiceEvalEngine.evaluate].
 *
 * The engine is a state machine: Idle → Preparing → Recording* → Scoring →
 * (Completed | Failed). Recording emits repeatedly with live decibel readings
 * so the UI can drive a VU meter without a second channel.
 */
sealed class EvalState {

    // 空闲：未启动任何评测。
    // Idle: no evaluation in flight.
    object Idle : EvalState()

    // 准备中：权限校验、AudioRecord 初始化、文件创建等前置工作。
    // Preparing: permission check, AudioRecord init, output file creation, etc.
    object Preparing : EvalState()

    /**
     * 录音过程中持续发射。
     * [currentDb] 是滚动 RMS 分贝值，[elapsedMs] 是从首次 Recording 算起的墙钟时长。
     *
     * Streamed during active capture. [currentDb] is the rolling RMS in dBFS;
     * [elapsedMs] is wall-clock since [Recording] first started.
     */
    data class Recording(
        val currentDb: Int,
        val elapsedMs: Long,
    ) : EvalState()

    // 录音结束、等待打分后端返回。
    // Audio capture stopped, waiting for the scorer to return.
    object Scoring : EvalState()

    /**
     * 软提示：用户长时间静默。引擎不会立刻终止；
     * UI 可借此提示「请大声朗读」。
     *
     * Soft signal — the user has been silent for too long. The engine keeps
     * running; the UI may choose to show a "speak louder" hint.
     */
    data class SilenceHint(val level: SilenceLevel) : EvalState()

    // 终态：评测成功，附带完整结果。
    // Terminal state: evaluation succeeded with the full result attached.
    data class Completed(val result: EvalResult) : EvalState()

    // 终态：评测失败，附带具体错误类型。
    // Terminal state: evaluation failed with a typed error attached.
    data class Failed(val error: EvalError) : EvalState()
}

// 静默检测级别：用于区分「轻提示」和「强制停止」两档信号。
// Silence detector level: distinguishes a soft hint from a hard auto-stop.
enum class SilenceLevel {
    // 已录 >1s 且 >1s 低音量。仅作提示。
    // > 1s elapsed and > 1s of low volume. Hint only.
    WARNING,

    // 已录 >5s 且 >2.5s 低音量。引擎会自动停止。
    // > 5s elapsed and > 2.5s of low volume. The engine will auto-stop.
    AUTO_STOP,
}
