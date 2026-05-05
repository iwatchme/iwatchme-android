package com.iwatchme.voiceeval.internal

import com.iwatchme.voiceeval.api.EvalRequest
import com.iwatchme.voiceeval.api.WordScore
import com.iwatchme.voiceeval.scoring.ScoringOutcome
import kotlin.random.Random

/**
 * 当真实打分器失败或超时时使用的兜底分数生成器。
 *
 * 复刻线上策略：返回一个「客气」的 60–70 分，避免后端瞬时抖动把用户卡在课程里走不下去。
 *
 * 这里特地保留随机性 —— 如果每次都固定 65，用户会很快察觉这是兜底分数并加以利用。
 *
 * Fallback used when the real scorer fails or times out. Mirrors the
 * production behavior of returning a "polite" 60-70 score so the user
 * isn't blocked from progressing through a lesson by a transient backend
 * outage.
 *
 * The randomness is intentional — a flat 65 every time would let users
 * notice the failure mode and game it.
 */
internal object DefaultScoreFactory {

    fun build(request: EvalRequest): ScoringOutcome {
        val rng = Random.Default
        val score = 60 + rng.nextInt(11)
        val words = request.refText
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .map { WordScore(it, score) }
        return ScoringOutcome(overallScore = score, words = words)
    }
}
