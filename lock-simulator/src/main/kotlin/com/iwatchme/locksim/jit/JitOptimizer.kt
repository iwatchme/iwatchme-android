package com.iwatchme.locksim.jit

data class OptimizeReport(
    val elided: List<String>,
    val coarsened: Map<String, Int>,
    val optimized: Method,
)

/**
 * 模拟 JIT 的两个锁优化 pass。顺序很重要:
 *   1) 先做锁消除 (依赖逃逸分析的结果) —— 把"根本没必要加锁"的整对都删掉
 *   2) 再做锁粗化 —— 把"虽然要加锁但加得太碎"的相邻几对合成一对
 * 如果顺序反了,可能先合并出大段然后才发现整个对象都不逃逸,白做工。
 */
class JitOptimizer(private val analyzer: EscapeAnalyzer = EscapeAnalyzer()) {

    fun optimize(method: Method): OptimizeReport {
        val escaped = analyzer.analyze(method)
        val (afterElision, elided) = lockElision(method, escaped)
        val (afterCoarsening, coarsened) = lockCoarsening(afterElision)
        return OptimizeReport(elided, coarsened, afterCoarsening)
    }

    private fun lockElision(method: Method, escaped: Set<String>): Pair<Method, List<String>> {
        val elided = mutableListOf<String>()
        val out = method.ops.filter { op ->
            when (op) {
                is Op.Lock -> if (op.tag !in escaped) { elided += op.tag; false } else true
                is Op.Unlock -> if (op.tag !in escaped) { false } else true
                else -> true
            }
        }
        return method.copy(out) to elided
    }

    /**
     * 锁粗化: 模式 `... unlock(x) ... lock(x) ...` 中,如果两条之间没有
     * "把 x 暴露出去"的事件 (HandOff / WriteField),就可以把中间这对 unlock+lock 删掉,
     * 等价于把临界区合并。重复跑直到不再有可合并对,处理 N 对相邻锁的情况。
     */
    private fun lockCoarsening(method: Method): Pair<Method, Map<String, Int>> {
        val ops = method.ops.toMutableList()
        val coarsened = mutableMapOf<String, Int>()
        var changed = true
        while (changed) {
            changed = false
            var i = 0
            while (i < ops.size) {
                val op = ops[i]
                if (op is Op.Unlock) {
                    val tag = op.tag
                    val nextLock = findNextLock(ops, i + 1, tag)
                    if (nextLock != null && noEscapingOpsBetween(ops, i + 1, nextLock, tag)) {
                        ops.removeAt(nextLock)
                        ops.removeAt(i)
                        coarsened.merge(tag, 1) { a, b -> a + b }
                        changed = true
                        continue
                    }
                }
                i++
            }
        }
        return method.copy(ops.toList()) to coarsened
    }

    private fun findNextLock(ops: List<Op>, from: Int, tag: String): Int? {
        for (i in from until ops.size) {
            val op = ops[i]
            if (op is Op.Lock && op.tag == tag) return i
            if (op is Op.Unlock && op.tag == tag) return null
        }
        return null
    }

    private fun noEscapingOpsBetween(ops: List<Op>, start: Int, end: Int, tag: String): Boolean {
        for (i in start until end) {
            val op = ops[i]
            if (op is Op.HandOff && op.tag == tag) return false
            if (op is Op.WriteField && op.value == tag) return false
        }
        return true
    }
}
