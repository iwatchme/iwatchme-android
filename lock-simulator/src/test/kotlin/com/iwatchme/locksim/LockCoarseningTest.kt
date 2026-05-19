package com.iwatchme.locksim

import com.iwatchme.locksim.jit.JitOptimizer
import com.iwatchme.locksim.jit.MethodRunner
import com.iwatchme.locksim.jit.Op
import com.iwatchme.locksim.jit.method
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LockCoarseningTest {

    @Test
    fun `consecutive lock-unlock pairs on the same escaping object get coarsened`() {
        val m = method {
            newObj("lock", klass = "GlobalLock")
            handOff("lock", toThread = "other")   // make it escape so elision skips it
            lock("lock"); work(); unlock("lock")
            lock("lock"); work(); unlock("lock")
            lock("lock"); work(); unlock("lock")
            ret(null)
        }
        val report = JitOptimizer().optimize(m)

        val lockOps = report.optimized.ops.count { it is Op.Lock }
        val unlockOps = report.optimized.ops.count { it is Op.Unlock }
        assertEquals(1, lockOps, "expected coarsened to a single lock")
        assertEquals(1, unlockOps, "expected coarsened to a single unlock")
        assertEquals(2, report.coarsened["lock"] ?: 0)

        val engine = LockEngine(events = EventLog(printToStdout = true))
        val stats = MethodRunner(engine).run(report.optimized, SimThread("worker"))
        assertEquals(1, stats.lockCalls)
        assertEquals(1, stats.unlockCalls)
    }

    @Test
    fun `coarsening preserves operations between locks`() {
        val m = method {
            newObj("x", klass = "Shared")
            handOff("x", toThread = "other")
            lock("x"); work(5); unlock("x")
            lock("x"); work(7); unlock("x")
            ret(null)
        }
        val report = JitOptimizer().optimize(m)
        val workOps = report.optimized.ops.filterIsInstance<Op.Work>()
        assertEquals(2, workOps.size)
        assertTrue(workOps.any { it.cycles == 5 })
        assertTrue(workOps.any { it.cycles == 7 })
    }
}
