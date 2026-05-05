package com.iwatchme.voiceeval.scoring

import com.iwatchme.voiceeval.api.EvalRequest
import com.iwatchme.voiceeval.api.WordScore
import kotlinx.coroutines.flow.Flow

/**
 * 真实「发音打分后端」的策略接口。
 *
 * 形态是流式的：引擎把 [EvalRequest] 加上一条音频 chunk 的冷 Flow 交给打分器，
 * 打分器需要：
 *  - 把所有 chunk 消费完（每个 chunk 大约就是一帧网络包，比如 ~4KB MP3 或 PCM）；
 *  - 在完成时返回逐词得分 + 总分。
 *
 * 不同实现可热插拔：生产环境会把帧推到腾讯 SOE 的 HTTPS 接口；
 * 而内置的 [MockVoiceScorer] 只是合成一个像模像样的分数，让整个引擎在没有
 * 网络和后端凭证的情况下也能端到端跑起来（演示 / 单测都很方便）。
 *
 * Strategy for the actual pronunciation-scoring back-end.
 *
 * Streaming-shaped: the engine hands the scorer the [EvalRequest] plus a
 * cold flow of audio chunks. The scorer is expected to:
 *
 *  - drain the chunks (each is one network frame, e.g. ~4KB MP3 or PCM),
 *  - return a per-word breakdown plus an overall score on completion.
 *
 * Implementations are interchangeable: the production version would push
 * frames into Tencent SOE over HTTPS; the bundled [MockVoiceScorer] just
 * fabricates plausible numbers so the engine can be exercised end-to-end
 * without a network or credentials.
 */
interface VoiceScorer {

    /**
     * 一直挂起，直到：
     *  - 上游 chunk Flow 结束（视作用户说完了）；并且
     *  - 后端返回最终得分。
     *
     * 如果后端拒绝本次请求，应抛出异常 —— 引擎会把它包装为
     * [com.iwatchme.voiceeval.api.EvalError.ScorerFailure]，并走默认分数兜底路径。
     *
     * Suspend until either:
     *  - the upstream chunk flow completes (treat as user finished speaking), and
     *  - the scoring back-end returns its verdict.
     *
     * Should throw if the back-end rejects the session — the engine wraps
     * that into an [com.iwatchme.voiceeval.api.EvalError.ScorerFailure] and
     * applies the default-score fallback path.
     */
    suspend fun score(
        request: EvalRequest,
        chunks: Flow<AudioChunk>,
    ): ScoringOutcome
}

/**
 * 引擎切出的一个传输尺寸的音频片段（默认 4KB）。
 * `index` 从 0 开始单调递增；`isEnd` 在每次会话中**恰好**有一个 chunk 为 true
 * （即最后一个），形态对齐腾讯 SOE 的网络协议。
 *
 * One transmit-sized fragment as the engine slices it (default 4KB).
 * `index` is monotonically increasing from 0; `isEnd` is true on exactly
 * one chunk per session — the last one — to mirror the SOE wire protocol.
 */
data class AudioChunk(
    // 序号；0 起步，单调递增。
    // Sequence number; starts at 0 and increases monotonically.
    val index: Int,
    // 是否为本次会话的最后一帧；用作流终止信号。
    // Whether this is the final frame of the session; used as the stream-end signal.
    val isEnd: Boolean,
    // 编码后的字节负载（PCM / MP3，取决于编码器）。
    // Encoded payload bytes (PCM or MP3, depending on the encoder).
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is AudioChunk &&
                index == other.index &&
                isEnd == other.isEnd &&
                bytes.contentEquals(other.bytes)

    override fun hashCode(): Int =
        (index * 31 + isEnd.hashCode()) * 31 + bytes.contentHashCode()
}

// 打分器返回的内部数据结构：总分 + 逐词得分；引擎再套上 EvalResult。
// Internal scoring payload: overall + per-word scores; the engine wraps it into EvalResult.
data class ScoringOutcome(
    val overallScore: Int,
    val words: List<WordScore>,
)
