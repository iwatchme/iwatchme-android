package com.iwatchme.voiceeval.scoring.soe

import android.util.Base64
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * 同进程内的 SOE 服务端仿真。
 *
 * 它不发任何真实 HTTP 请求，但会**严格按 SOE 协议**接收分片、推进状态机：
 *  - 校验首片携带 InitParams、SeqId 从 1 起步、连续递增、上限 3000；
 *  - 中间片永远返回 [SoeStatus.Evaluating]（数值字段为 SOE 文档约定的占位值）；
 *  - 收到 IsEnd=1 后进入「评估中」期，按 [serverProcessingMs] 模拟服务端
 *    最终对齐 + 打分耗时；这段期间 IsQuery=1 仍然返回 Evaluating；
 *  - 期满后首次查询合成最终结果（带 PronAccuracy / PronFluency / PronCompletion /
 *    SuggestedScore / Words+PhoneInfos 完整字段），并在会话内幂等缓存。
 *
 * 评分本身仍然是「依据 refText 合成」的玩具实现 —— 这是 mock 的本质。
 * 真正的价值在于：调用方代码（[com.iwatchme.voiceeval.scoring.MockVoiceScorer]）
 * 已经在按真实协议驱动，未来切到生产只需把 [SoeService] 实现换掉即可。
 *
 * In-process simulation of the SOE server side.
 *
 * No real HTTP request is ever issued, but the protocol state machine is honored
 * to the letter:
 *  - first chunk must carry InitParams; SeqId must start at 1, increase
 *    monotonically, and stay ≤ 3000;
 *  - intermediate chunks always return [SoeStatus.Evaluating] (numeric fields
 *    populated with SOE-documented sentinel values);
 *  - IsEnd=1 enters the "evaluating" phase whose duration is simulated by
 *    [serverProcessingMs]; IsQuery=1 during this window still returns Evaluating;
 *  - after the window expires, the first query synthesizes the final response
 *    (full PronAccuracy / PronFluency / PronCompletion / SuggestedScore /
 *    Words+PhoneInfos fields) and caches it idempotently for the session.
 *
 * The score itself is still a refText-based toy — that's what "mock" means.
 * The point is that the caller is already driving the protocol exactly as
 * production would; swapping in a real client is just a [SoeService] substitution.
 */
class MockSoeService(
    // 用于复现的 RNG 种子；非 null 时同 refText 会得到稳定分数。
    // RNG seed for reproducibility; with a non-null seed, the same refText
    // always yields the same score.
    private val seed: Long? = null,
    // 单次「请求」的模拟延迟（毫秒），代表网络往返。
    // Per-request simulated network round-trip latency in ms.
    private val perRequestLatencyMs: Long = 30,
    // IsEnd=1 后服务端「评估中」的耗时；模拟最终对齐 + 打分。
    // Server-side processing time after IsEnd=1; simulates final alignment + scoring.
    private val serverProcessingMs: Long = 350,
    // 单词得分基线下限（合成分数时使用）。
    // Floor for synthesized per-word accuracy.
    private val floor: Int = 65,
) : SoeService {

    private class SessionState(
        val refText: String,
        val evalMode: Int,
        val createdAtMs: Long,
    ) {
        // 已经收到的最大 SeqId；用于校验单调连续。
        // Largest SeqId received so far; enforces monotonic, contiguous numbering.
        var lastSeq: Int = 0

        // 已接收的有效音频字节数（base64 解码后）。
        // Total decoded audio bytes ingested for this session.
        var totalDecodedBytes: Long = 0

        // 收到 IsEnd=1 的时刻，-1 表示尚未收到。
        // Timestamp of the IsEnd=1 frame; -1 means not yet received.
        var endReceivedAtMs: Long = -1

        // 终态响应的幂等缓存。
        // Idempotent cache of the terminal response.
        var cachedFinal: SoeResponse? = null

        // 同一 Session 内的请求需串行；避免轮询和最后一片并发改状态。
        // Requests within a session must be serialized; avoids races between
        // the final transmit and the polling queries.
        val mutex = Mutex()
    }

    // 多 Session 并发安全；每个 session 内部再用 mutex 串行化 transmit。
    // Thread-safe across sessions; per-session mutex serializes its transmit calls.
    private val sessions = HashMap<String, SessionState>()
    private val sessionsLock = Any()

    override suspend fun transmit(request: SoeTransmitRequest): SoeResponse {
        delay(perRequestLatencyMs)

        val state = obtainSession(request)
        return state.mutex.withLock { handle(request, state) }
    }

    private fun obtainSession(request: SoeTransmitRequest): SessionState {
        // 首片（非查询）：必须带 InitParams，并新建会话状态。
        // First non-query chunk: must include InitParams and creates the session.
        if (request.seqId == 1 && request.isQuery == 0) {
            val init = requireNotNull(request.initParams) {
                "SOE: 首片必须附带 InitParams / first chunk must include InitParams"
            }
            synchronized(sessionsLock) {
                sessions[request.sessionId]?.let { return it }
                val fresh = SessionState(
                    refText = init.refText,
                    evalMode = init.evalMode,
                    createdAtMs = System.currentTimeMillis(),
                )
                sessions[request.sessionId] = fresh
                return fresh
            }
        }
        // 后续片：必须能找到已存在的会话。
        // Later chunks: session must already exist.
        synchronized(sessionsLock) {
            return requireNotNull(sessions[request.sessionId]) {
                "SOE: 未知 SessionId / unknown SessionId: ${request.sessionId}"
            }
        }
    }

    private fun handle(req: SoeTransmitRequest, state: SessionState): SoeResponse {
        // 纯查询：根据当前会话状态决定返回 Evaluating 还是 Finished。
        // Query-only: pick Evaluating or the cached Finished response based on state.
        if (req.isQuery == 1) {
            return queryResult(req.sessionId, state)
        }

        // 协议级校验：SeqId 单调连续、上限 3000、IsEnd 取值合法。
        // Wire-protocol checks: monotonic contiguous SeqId, ≤ 3000, IsEnd in {0,1}.
        require(req.seqId in 1..MAX_SEQ_ID) {
            "SOE: SeqId 越界 / SeqId out of range: ${req.seqId}"
        }
        require(req.seqId == state.lastSeq + 1) {
            "SOE: SeqId 不连续 / non-contiguous SeqId: " +
                    "expected ${state.lastSeq + 1}, got ${req.seqId}"
        }
        require(req.isEnd == 0 || req.isEnd == 1) {
            "SOE: IsEnd 非法 / invalid IsEnd: ${req.isEnd}"
        }
        state.lastSeq = req.seqId

        // 解码音频，仅用于统计有效字节数 —— mock 不做真正的语音处理。
        // Decode the audio chunk just to track byte counts; the mock never analyzes signal.
        val decoded = if (req.userVoiceData.isEmpty()) ByteArray(0)
        else Base64.decode(req.userVoiceData, Base64.NO_WRAP)
        state.totalDecodedBytes += decoded.size

        if (req.isEnd == 1) {
            // 标记进入评估期；最终结果在第一次符合时机的查询里合成。
            // Enter the "evaluating" phase; the final score is synthesized lazily.
            state.endReceivedAtMs = System.currentTimeMillis()
            return queryResult(req.sessionId, state)
        }

        // 中间片：SOE 文档明确规定「IsEnd 未置 1 时数值字段无意义」。
        // Intermediate chunk: per SOE docs, numeric fields are meaningless until IsEnd=1.
        return evaluatingShell(req.sessionId)
    }

    private fun queryResult(sessionId: String, state: SessionState): SoeResponse {
        // 还没收到 IsEnd=1：仍在录入。
        // IsEnd=1 not yet received: still ingesting.
        if (state.endReceivedAtMs < 0) {
            return evaluatingShell(sessionId)
        }
        // 模拟服务端「对齐 + 打分」尚未完成。
        // Simulate server still computing alignment + scoring.
        val elapsed = System.currentTimeMillis() - state.endReceivedAtMs
        if (elapsed < serverProcessingMs) {
            return evaluatingShell(sessionId)
        }
        // 命中缓存（幂等）或首次合成。
        // Hit the cache (idempotent) or synthesize on first hit.
        return state.cachedFinal
            ?: synthesize(sessionId, state).also { state.cachedFinal = it }
    }

    private fun synthesize(sessionId: String, state: SessionState): SoeResponse {
        val rng = if (seed == null) Random.Default else Random(seed)

        val tokens = state.refText.split(Regex("\\s+")).filter { it.isNotBlank() }
        val words = tokens.map { word ->
            val lengthPenalty = min(15, max(0, word.length - 4))
            val acc = (rng.nextInt(floor.coerceIn(0, 100), 101) - lengthPenalty)
                .coerceIn(0, 100).toFloat()
            val fluency = (0.6f + rng.nextFloat() * 0.4f).coerceIn(0f, 1f)
            SoeWordResult(
                word = word,
                pronAccuracy = acc,
                pronFluency = fluency,
                memBeginTime = 0,
                memEndTime = 0,
                phoneInfos = synthesizePhones(word, acc, rng),
            )
        }
        val overall = if (words.isEmpty()) 0f
        else words.map { it.pronAccuracy.toDouble() }.average().toFloat()
        val fluency = if (words.isEmpty()) 0f
        else words.map { it.pronFluency.toDouble() }.average().toFloat()
        // 完整度：用「是否有真实音频字节进来」做粗糙代理。
        // Completion: rough proxy — did any real audio bytes come in?
        val completion = if (state.totalDecodedBytes > 0) 1f else 0f
        val suggested = overall * completion * (2f - completion)
        return SoeResponse(
            sessionId = sessionId,
            status = SoeStatus.Finished,
            pronAccuracy = overall,
            pronFluency = fluency,
            pronCompletion = completion,
            suggestedScore = suggested,
            words = words,
        )
    }

    private fun synthesizePhones(
        word: String,
        baseAcc: Float,
        rng: Random,
    ): List<SoePhoneInfo> {
        // 把每个字母粗糙当作一个音素。真实 SOE 会做实际的 G2P + 强制对齐。
        // Treat each letter as a phoneme — real SOE does actual G2P + forced alignment.
        return word.toCharArray().map { ch ->
            SoePhoneInfo(
                phone = ch.toString(),
                pronAccuracy = (baseAcc + rng.nextInt(-10, 11)).coerceIn(0f, 100f),
                memBeginTime = 0,
                memEndTime = 0,
                detectedStress = false,
            )
        }
    }

    // 中间态响应：所有数值字段按 SOE 文档约定填占位值。
    // Intermediate response: numeric fields filled with documented sentinel values.
    private fun evaluatingShell(sessionId: String) = SoeResponse(
        sessionId = sessionId,
        status = SoeStatus.Evaluating,
        pronAccuracy = -1f,
        pronFluency = 0f,
        pronCompletion = 0f,
        suggestedScore = 0f,
        words = emptyList(),
    )

    private companion object {
        // SOE 协议规定单 Session SeqId 上限 3000。
        // SOE wire-protocol limit: SeqId ≤ 3000 per session.
        const val MAX_SEQ_ID = 3000
    }
}
