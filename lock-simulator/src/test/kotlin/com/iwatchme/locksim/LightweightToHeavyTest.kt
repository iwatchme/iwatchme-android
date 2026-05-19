package com.iwatchme.locksim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LightweightToHeavyTest {

    @Test
    fun `spin exhaustion inflates to heavyweight`() {
        val engine = LockEngine(
            biasedLockingEnabled = false,
            spinPolicy = SpinPolicy(minSpins = 3, maxSpins = 3),
            events = EventLog(printToStdout = true),
        )
        val a = SimThread("A")
        val b = SimThread("B")
        val obj = SimObject()

        engine.monitorEnter(obj, a)

        val worker = Thread {
            engine.monitorEnter(obj, b)
            engine.monitorExit(obj, b)
        }
        worker.start()

        val deadline = System.currentTimeMillis() + 2000
        while (System.currentTimeMillis() < deadline &&
            !engine.events.has { it is LockEvent.InflateToHeavyweight }) {
            Thread.sleep(10)
        }
        assertTrue(engine.events.has { it is LockEvent.InflateToHeavyweight })
        assertEquals(LockState.HEAVYWEIGHT, obj.header.state())

        Thread.sleep(50)
        engine.monitorExit(obj, a)
        worker.join(2000)
        assertTrue(!worker.isAlive)
    }

    @Test
    fun `heavyweight lock does not downgrade after release`() {
        val engine = LockEngine(
            biasedLockingEnabled = false,
            spinPolicy = SpinPolicy(minSpins = 2, maxSpins = 2),
            events = EventLog(printToStdout = true),
        )
        val a = SimThread("A")
        val b = SimThread("B")
        val obj = SimObject()

        engine.monitorEnter(obj, a)
        val worker = Thread {
            engine.monitorEnter(obj, b)
            engine.monitorExit(obj, b)
        }
        worker.start()
        val deadline = System.currentTimeMillis() + 2000
        while (System.currentTimeMillis() < deadline &&
            !engine.events.has { it is LockEvent.InflateToHeavyweight }) {
            Thread.sleep(10)
        }
        Thread.sleep(50)
        engine.monitorExit(obj, a)
        worker.join(2000)

        assertEquals(LockState.HEAVYWEIGHT, obj.header.state())
    }
}
