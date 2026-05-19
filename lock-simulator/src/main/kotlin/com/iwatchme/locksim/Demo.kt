package com.iwatchme.locksim

import com.iwatchme.locksim.jit.JitOptimizer
import com.iwatchme.locksim.jit.MethodRunner
import com.iwatchme.locksim.jit.Op
import com.iwatchme.locksim.jit.method

/**
 * 跑一次就能把文章里所有关键状态机迁移都看到一遍。
 * 入口: `./gradlew :lock-simulator:runDemo`
 */
fun main() {
    section("场景 1: 单线程反复进同一把锁 (偏向锁 fast path,几乎零成本)")
    scenarioBiasReentry()

    section("场景 2: 第二个线程来 (owner 已退出) → 撤销到无锁后再 bias")
    scenarioBiasRevokeNoLock()

    section("场景 3: 第二个线程来 (owner 还在临界区) → 升级轻量级 → 自旋 → 膨胀重量级")
    scenarioBiasToLightToHeavy()

    section("场景 4: JIT 锁消除 — StringBuffer 没逃逸,append 里的锁直接删")
    scenarioLockElision()

    section("场景 5: JIT 锁粗化 — 循环里 3 对相邻 lock/unlock 合并成 1 对")
    scenarioLockCoarsening()
}

private fun section(title: String) {
    println()
    println("━".repeat(80))
    println("  $title")
    println("━".repeat(80))
}

private fun newEngine(): LockEngine = LockEngine(events = EventLog(printToStdout = true))

private fun scenarioBiasReentry() {
    val engine = newEngine()
    val a = SimThread("A")
    val obj = SimObject("Counter")
    repeat(3) {
        engine.monitorEnter(obj, a)
        engine.monitorExit(obj, a)
    }
    println("→ 最终 Mark Word state = ${obj.header.state()}  (期望: BIASED)")
}

private fun scenarioBiasRevokeNoLock() {
    val engine = newEngine()
    val a = SimThread("A")
    val b = SimThread("B")
    val obj = SimObject("MessageQueue")
    engine.monitorEnter(obj, a); engine.monitorExit(obj, a)
    engine.monitorEnter(obj, b); engine.monitorExit(obj, b)
    println("→ 最终 Mark Word state = ${obj.header.state()}  (期望: BIASED to B)")
}

private fun scenarioBiasToLightToHeavy() {
    val engine = LockEngine(
        spinPolicy = SpinPolicy(minSpins = 3, maxSpins = 3),
        events = EventLog(printToStdout = true),
    )
    val a = SimThread("A")
    val b = SimThread("B")
    val obj = SimObject("Resource")

    engine.monitorEnter(obj, a)  // A bias 后停留在临界区

    val worker = Thread {
        engine.monitorEnter(obj, b)  // B 触发 revoke → spin → inflate → park
        engine.monitorExit(obj, b)
    }
    worker.start()

    // 等 B 走到 park
    val deadline = System.currentTimeMillis() + 2000
    while (System.currentTimeMillis() < deadline &&
        !engine.events.has { it is LockEvent.Park }
    ) Thread.sleep(10)

    Thread.sleep(50)
    engine.monitorExit(obj, a)   // 释放 → B 被唤醒,获得 monitor
    worker.join(2000)
    println("→ 最终 Mark Word state = ${obj.header.state()}  (期望: HEAVYWEIGHT,不降级)")
}

private fun scenarioLockElision() {
    val m = method {
        newObj("sb", klass = "StringBuffer")
        lock("sb"); work(); unlock("sb")
        lock("sb"); work(); unlock("sb")
        ret(null)   // 不返回 sb,完全局部
    }
    val report = JitOptimizer().optimize(m)
    println("JIT report: elided=${report.elided}")
    println("优化后剩余 Op 序列:")
    report.optimized.ops.forEach { println("    $it") }
    val engine = newEngine()
    val stats = MethodRunner(engine).run(report.optimized, SimThread("worker"))
    println("→ 实际 monitorEnter 调用次数 = ${stats.lockCalls}  (期望: 0)")
}

private fun scenarioLockCoarsening() {
    val m = method {
        newObj("lock", klass = "GlobalLock")
        handOff("lock", toThread = "consumer")   // 让它逃逸,避免被锁消除吃掉
        lock("lock"); work(); unlock("lock")
        lock("lock"); work(); unlock("lock")
        lock("lock"); work(); unlock("lock")
        ret(null)
    }
    val report = JitOptimizer().optimize(m)
    println("JIT report: coarsened=${report.coarsened}")
    println("优化后剩余 Op 序列:")
    report.optimized.ops.forEach { println("    $it") }
    val engine = newEngine()
    val stats = MethodRunner(engine).run(report.optimized, SimThread("worker"))
    println("→ 实际 monitorEnter 调用次数 = ${stats.lockCalls}  (期望: 1)")
}
