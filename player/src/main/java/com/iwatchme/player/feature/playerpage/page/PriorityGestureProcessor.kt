package com.iwatchme.player.feature.playerpage.page

import android.util.SparseArray
import java.util.LinkedList

/**
 * 带优先级的 listener 链：HIGH → NORMAL → LOW → LOWEST 顺序派发，
 * 先返回 true 的短路掉后续。默认实现约定挂 LOWEST，业务注入挂 NORMAL。
 */
class PriorityGestureProcessor<T> {

    companion object {
        const val PRIORITY_LOWEST = 0
        const val PRIORITY_LOW = 1
        const val PRIORITY_NORMAL = 2
        const val PRIORITY_HIGH = 3
    }

    private val entries = SparseArray<PriorityEntry<T>>(3)

    fun process(shouldConsume: (callback: T) -> Boolean): Boolean {
        if (entries[PRIORITY_HIGH]?.process(shouldConsume) == true) return true
        if (entries[PRIORITY_NORMAL]?.process(shouldConsume) == true) return true
        if (entries[PRIORITY_LOW]?.process(shouldConsume) == true) return true
        if (entries[PRIORITY_LOWEST]?.process(shouldConsume) == true) return true
        return false
    }

    fun add(callback: T, priority: Int) {
        val actual = priority.coerceIn(PRIORITY_LOWEST, PRIORITY_HIGH)
        var entry = entries[actual]
        if (entry?.contains(callback) == true) return
        if (entry == null) {
            entry = PriorityEntry()
            entries.put(actual, entry)
        }
        entry.add(callback)
    }

    fun remove(callback: T) {
        for (i in 0 until entries.size()) {
            entries[entries.keyAt(i)].remove(callback)
        }
    }

    fun clear() {
        entries.clear()
    }

    private class PriorityEntry<T> {
        private val callbacks = LinkedList<T>()

        fun process(shouldConsume: (callback: T) -> Boolean): Boolean {
            callbacks.forEach { if (shouldConsume(it)) return true }
            return false
        }

        fun add(callback: T) { callbacks.add(callback) }

        fun remove(callback: T) {
            val it = callbacks.iterator()
            while (it.hasNext()) if (it.next() == callback) it.remove()
        }

        fun contains(callback: T): Boolean = callbacks.contains(callback)
    }
}
