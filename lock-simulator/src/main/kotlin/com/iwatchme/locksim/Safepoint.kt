package com.iwatchme.locksim

import java.util.concurrent.locks.ReentrantLock

/**
 * 简化的全局安全点 (STW). 真实 HotSpot 会在分配/调用边界插入 polling page,
 * 这里就用一把进程级的 ReentrantLock 来达到"同时只有一份撤销动作在跑"的效果。
 * 凡是要扫别人栈、改 Mark Word 这种动作,都必须在 [stw] 块里做。
 */
class Safepoint {

    private val lock = ReentrantLock()

    fun <T> stw(block: () -> T): T {
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }
}
