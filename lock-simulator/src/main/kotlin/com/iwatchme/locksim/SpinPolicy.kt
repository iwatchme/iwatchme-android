package com.iwatchme.locksim

import java.util.concurrent.ConcurrentHashMap

/**
 * 自适应自旋: 每个对象单独维护"建议自旋次数"。
 * 上一次自旋成功 → 加 5 (奖励,下次允许更久);
 * 上一次失败到要膨胀 → 减 5 (惩罚,下次别等了直接挂)。
 * 这是 HotSpot JDK 6+ 的策略,对应文章里"次数不是固定值"。
 */
class SpinPolicy(
    private val minSpins: Int = 5,
    private val maxSpins: Int = 50,
) {

    private val perObjectLimit = ConcurrentHashMap<Long, Int>()

    fun limit(obj: SimObject): Int =
        perObjectLimit.getOrDefault(obj.id, minSpins)

    fun onSpinSuccess(obj: SimObject) {
        perObjectLimit.compute(obj.id) { _, cur ->
            ((cur ?: minSpins) + 5).coerceAtMost(maxSpins)
        }
    }

    fun onSpinFailure(obj: SimObject) {
        perObjectLimit.compute(obj.id) { _, cur ->
            ((cur ?: minSpins) - 5).coerceAtLeast(minSpins)
        }
    }
}
