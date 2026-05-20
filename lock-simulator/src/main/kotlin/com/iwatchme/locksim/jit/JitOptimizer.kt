package com.iwatchme.locksim.jit

data class OptimizeReport(
    val elided: List<String>,
    val coarsened: Map<String, Int>,
    val optimized: Method,
)

/**
 * 模拟 JIT 的两个锁优化 pass。
 *
 * 一、整体定位
 * JVM 的锁本身已经做了运行时优化 (偏向 → 轻量 → 重量 三级状态机),但这些都是"锁已经走到了
 * 加锁那一步"再去省。JIT 这一层做的是更狠的事 —— 在编译期就把多余的加锁动作**整段抹掉或合并**,
 * 让运行时压根碰不到那行 lock 指令。两个 pass 解决两类不同的浪费:
 *   1) lockElision   (锁消除) —— 解决"这把锁根本没必要存在"
 *   2) lockCoarsening (锁粗化) —— 解决"锁是必要的,但加得太碎"
 *
 * 二、为什么必须先消除再粗化
 * 假设代码里有 3 对连续的 lock/unlock,对象又没逃逸:
 *   - 顺序对:消除 pass 直接把 6 条指令全删 → 0 次加锁,完美。
 *   - 顺序反:粗化 pass 先把 3 对合成 1 对,得到 1 个 lock + 1 个 unlock;接着消除 pass 再
 *            发现对象没逃逸,把这 1 对也删了 → 结果一样,但白做了一次合并工作,且粗化中间
 *            可能引入跨基本块的合并,让后续判断更难做。
 * 所以"先全删,再合并剩下的"是正确顺序。
 */
class JitOptimizer(private val analyzer: EscapeAnalyzer = EscapeAnalyzer()) {

    fun optimize(method: Method): OptimizeReport {
        val escaped = analyzer.analyze(method)
        val (afterElision, elided) = lockElision(method, escaped)
        val (afterCoarsening, coarsened) = lockCoarsening(afterElision)
        return OptimizeReport(elided, coarsened, afterCoarsening)
    }

    /**
     * 锁消除 (Lock Elision)。
     *
     * 一、为什么能删
     * 锁的全部作用是防止多个线程同时改一个对象。要发生这件事,前提是别的线程能拿到这个对象的
     * 引用。如果逃逸分析已经证明这个对象的引用没离开过当前栈帧 (tag 不在 escaped 集合里),那
     * 物理上就不存在"别的线程"能跟你争 —— lock/unlock 在防一件根本不会发生的事,是死代码。
     * 整对删掉,语义不变,运行时省下 CAS、内存屏障、可能的锁膨胀路径。
     *
     * 二、怎么判断
     * 判断分两步,这里是第二步;第一步在 `EscapeAnalyzer` 里已经做完了,产出一个 escaped 集合
     * (= 方法里所有已经逃逸的 tag)。这一步只做一件事:
     *   - 扫指令流,看到 Op.Lock(tag) / Op.Unlock(tag),去集合里查 tag;
     *   - tag 不在集合 → 这条指令删掉;tag 在集合 → 保留。
     * 就这么简单,本质是一次集合查询。判断粒度是"按 tag",不需要先把 Lock/Unlock 配对找出来:
     * 同一个 tag 要么全删要么全留,配对关系自动保持,不会出现野 Unlock。
     *
     * 三、经典场景
     * 方法内 `new StringBuffer()` 拼字符串。StringBuffer.append 是 synchronized 的,但只要这个
     * sb 没被 return / 没写进字段 / 没传给别的线程,扫描出的 escaped 集合里就没有它,所有 append
     * 上的锁全部消除,等价于直接用 StringBuilder。
     *
     * 四、判断是保守的
     * 如果逃逸分析拿不准,就把对象算进 escaped,锁原样保留。漏判一次逃逸的代价是并发出错,
     * 远比多保留几把锁严重 —— 所以宁可少优化,不能少加锁。
     */
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
     * 锁粗化 (Lock Coarsening)。
     *
     * 一、它要解决什么
     * 当一段代码里**对同一把锁反复 lock/unlock** (例如循环里调用一系列 synchronized 方法,
     * 或者连续调 StringBuffer.append),每次 lock/unlock 本身都有固定开销 —— 即使没人争用,
     * 偏向锁/轻量锁路径也得跑 CAS。把这些紧挨着的小临界区合并成一个大临界区,等价指令更少。
     *
     *   合并前:lock; A; unlock; lock; B; unlock; lock; C; unlock;
     *   合并后:lock; A; B; C; unlock;
     *
     * 二、为什么这样合并不会改变语义
     * 关键观察:两个相邻的 unlock(x) 和 lock(x) 之间,如果别的线程**根本拿不到 x**,那这段
     * "释放窗口"对外不可见 —— 合不合并,外部观察者都看不出区别。判定"外部拿不到 x"等价于:
     * 这中间没有任何"让 x 逃逸出去"的事件 (HandOff / WriteField)。
     *
     * 三、反例:什么时候**绝对不能**合并
     *   lock(L); ...; unlock(L);
     *   sharedQueue.put(L);     // ← 把 L 交出去
     *   lock(L); ...; unlock(L);
     * 中间这次 unlock 是有意义的释放窗口:让接手 L 的线程能拿到锁。如果粗化把这段合掉,
     * L 一直被本线程持有,对方死等,直接死锁/卡死。所以 `noEscapingOpsBetween` 是硬否决条件,
     * 不是优化建议。
     *
     * 四、实现要点
     *   - 扫描:遇到 Op.Unlock(x) 就往后找最近的 Op.Lock(x);
     *   - 安全:中间不能有 HandOff(x) 或 WriteField(value = x),否则放弃;
     *   - 边界:途中再次出现 Op.Unlock(x) 视为已经被合并过/锁状态异常,findNextLock 返回 null;
     *   - 迭代:删完一对后相邻关系会变,新产生的相邻对可能继续可合并,所以外层 while (changed)
     *           跑到不动点,N 对连续锁最终都被压成 1 对。
     *
     * 五、与锁消除的边界
     * 粗化只对"必须保留的锁"做合并;能整对删的早在 lockElision 里删干净了。所以测试用例里
     * 想观察粗化效果,必须故意 handOff 让对象逃逸,绕开消除 pass。
     *
     * 六、与真实 HotSpot 的差异
     * 这里为了演示清晰,不限制合并范围,反复迭代直到收敛。真实编译器会更保守:粗化一般只在
     * 同一基本块或有限指令窗口内做,不会跨循环、跨方法调用合并 —— 防止临界区被拉得过长反而
     * 拖垮并发吞吐。
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
