package com.iwatchme.locksim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BiasToLightweightTest {

    @Test
    fun `second thread after owner exited resets to nolock then biases`() {
        val engine = LockEngine(events = EventLog(printToStdout = true))
        val a = SimThread("A")
        val b = SimThread("B")
        val obj = SimObject()

        engine.monitorEnter(obj, a)
        engine.monitorExit(obj, a)

        engine.monitorEnter(obj, b)
        engine.monitorExit(obj, b)

        val log = engine.events.snapshot()
        val hasNoLockRevoke = log.any { it is LockEvent.RevokeToNoLock }
        val hasReBias = engine.events.count<LockEvent.Bias>() == 2
        assertTrue(hasNoLockRevoke, "expected revoke-to-no-lock event")
        assertTrue(hasReBias, "expected B to acquire its own bias")
    }

    /**
     * 验证 "revoke 这一步" 把 BIASED 升到 LIGHTWEIGHT。
     *
     * 注意终态不是 lightweight: 因为 A 持锁不释放,B 抢 lightweight 的 CAS 必失败,
     * 自旋耗尽后会继续 INFLATE → HEAVYWEIGHT。完整链:
     *   BIASED(A) → [revoke] → LIGHTWEIGHT(A) → [B 自旋耗尽] → HEAVYWEIGHT
     * 测试只断言 revoke 那一步发生了,以及最终能正常释放。
     */
    @Test
    fun `revoke from biased upgrades to lightweight when owner is still in CS`() {
        val engine = LockEngine(events = EventLog(printToStdout = true))
        val a = SimThread("A")
        val b = SimThread("B")
        val obj = SimObject()

        engine.monitorEnter(obj, a)   // A 拿到 BIASED 后留在临界区

        val bWorker = Thread {
            engine.monitorEnter(obj, b)
            engine.monitorExit(obj, b)
        }
        bWorker.start()

        // 等 revoke 事件出现
        val deadline = System.currentTimeMillis() + 2000
        while (System.currentTimeMillis() < deadline &&
            !engine.events.has { it is LockEvent.RevokeToLightweight }) {
            Thread.sleep(10)
        }
        assertTrue(engine.events.has { it is LockEvent.RevokeToLightweight },
            "revoke 应该把 BIASED 升到 LIGHTWEIGHT")

        Thread.sleep(100)             // 等 B 自旋耗尽 + 膨胀 + park
        engine.monitorExit(obj, a)    // A 释放 → B 醒
        bWorker.join(2000)
        assertTrue(!bWorker.isAlive, "B 线程应该已经完成")
        assertEquals(LockState.HEAVYWEIGHT, obj.header.state(),
            "由于 A 没及时退出,终态是 HEAVYWEIGHT (LIGHTWEIGHT 只是中间态)")
    }

    /**
     * 反面例子: A 在 B 自旋耗尽前先 exit,B 直接拿到 LIGHTWEIGHT,终态停在 LIGHTWEIGHT,
     * 不会继续膨胀到 HEAVYWEIGHT。
     */
    @Test
    fun `if owner releases fast enough contender stays at lightweight`() {
        val engine = LockEngine(
            spinPolicy = SpinPolicy(minSpins = 100, maxSpins = 100), // 给 B 留足自旋窗口
            events = EventLog(printToStdout = true),
        )
        val a = SimThread("A")
        val b = SimThread("B")
        val obj = SimObject()

        engine.monitorEnter(obj, a)

        val bWorker = Thread {
            engine.monitorEnter(obj, b)
            engine.monitorExit(obj, b)
        }
        bWorker.start()

        // 等 revoke 升到 lightweight
        val deadline = System.currentTimeMillis() + 2000
        while (System.currentTimeMillis() < deadline &&
            !engine.events.has { it is LockEvent.RevokeToLightweight }) {
            Thread.sleep(5)
        }

        // 立即释放,趁 B 还在自旋
        engine.monitorExit(obj, a)
        bWorker.join(2000)

        assertTrue(!bWorker.isAlive)
        // B 已经 exit 完,header 回到了 NO_LOCK;不查终态,改查"是否走过 lightweight 路径"
        val bGotLightweight = engine.events.snapshot()
            .filterIsInstance<LockEvent.LightweightAcquired>()
            .any { it.thread === b }
        assertTrue(bGotLightweight, "B 自旋期间应该直接拿到 LIGHTWEIGHT")
        assertEquals(0, engine.events.count<LockEvent.InflateToHeavyweight>(),
            "不应该膨胀到 HEAVYWEIGHT")
    }
}
