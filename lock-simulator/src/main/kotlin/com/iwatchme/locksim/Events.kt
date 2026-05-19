package com.iwatchme.locksim

import java.util.concurrent.CopyOnWriteArrayList

sealed class LockEvent {
    data class Bias(val obj: SimObject, val thread: SimThread) : LockEvent()
    data class BiasReenter(val obj: SimObject, val thread: SimThread) : LockEvent()
    data class RevokeToNoLock(val obj: SimObject, val reason: String) : LockEvent()
    data class RevokeToLightweight(val obj: SimObject, val from: SimThread) : LockEvent()
    data class LightweightAcquired(val obj: SimObject, val thread: SimThread) : LockEvent()
    data class LightweightReenter(val obj: SimObject, val thread: SimThread) : LockEvent()
    data class SpinAttempt(val obj: SimObject, val thread: SimThread, val n: Int) : LockEvent()
    data class InflateToHeavyweight(val obj: SimObject, val by: SimThread) : LockEvent()
    data class HeavyweightAcquired(val obj: SimObject, val thread: SimThread) : LockEvent()
    data class HeavyweightReenter(val obj: SimObject, val thread: SimThread) : LockEvent()
    data class Park(val obj: SimObject, val thread: SimThread) : LockEvent()
    data class Unpark(val obj: SimObject, val thread: SimThread) : LockEvent()
    data class Release(val obj: SimObject, val thread: SimThread, val state: LockState) : LockEvent()
    data class LockElided(val obj: SimObject, val reason: String) : LockEvent()
    data class LockCoarsened(val obj: SimObject, val mergedPairs: Int) : LockEvent()
}

class EventLog(
    // 设为 true 时每条事件都会实时打印到 stdout,便于跑 demo / 调试时直接看到状态机迁移
    var printToStdout: Boolean = false,
) {

    private val events = CopyOnWriteArrayList<LockEvent>()
    private val startNanos = System.nanoTime()

    fun record(e: LockEvent) {
        events.add(e)
        if (printToStdout) {
            val tMs = (System.nanoTime() - startNanos) / 1_000_000.0
            println("[%7.2f ms] %s".format(tMs, format(e)))
        }
    }

    fun snapshot(): List<LockEvent> = events.toList()

    fun clear() {
        events.clear()
    }

    inline fun <reified T : LockEvent> count(): Int = snapshot().count { it is T }

    inline fun <reified T : LockEvent> filter(): List<T> =
        snapshot().filterIsInstance<T>()

    fun has(predicate: (LockEvent) -> Boolean): Boolean = snapshot().any(predicate)

    private fun format(e: LockEvent): String = when (e) {
        is LockEvent.Bias                 -> "BIAS                ${e.obj} ← ${e.thread}"
        is LockEvent.BiasReenter          -> "  bias-reenter      ${e.obj} ← ${e.thread}"
        is LockEvent.RevokeToNoLock       -> "REVOKE → NO_LOCK    ${e.obj}  (${e.reason})"
        is LockEvent.RevokeToLightweight  -> "REVOKE → LIGHT      ${e.obj}  (owner ${e.from} still in CS)"
        is LockEvent.LightweightAcquired  -> "LIGHTWEIGHT         ${e.obj} ← ${e.thread}"
        is LockEvent.LightweightReenter   -> "  light-reenter     ${e.obj} ← ${e.thread}"
        is LockEvent.SpinAttempt          -> "  spin #${e.n}".padEnd(20) + "${e.obj} ← ${e.thread}"
        is LockEvent.InflateToHeavyweight -> "INFLATE → HEAVY     ${e.obj}  triggered by ${e.by}"
        is LockEvent.HeavyweightAcquired  -> "HEAVYWEIGHT         ${e.obj} ← ${e.thread}"
        is LockEvent.HeavyweightReenter   -> "  heavy-reenter     ${e.obj} ← ${e.thread}"
        is LockEvent.Park                 -> "  park              ${e.thread}  on ${e.obj}"
        is LockEvent.Unpark               -> "  unpark            ${e.thread}  on ${e.obj}"
        is LockEvent.Release              -> "release ${e.state}".padEnd(20) + "${e.obj} ← ${e.thread}"
        is LockEvent.LockElided           -> "JIT: LOCK_ELIDED    ${e.obj}  (${e.reason})"
        is LockEvent.LockCoarsened        -> "JIT: COARSENED      ${e.obj}  (merged ${e.mergedPairs} pair(s))"
    }
}
