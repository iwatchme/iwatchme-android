package com.iwatchme.locksim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BiasedLockTest {

    @Test
    fun `single thread first entry biases the lock`() {
        val engine = LockEngine(events = EventLog(printToStdout = true))
        val t = SimThread("A")
        val obj = SimObject()

        engine.monitorEnter(obj, t)
        engine.monitorExit(obj, t)

        assertEquals(LockState.BIASED, obj.header.state())
        assertEquals(1, engine.events.count<LockEvent.Bias>())
    }

    @Test
    fun `same thread reentry is biased fast path`() {
        val engine = LockEngine(events = EventLog(printToStdout = true))
        val t = SimThread("A")
        val obj = SimObject()

        repeat(5) {
            engine.monitorEnter(obj, t)
            engine.monitorExit(obj, t)
        }

        assertEquals(1, engine.events.count<LockEvent.Bias>())
        assertEquals(4, engine.events.count<LockEvent.BiasReenter>())
        assertTrue(engine.events.count<LockEvent.LightweightAcquired>() == 0)
    }

    @Test
    fun `with biased locking disabled goes straight to lightweight`() {
        val engine = LockEngine(biasedLockingEnabled = false, events = EventLog(printToStdout = true))
        val t = SimThread("A")
        val obj = SimObject()

        engine.monitorEnter(obj, t)
        engine.monitorExit(obj, t)

        assertEquals(0, engine.events.count<LockEvent.Bias>())
        assertEquals(1, engine.events.count<LockEvent.LightweightAcquired>())
    }
}
