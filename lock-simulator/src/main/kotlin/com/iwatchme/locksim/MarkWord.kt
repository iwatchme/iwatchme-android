package com.iwatchme.locksim

import java.util.concurrent.atomic.AtomicLong

/**
 * 锁状态用低 3 位标志位区分,跟 HotSpot 一致:
 *   - NO_LOCK     001  无锁
 *   - BIASED      101  偏向锁  (注意低 2 位仍是 01)
 *   - LIGHTWEIGHT 000  轻量级 (Mark Word 高位 = LockRecord 指针)
 *   - HEAVYWEIGHT 010  重量级 (Mark Word 高位 = ObjectMonitor 指针)
 * 升级 = 改写 Mark Word,所以"加锁"不需要额外内存。
 */
enum class LockState(val tag: Long) {
    NO_LOCK(0b001),
    BIASED(0b101),
    LIGHTWEIGHT(0b000),
    HEAVYWEIGHT(0b010);

    companion object {
        fun from(bits: Long): LockState {
            val low = bits and 0b111L
            return entries.firstOrNull { it.tag == low }
                ?: error("unknown lock state bits: ${low.toString(2)}")
        }
    }
}

/**
 * 模拟对象头里的 Mark Word。AtomicLong 取代真实的 cmpxchg 指令,
 * 状态机的每一次升级都靠 [cas] 抢这一个 8 字节。
 */
class MarkWord(initial: Long = LockState.NO_LOCK.tag) {

    private val raw = AtomicLong(initial)

    val value: Long get() = raw.get()

    fun state(): LockState = LockState.from(raw.get())

    fun cas(expected: Long, updated: Long): Boolean =
        raw.compareAndSet(expected, updated)

    fun set(updated: Long) {
        raw.set(updated)
    }

    override fun toString(): String {
        val v = raw.get()
        val payload = v ushr 3
        return "MarkWord(state=${state()}, payload=0x${payload.toString(16)})"
    }

    companion object {
        // 偏向锁 Mark Word 位布局 (高位 → 低位):
        //   [ threadId | tag(3 bit = 101) ]
        // threadId 直接放在 tag 之上,3 位偏移避开标志位。
        fun encodeBiased(threadId: Long): Long =
            (threadId shl 3) or LockState.BIASED.tag

        fun decodeBiasedThreadId(value: Long): Long = value ushr 3

        fun encodePointer(ptr: Long, state: LockState): Long =
            (ptr shl 3) or state.tag

        fun decodePointer(value: Long): Long = value ushr 3
    }
}
