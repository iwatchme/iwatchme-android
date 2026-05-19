package com.iwatchme.locksim.jit

import com.iwatchme.locksim.LockEngine
import com.iwatchme.locksim.SimObject
import com.iwatchme.locksim.SimThread

class MethodRunner(val engine: LockEngine) {

    data class RunStats(
        val lockCalls: Int,
        val unlockCalls: Int,
    )

    fun run(method: Method, thread: SimThread): RunStats {
        val locals = mutableMapOf<String, SimObject>()
        var lockCalls = 0
        var unlockCalls = 0
        for (op in method.ops) {
            when (op) {
                is Op.NewObj -> locals[op.tag] = SimObject(op.klass)
                is Op.Lock -> {
                    val obj = locals[op.tag] ?: error("unknown local '${op.tag}'")
                    engine.monitorEnter(obj, thread)
                    lockCalls++
                }
                is Op.Unlock -> {
                    val obj = locals[op.tag] ?: error("unknown local '${op.tag}'")
                    engine.monitorExit(obj, thread)
                    unlockCalls++
                }
                is Op.Work -> repeat(op.cycles) { Thread.onSpinWait() }
                is Op.WriteField -> locals[op.value]?.let { it.escaped = true }
                is Op.HandOff -> locals[op.tag]?.let { it.escaped = true }
                is Op.Return -> Unit
            }
        }
        return RunStats(lockCalls, unlockCalls)
    }
}
