package com.iwatchme.voiceeval.api

/**
 * 单轮评测的最终结果。
 *
 * `localPath` 始终指向本机录音文件；
 * `uploadedUrl` 只有在配置了 [com.iwatchme.voiceeval.upload.AudioUploader]
 * 且上传成功的情况下才会有值，否则保持 null，UI 应回退到本地播放。
 *
 * Final outcome of one evaluation round.
 *
 * `localPath` always points to the on-device audio file. `uploadedUrl` is
 * populated only when an [com.iwatchme.voiceeval.upload.AudioUploader]
 * is configured AND the upload succeeded — otherwise it stays null and the
 * UI should fall back to local playback.
 */
data class EvalResult(
    // 原始请求，便于结果与请求一一关联。
    // Echo of the original request so results stay correlated with their inputs.
    val request: EvalRequest,
    // 总分（0–100）。
    // Overall score on a 0–100 scale.
    val overallScore: Int,
    // 逐词得分。当模式为 WORD 时通常只有一项。
    // Per-word breakdown; typically a single entry when mode is WORD.
    val words: List<WordScore>,
    // 实际录音时长（毫秒）。
    // Wall-clock recording duration in milliseconds.
    val durationMs: Long,
    // 本地录音文件绝对路径，永远可用。
    // Absolute path of the on-device recording; always populated.
    val localPath: String,
    // 上传后的 URL；仅在配置了上传器且上传成功时有值。
    // Public URL after upload; populated only when an uploader is set and the upload succeeded.
    val uploadedUrl: String? = null,
    // 分数来源（真实打分 / 默认兜底 / 超时兜底）。
    // Source of the score (real scorer, default fallback, or timeout fallback).
    val source: ResultSource = ResultSource.SCORER,
)

// 单个词的得分。
// Per-word score entry.
data class WordScore(
    val word: String,
    val score: Int,
)

/**
 * 告诉宿主 App：这个分数到底是真实后端返回的，还是兜底（超时 / 异常 / 网络故障）算出来的。
 * 用途：埋点分析、决定 UI 展示「请重试」还是直接展示分数。
 *
 * Tells the host app whether the score actually came from the scorer or
 * from a fallback path (timeout, network error, etc.). Useful for analytics
 * and for deciding whether to show "请重试" vs the score directly.
 */
enum class ResultSource {
    // 真实打分器返回。
    // Real scorer returned a value.
    SCORER,

    // 打分器抛错，使用默认兜底分数。
    // Scorer threw; default-score fallback was used.
    DEFAULT_FALLBACK,

    // 打分器在限定时间内未返回，使用超时兜底分数。
    // Scorer did not return within the timeout; timeout fallback was used.
    TIMEOUT_FALLBACK,
}
