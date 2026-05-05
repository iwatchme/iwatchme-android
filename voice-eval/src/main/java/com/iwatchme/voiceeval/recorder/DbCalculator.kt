package com.iwatchme.voiceeval.recorder

import kotlin.math.abs
import kotlin.math.log10

/**
 * 针对 16-bit PCM 的轻量级 RMS 分贝表。返回值为非负整数，数值越大越响。
 *
 * 这里得到的并非绝对声压级（SPL），而是手机端音量条常用的近似公式
 * `20 × log10(avg(|sample|))`。
 * 对于静音 / 空缓冲区返回 0，避免出现 -Infinity。
 *
 * Lightweight RMS-style decibel meter for 16-bit PCM. Returns a non-negative
 * integer where higher = louder. The exact mapping is not absolute SPL —
 * it's the common "20 * log10(avg(|sample|))" trick used by mobile VU meters.
 *
 * Returns 0 for a silent / empty buffer instead of -Infinity.
 */
internal object DbCalculator {

    fun compute(samples: ShortArray, length: Int): Int {
        if (length <= 0) return 0
        var sum = 0.0
        for (i in 0 until length) {
            sum += abs(samples[i].toInt())
        }
        val avg = sum / length
        // 平均幅度过小直接视作静音，跳过 log10 以免溢出为 -Infinity。
        // Treat tiny averages as silence; skip log10 to avoid -Infinity.
        if (avg < 1.0) return 0
        return (20.0 * log10(avg)).toInt()
    }
}
