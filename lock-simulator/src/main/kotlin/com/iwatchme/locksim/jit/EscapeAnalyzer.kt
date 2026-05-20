package com.iwatchme.locksim.jit

/**
 * 极简版逃逸分析 (Escape Analysis)。
 *
 * 一、它要回答一个问题:这个对象,有没有可能被别的线程拿到?
 * 锁存在的唯一作用是防止多个线程同时改一个对象。要发生这件事,前提是别的线程能拿到这个对象的
 * 引用 (地址)。如果一个对象的引用从生到死都没离开过当前方法的栈帧,那别的线程根本不知道它在哪、
 * 也拿不到地址,就没人能跟你争 —— 这把锁就是死代码,后续锁消除 pass 可以整对删掉 lock/unlock。
 *
 * 二、引用怎么"流出"栈帧?穷举只有三个出口
 *   - Op.Return tag       —— 对象被 return,调用方拿到了,可能再传给任意线程
 *   - Op.WriteField       —— 对象被写进堆上某个字段,顺着引用图任何线程都摸得到
 *   - Op.HandOff          —— 对象被显式交给别的线程 (放进队列、共享变量等)
 * 只要这三种 op 都没在方法体里出现过该对象,就能断言它的引用始终停留在当前栈帧,即"未逃逸"。
 * 其余 op (lock/unlock/work/newObj 等) 都不会让引用流出去,与逃逸判断无关。
 *
 * 三、扫描就是把出口都找一遍
 * `analyze` 顺序扫一遍 method.ops,凡是踩到上面三个出口之一,就把对应 tag 加进 escaped 集合。
 * 扫完返回的集合 = "这个方法里所有已经逃逸的对象"。下游 pass 拿着这个集合做查表:
 *   - lockElision:  tag 不在集合 → 这把锁可以删
 *   - lockCoarsening: tag 在集合 → 锁不能删,但如果中间没再次出现逃逸 op,可以合并相邻临界区
 *
 * 四、判断是保守的
 * 静态分析看不出对象一定没逃逸,就当它逃了 —— 宁可少优化,不能少加锁。漏判一次"逃逸"的代价
 * 是并发出错,代价远大于多保留几把锁的开销。
 *
 * 五、简化处 (对照真实 HotSpot)
 * 真实 HotSpot 用 Connection Graph 做指针流分析,分三档 NoEscape / ArgEscape / GlobalEscape,
 * 且会先内联再分析、做控制流敏感、字段敏感。这里只做最粗的二分,够演示原理用。
 */
class EscapeAnalyzer {

    fun analyze(method: Method): Set<String> {
        val escaped = mutableSetOf<String>()
        for (op in method.ops) {
            when (op) {
                is Op.Return -> op.tag?.let { escaped += it }
                is Op.WriteField -> escaped += op.value
                is Op.HandOff -> escaped += op.tag
                else -> Unit
            }
        }
        return escaped
    }
}
