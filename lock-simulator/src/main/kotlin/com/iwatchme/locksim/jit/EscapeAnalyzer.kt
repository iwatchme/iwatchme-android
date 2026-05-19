package com.iwatchme.locksim.jit

/**
 * 极简版逃逸分析。HotSpot 真做这件事要走 Connection Graph,我们这里只识别三种"明确逃逸":
 *   - return obj       —— 被方法外拿到
 *   - obj 写入字段     —— 别的对象/线程可达
 *   - obj 交给别的线程 —— 显然逃了
 * 没逃出去的对象,后续锁消除 pass 会把它身上的 lock/unlock 整对删掉。
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
