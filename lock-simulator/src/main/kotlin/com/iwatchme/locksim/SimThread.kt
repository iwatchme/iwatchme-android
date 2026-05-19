package com.iwatchme.locksim

import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.Condition

class SimThread(val name: String) {

    val id: Long = nextId.incrementAndGet()

    val stack: ArrayDeque<LockRecord> = ArrayDeque()

    val parkLock: ReentrantLock = ReentrantLock()
    val parkCondition: Condition = parkLock.newCondition()

    @Volatile
    var parked: Boolean = false

    /**
     * 模拟 HotSpot "在当前线程栈帧里开一块 Lock Record" 的动作。
     *
     * 为什么必须开在线程栈、不放堆:
     *   1) 零分配开销 —— 跟着栈帧一起生灭,不走 GC
     *   2) 天然线程隔离 —— 别的线程看不到我的栈,完全不用同步
     *   3) 生命周期天然匹配 synchronized 块 —— 出块就消失,不会泄漏
     *
     * LockRecord 本质是"对象头那 8 字节的临时寄存柜"。轻量级锁加锁时:
     *   - 把 obj.header 当前值搬到 LockRecord.displacedHeader 暂存
     *   - 把 obj.header 改成"指向这条 LR 的指针 | 000"
     *   - 解锁时 CAS 把 displacedHeader 写回 obj.header,原始数据 (含 hashCode) 复原
     *
     * 三种调用场景:
     *   - 偏向锁入口/重入: displaced=null,sentinel,只为占一格让 isInSyncBlock() 感知深度
     *   - 轻量级锁加锁:   displaced=旧 header,真寄存,解锁时要 CAS 写回
     */
    fun allocateLockRecord(target: SimObject, displaced: Long?): LockRecord {
        val lr = LockRecord(target, displaced)
        stack.push(lr)
        return lr
    }

    fun popLockRecord(): LockRecord = stack.pop()

    fun topLockRecord(target: SimObject): LockRecord? =
        stack.firstOrNull { it.owner === target }

    fun countLockRecords(target: SimObject): Int =
        stack.count { it.owner === target }

    fun isInSyncBlock(target: SimObject): Boolean =
        stack.any { it.owner === target }

    /**
     * 取 target 在栈里最底部那条 LockRecord —— 即"最早进入临界区"的那次。
     * 偏向锁撤销升级到轻量级时,要把这条 sentinel 改成真 LockRecord,
     * 上面摞着的 sentinel 都是后续重入,保持 displacedHeader=null 即可。
     */
    fun bottomLockRecord(target: SimObject): LockRecord? {
        var bottom: LockRecord? = null
        for (lr in stack) {
            if (lr.owner === target) bottom = lr
        }
        return bottom
    }

    fun park() {
        parkLock.lock()
        try {
            parked = true
            while (parked) parkCondition.await()
        } finally {
            parkLock.unlock()
        }
    }

    fun unpark() {
        parkLock.lock()
        try {
            parked = false
            parkCondition.signalAll()
        } finally {
            parkLock.unlock()
        }
    }

    override fun toString(): String = "SimThread#$id($name)"

    companion object {
        private val nextId = AtomicLong(0)
    }
}
