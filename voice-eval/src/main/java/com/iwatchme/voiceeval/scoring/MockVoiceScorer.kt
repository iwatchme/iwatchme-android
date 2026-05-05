package com.iwatchme.voiceeval.scoring

import android.util.Base64
import com.iwatchme.voiceeval.api.EvalMode
import com.iwatchme.voiceeval.api.EvalRequest
import com.iwatchme.voiceeval.api.WordScore
import com.iwatchme.voiceeval.scoring.soe.MockSoeService
import com.iwatchme.voiceeval.scoring.soe.SoeEvalMode
import com.iwatchme.voiceeval.scoring.soe.SoeInitParams
import com.iwatchme.voiceeval.scoring.soe.SoeResponse
import com.iwatchme.voiceeval.scoring.soe.SoeService
import com.iwatchme.voiceeval.scoring.soe.SoeStatus
import com.iwatchme.voiceeval.scoring.soe.SoeTransmitRequest
import com.iwatchme.voiceeval.scoring.soe.SoeVoiceFileType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * 腾讯云智聆口语评测（SOE）后端的「协议级仿真」实现。
 *
 * 它**不是**简单地把 chunk 收集起来再合成分数 —— 而是严格按真实 SOE 协议去驱动：
 *  - 每片调用 [SoeService.transmit]，按真实 SeqId / IsEnd / Base64(UserVoiceData) /
 *    VoiceFileType 组装请求。
 *  - 首片附带 [SoeInitParams]（refText / evalMode），与生产
 *    `TransmitOralProcessWithInit` 路径一致。
 *  - 末片返回 [SoeStatus.Evaluating] 时按文档要求轮询 `IsQuery=1` 直到
 *    [SoeStatus.Finished]。
 *  - 把 SOE 的 Words / PronAccuracy / SuggestedScore 映射回 [ScoringOutcome]。
 *
 * 这样未来切到真实 HTTPS 时**只需替换** [SoeService] 这一个实例 ——
 * 调用方代码、demo 代码无需任何改动。
 *
 * 默认注入的服务端是 [MockSoeService]，纯本地仿真、不发任何网络请求。
 *
 * Protocol-shaped simulation of the Tencent Cloud SOE backend.
 *
 * Rather than just draining chunks and synthesizing a score, this implementation
 * drives the real SOE wire protocol step-by-step:
 *  - One [SoeService.transmit] call per chunk with real SeqId / IsEnd /
 *    Base64(UserVoiceData) / VoiceFileType fields.
 *  - First chunk carries [SoeInitParams] (matches the production
 *    `TransmitOralProcessWithInit` path).
 *  - When the final response comes back as [SoeStatus.Evaluating], polls with
 *    `IsQuery=1` until [SoeStatus.Finished], exactly as the docs prescribe.
 *  - Maps the resulting Words / PronAccuracy / SuggestedScore back into [ScoringOutcome].
 *
 * To switch to real HTTPS, replace just the [SoeService] instance — caller and
 * demo code stay unchanged. The default injected service is [MockSoeService],
 * an in-process simulation that issues no real network calls.
 */
class MockVoiceScorer(
    seed: Long? = null,
    networkLatencyMs: Long = 400,
    floor: Int = 65,
    /**
     * 录音封装格式，决定 SOE 的 VoiceFileType 字段（PCM/WAV/MP3/Speex）。默认 PCM。
     * Wire format of the encoded chunks; controls SOE VoiceFileType. Defaults to PCM.
     */
    private val voiceFileType: Int = SoeVoiceFileType.PCM,
    /**
     * SOE 服务端实现。生产环境换成 HTTPS 客户端即可，调用方代码无需改动。
     * SOE service binding. Swap with an HTTPS client in production; caller code stays unchanged.
     */
    private val service: SoeService = MockSoeService(
        seed = seed,
        perRequestLatencyMs = 30,
        serverProcessingMs = networkLatencyMs.coerceAtLeast(50),
        floor = floor,
    ),
    /**
     * 末片后轮询 IsQuery=1 的间隔（毫秒）。
     * Polling interval (ms) for IsQuery=1 after the final chunk.
     */
    private val pollIntervalMs: Long = 100,
) : VoiceScorer {

    override suspend fun score(
        request: EvalRequest,
        chunks: Flow<AudioChunk>,
    ): ScoringOutcome {
        // 每轮评测对应一个全新 SessionId（UUID）—— 与生产保持一致。
        // Fresh UUID per evaluation, mirroring the production protocol.
        val sessionId = UUID.randomUUID().toString()
        var lastResp: SoeResponse? = null
        var lastSeq = 0

        // 流式上传：每片对应一次 transmit。SeqId 必须从 1 起步（SOE 协议硬约束）。
        // Streaming upload: one transmit per chunk. SeqId starts at 1
        // (a hard SOE wire-protocol invariant).
        chunks.collect { chunk ->
            val seq = chunk.index + 1
            check(seq <= MAX_SEQ_ID) {
                "SOE: 单 Session 不能超过 $MAX_SEQ_ID 包 / chunk count exceeded $MAX_SEQ_ID"
            }
            val req = SoeTransmitRequest(
                sessionId = sessionId,
                seqId = seq,
                isEnd = if (chunk.isEnd) 1 else 0,
                voiceFileType = voiceFileType,
                voiceEncodeType = SOE_VOICE_ENCODE_PCM,
                userVoiceData = Base64.encodeToString(chunk.bytes, Base64.NO_WRAP),
                // 仅首片附带 InitParams —— 对应 TransmitOralProcessWithInit。
                // Init params ride only on the first chunk — TransmitOralProcessWithInit.
                initParams = if (seq == 1) request.toInitParams() else null,
            )
            lastResp = service.transmit(req)
            lastSeq = seq
        }

        // 上游 Flow 没有发任何 chunk —— 直接给 0 分兜底。
        // Upstream flow emitted zero chunks — return a 0 outcome instead of throwing.
        val initial = lastResp ?: return ScoringOutcome(0, emptyList())

        // 末片之后服务端可能仍处于 Evaluating；按文档要求轮询 IsQuery=1。
        // Final response may still be Evaluating; poll IsQuery=1 per the SOE docs.
        var resp = initial
        var attempts = 0
        while (resp.status == SoeStatus.Evaluating && attempts < MAX_QUERY_ATTEMPTS) {
            delay(pollIntervalMs)
            attempts++
            resp = service.transmit(
                SoeTransmitRequest(
                    sessionId = sessionId,
                    // IsEnd=1 之后 SeqId 对服务端无意义；这里复用最后一片的序号。
                    // SeqId is meaningless after IsEnd=1; reuse the last sent value.
                    seqId = lastSeq,
                    isEnd = 1,
                    voiceFileType = voiceFileType,
                    voiceEncodeType = SOE_VOICE_ENCODE_PCM,
                    userVoiceData = "",
                    isQuery = 1,
                ),
            )
        }
        check(resp.status == SoeStatus.Finished) {
            "SOE: Session 未完成 / session did not finish: status=${resp.status}"
        }
        return resp.toScoringOutcome()
    }

    private fun EvalRequest.toInitParams(): SoeInitParams = SoeInitParams(
        refText = refText,
        evalMode = mode.toSoeEvalMode(),
    )

    private fun EvalMode.toSoeEvalMode(): Int = when (this) {
        EvalMode.WORD -> SoeEvalMode.WORD
        EvalMode.SENTENCE -> SoeEvalMode.SENTENCE
        EvalMode.PARAGRAPH -> SoeEvalMode.PARAGRAPH
    }

    private fun SoeResponse.toScoringOutcome(): ScoringOutcome = ScoringOutcome(
        // SOE 的 SuggestedScore 是 0–100 浮点；我们对外用整型。
        // SOE's SuggestedScore is a 0–100 float; we expose an Int.
        overallScore = suggestedScore.toInt().coerceIn(0, 100),
        words = words.map {
            WordScore(it.word, it.pronAccuracy.toInt().coerceIn(0, 100))
        },
    )

    private companion object {
        // SOE: VoiceEncodeType 文档目前只列了 PCM。
        // SOE: VoiceEncodeType is documented as PCM only.
        const val SOE_VOICE_ENCODE_PCM = 1

        // SOE 协议硬上限：单 Session 至多 3000 包。
        // SOE wire-protocol cap: ≤ 3000 packets per session.
        const val MAX_SEQ_ID = 3000

        // 轮询次数上限（约 5s @ 100ms 间隔），避免无限等待。
        // Cap on polling attempts (~5s at 100ms interval); guards against infinite waits.
        const val MAX_QUERY_ATTEMPTS = 50
    }
}
