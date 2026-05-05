package com.iwatchme.voiceeval.recorder

import com.iwatchme.voiceeval.api.SilenceLevel

/**
 * 复刻原生产代码里的「长时间静默自动停止」启发式：
 *  - 录音 >1s 且 已静默 >1s ⇒ 发出 WARNING（仅提示，不停止）。
 *  - 录音 >[autoStopAfterMs] 且 连续静默 >[quietForMs] ⇒ 发出 AUTO_STOP。
 *
 * 类内部维护状态 —— 每收到一个 dB 采样就调用一次 [observe]，按返回信号决定后续动作。
 * 录音生命周期由调用方掌控，本类自身从不主动停止任何东西。
 *
 * Replicates the "auto stop on prolonged silence" heuristic from the original
 * production code: emit WARNING after 1s overall + 1s of quiet, and AUTO_STOP
 * once the user has had > [autoStopAfterMs] of total recording AND > [quietForMs]
 * of consecutive sub-threshold audio.
 *
 * Stateful — call [observe] for every dB sample and act on the returned signal.
 * Caller owns the recording lifecycle; this class never stops anything itself.
 */
internal class SilenceDetector(
    // 静默判定阈值（dB），≤ 此值视为静默。
    // Threshold in dB; samples ≤ this are treated as silence.
    private val thresholdDb: Int,
    // 触发 AUTO_STOP 所需的连续静默时长（毫秒）。
    // Continuous silence (ms) required to trigger AUTO_STOP.
    private val quietForMs: Long,
    // 录音满多久后才允许触发 AUTO_STOP（毫秒）。
    // Minimum total recording time (ms) before AUTO_STOP can fire.
    private val autoStopAfterMs: Long,
) {

    // 录音开始时间戳；首个 observe() 时填入。
    // Timestamp when recording started; set on first observe() call.
    private var startedAtMs: Long = -1
    // 当前静默段的起始时间；非静默时被重置为 -1。
    // Start of the current silent run; reset to -1 whenever audio rises above threshold.
    private var quietSinceMs: Long = -1
    // 是否已经发过 WARNING；避免重复打扰 UI。
    // Whether a WARNING has already been emitted; prevents duplicate hints to the UI.
    private var warningEmitted = false

    fun reset() {
        startedAtMs = -1
        quietSinceMs = -1
        warningEmitted = false
    }

    fun observe(currentDb: Int, nowMs: Long): SilenceLevel? {
        if (startedAtMs == -1L) startedAtMs = nowMs

        if (currentDb <= thresholdDb) {
            if (quietSinceMs == -1L) quietSinceMs = nowMs
        } else {
            // 一旦有声响立即清空静默计时。
            // Any audio above threshold resets the silence timer.
            quietSinceMs = -1L
        }

        val elapsed = nowMs - startedAtMs
        val quietFor = if (quietSinceMs == -1L) 0 else nowMs - quietSinceMs

        // 软提示：录音 >1s 且静默 >1s。仅触发一次。
        // Soft hint: recorded >1s and silent >1s. Fires at most once.
        if (!warningEmitted && elapsed > 1_000 && quietFor > 1_000) {
            warningEmitted = true
            return SilenceLevel.WARNING
        }
        // 强信号：录音足够久且静默足够久，让引擎自动停止。
        // Hard signal: enough total recording AND enough silence — engine should auto-stop.
        if (elapsed > autoStopAfterMs && quietFor > quietForMs) {
            return SilenceLevel.AUTO_STOP
        }
        return null
    }
}
