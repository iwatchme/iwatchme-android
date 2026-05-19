package com.iwatchme.locksim

import com.iwatchme.locksim.jit.JitOptimizer
import com.iwatchme.locksim.jit.MethodRunner
import com.iwatchme.locksim.jit.method
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LockElisionTest {

    @Test
    fun `non-escaping object has its locks eliminated`() {
        val m = method {
            newObj("sb", klass = "StringBuffer")
            lock("sb"); work(); unlock("sb")
            lock("sb"); work(); unlock("sb")
            ret(null)
        }
        val report = JitOptimizer().optimize(m)

        assertEquals(listOf("sb", "sb"), report.elided)
        assertTrue(report.optimized.ops.none { it is com.iwatchme.locksim.jit.Op.Lock })

        val engine = LockEngine(events = EventLog(printToStdout = true))
        val stats = MethodRunner(engine).run(report.optimized, SimThread("worker"))
        assertEquals(0, stats.lockCalls)
        assertEquals(0, stats.unlockCalls)
    }

    @Test
    fun `escaping object via return is not elided`() {
        val m = method {
            newObj("sb", klass = "StringBuffer")
            lock("sb"); work(); unlock("sb")
            ret("sb")
        }
        val report = JitOptimizer().optimize(m)
        assertTrue(report.elided.isEmpty())
    }

    @Test
    fun `escaping object via handoff is not elided`() {
        val m = method {
            newObj("q", klass = "Queue")
            lock("q"); work(); unlock("q")
            handOff("q", toThread = "consumer")
        }
        val report = JitOptimizer().optimize(m)
        assertTrue(report.elided.isEmpty())
    }
}
