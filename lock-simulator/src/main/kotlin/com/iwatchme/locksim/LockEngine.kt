package com.iwatchme.locksim

import java.util.concurrent.ConcurrentHashMap

/**
 * 锁状态机引擎。读这份代码请抓住一个核心:
 *
 *   "升级 = 改写 Mark Word",每一次升级都是某个线程的 CAS 成功了。
 *   每一档锁都建立在一个"乐观假设"上,假设被打破才会升级:
 *     无锁    -> 偏向锁     假设没人来抢
 *     偏向锁  -> 轻量级锁   假设永远只有我一个线程
 *     轻量级  -> 重量级锁   假设竞争方很快释放,值得自旋
 *     重量级  -> (不降级)   一旦走到这里就承认有真竞争
 */
class LockEngine(
    val biasedLockingEnabled: Boolean = true,
    val safepoint: Safepoint = Safepoint(),
    val spinPolicy: SpinPolicy = SpinPolicy(),
    val events: EventLog = EventLog(),
) {

    private val monitors = ConcurrentHashMap<Long, ObjectMonitor>()
    private val threadRegistry = ConcurrentHashMap<Long, SimThread>()

    fun register(thread: SimThread) {
        threadRegistry[thread.id] = thread
    }

    /**
     * synchronized 入口的总调度。读 Mark Word 决定走哪条路径,
     * 每条路径自己负责 CAS 失败/状态改变后的重派发 —— 实现里都是
     * "失败就递归调用 monitorEnter",对应 HotSpot 里的 goto retry。
     */
    fun monitorEnter(obj: SimObject, t: SimThread) {
        register(t)
        when (obj.header.state()) {
            LockState.NO_LOCK -> tryBiasedLock(obj, t)
            LockState.BIASED -> handleBiasedLock(obj, t)
            LockState.LIGHTWEIGHT -> handleLightweightLock(obj, t)
            LockState.HEAVYWEIGHT -> handleHeavyweightLock(obj, t)
        }
    }

    fun monitorExit(obj: SimObject, t: SimThread) {
        when (obj.header.state()) {
            LockState.BIASED -> {
                // 关键: 偏向锁 monitorExit 故意不动 Mark Word。
                //
                // 原因 —— 偏向锁的整个赌注就是"同一个线程会反复进出这把锁":
                //   - 如果出来时把 Mark Word 改回 NO_LOCK,下次进来又得 CAS 改回 BIASED,
                //     一进一出两次 CAS,跟轻量级锁开销一样,偏向锁就白设计了
                //   - 不动 Mark Word,下次同线程进来只需 "读 mark + 比 tid",
                //     连 CAS 都没有,这才对应文章里说的"几乎零成本(一次比较)"
                //
                // 代价被延后:别的线程真来抢的时候,在安全点撤销一次,集中付出。
                // 这里只 pop 栈上的 sentinel,维护本地的"是否还在临界区"深度信息,
                // 撤销路径靠 isInSyncBlock() 扫栈才能判断"原 owner 是不是真的还在里面"。
                val lr = t.topLockRecord(obj) ?: error("no sentinel LR for biased $obj")
                t.stack.remove(lr)
                events.record(LockEvent.Release(obj, t, LockState.BIASED))
            }
            LockState.LIGHTWEIGHT -> exitLightweight(obj, t)
            LockState.HEAVYWEIGHT -> exitHeavyweight(obj, t)
            LockState.NO_LOCK -> error("exit on NO_LOCK: $obj")
        }
    }

    // ---------- biased ----------

    /**
     * 无锁 → 偏向锁: 唯一一次"凭空把 threadId 写进 Mark Word"的机会。
     * 全局关闭偏向 (JDK15+ 默认) 或该 class 已批量撤销过,直接绕过偏向走轻量级。
     *
     * sentinel LockRecord 的存在是关键:HotSpot 真实实现里偏向锁不分配 LR,
     * 但 simulator 没法去走线程栈帧找 obj 引用,只能用一条空 LR 在栈里占位,
     * 后续撤销时才能判断"原 owner 是否还在临界区"。
     */
    private fun tryBiasedLock(obj: SimObject, t: SimThread) {
        if (!biasedLockingEnabled) {
            tryLightweightLock(obj, t)
            return
        }
        val oldVal = obj.header.value
        if (LockState.from(oldVal) != LockState.NO_LOCK) {
            // 别的线程刚把状态改了,重新进总入口走对应分支
            monitorEnter(obj, t)
            return
        }
        val newVal = MarkWord.encodeBiased(t.id)
        if (obj.header.cas(oldVal, newVal)) {
            t.allocateLockRecord(obj, null)
            events.record(LockEvent.Bias(obj, t))
            return
        }
        monitorEnter(obj, t)
    }

    /**
     * 已经是偏向锁状态时的两种分支:
     *
     *  1) 偏向的就是自己 → 重入,栈上多压一个 sentinel,几乎零开销 (文章里说的"一次比较")
     *  2) 偏向的是别人 → 进入安全点撤销,根据原 owner 状态决定降回无锁还是升到轻量级
     */
    private fun handleBiasedLock(obj: SimObject, t: SimThread) {
        val mark = obj.header.value
        val biasedTid = MarkWord.decodeBiasedThreadId(mark)

        if (biasedTid == t.id) {
            t.allocateLockRecord(obj, null)
            events.record(LockEvent.BiasReenter(obj, t))
            return
        }

        revokeBiasedLock(obj, t)
    }

    /**
     * 偏向锁撤销 —— 必须在安全点里做,因为要扫原 owner 的栈,期间它不能动。
     *
     * 撤销有三种结果,对应文章里的三种 case:
     *   - 原线程已死      → Mark Word 恢复无锁
     *   - 原线程没在临界区 → Mark Word 恢复无锁,等谁来再 bias
     *   - 原线程还在临界区 → 必须升级到轻量级 (见下面分析)
     *
     * ── 为什么"原线程还在临界区"必须升级,而且升的是轻量级? ──
     *
     * 矛盾: 偏向锁的"乐观假设——永远只有一个线程"已经被打破 (B 出现了),
     *      但 A 还在 synchronized 块里执行,不能简单"抢走"偏向。
     *
     * 1. 为什么不能维持偏向锁:
     *    偏向锁数据结构只能表达"我是不是 owner",Mark Word 里那 8 字节
     *    没地方放等待队列、也没有"被占用"信号。让 B 在偏向锁状态下等?
     *    偏向锁 monitorExit 又不动 Mark Word,B 自旋读永远读到 [tid=A | 101],
     *    根本观察不到 A 退出 → 死循环。
     *
     * 2. 为什么不能让 B 直接"接管"偏向:
     *    CAS 把 tid 改成 B → A 和 B 同时进同一个临界区,锁失去意义。
     *
     * 3. 为什么升到轻量级 (而不是直接到重量级):
     *    轻量级锁补齐了偏向锁的两个短板:
     *      - exit 时会 CAS 把 Mark Word 写回 → B 自旋能"看到"A 释放
     *      - Mark Word 里存的是 LR 指针,B 可以 CAS 抢过来当新 owner
     *    重量级也能解决问题,但要分配 ObjectMonitor + park/unpark (μs 级开销)。
     *    如果 A 马上就出来 (ns 级),让 B 自旋等等比挂起便宜得多。
     *    JVM 的策略: 先赌 A 快出来,赌输了再升重量级。
     *
     * 4. 升级动作的细节:
     *    A 的栈上有 N 条 sentinel (N = 嵌套深度),只把"栈底"那条改成真 LR
     *    (displacedHeader = NO_LOCK.tag),它对应 A 最外层的 synchronized 进入。
     *    上面那些 sentinel 保持 displacedHeader=null,A 后续逐层 exit 时:
     *      - 内层 exit: 看到 displaced=null → 直接 pop,不 CAS
     *      - 最外层 exit: 看到 displaced=NO_LOCK.tag → CAS 把 Mark Word 还原
     *    这跟"轻量级锁正常重入栈布局"完全一致,后续语义自动生效。
     */
    private fun revokeBiasedLock(obj: SimObject, contender: SimThread) {
        var upgradedToLightweight = false
        var ownerThread: SimThread? = null

        safepoint.stw {
            val mark = obj.header.value
            if (LockState.from(mark) != LockState.BIASED) return@stw
            val biasedTid = MarkWord.decodeBiasedThreadId(mark)
            val biased = threadRegistry[biasedTid]

            if (biased == null) {
                obj.header.set(LockState.NO_LOCK.tag)
                events.record(LockEvent.RevokeToNoLock(obj, "owner unknown"))
            } else if (!biased.isInSyncBlock(obj)) {
                obj.header.set(LockState.NO_LOCK.tag)
                events.record(LockEvent.RevokeToNoLock(obj, "owner not in critical section"))
            } else {
                // 关键: 把 A 栈底那条 sentinel 改造成"真"轻量级 LR。
                // 选栈底是因为它对应 A "最外层的一次 synchronized 进入",
                // 它之上的 sentinel 都是嵌套重入;栈底这条将来扛"恢复 Mark Word"的责任,
                // 上面的 sentinel exitLightweight 出栈时直接 pop、不需要 CAS。
                val lr = biased.bottomLockRecord(obj)
                    ?: error("biased thread missing LR for $obj")
                lr.displacedHeader = LockState.NO_LOCK.tag
                val ptr = System.identityHashCode(lr).toLong() and 0xFFFFFFFFL
                obj.header.set(MarkWord.encodePointer(ptr, LockState.LIGHTWEIGHT))
                lockRecordRegistry[ptr] = lr
                upgradedToLightweight = true
                ownerThread = biased
                events.record(LockEvent.RevokeToLightweight(obj, biased))
            }
        }

        if (upgradedToLightweight) {
            // contender now needs to enter the lightweight path
            handleLightweightLock(obj, contender)
        } else {
            monitorEnter(obj, contender)
        }
    }

    // ---------- lightweight ----------

    private val lockRecordRegistry = ConcurrentHashMap<Long, LockRecord>()

    private fun tryLightweightLock(obj: SimObject, t: SimThread) {
        val oldVal = obj.header.value
        if (LockState.from(oldVal) != LockState.NO_LOCK) {
            monitorEnter(obj, t)
            return
        }
        val lr = t.allocateLockRecord(obj, oldVal)
        val ptr = System.identityHashCode(lr).toLong() and 0xFFFFFFFFL
        val newVal = MarkWord.encodePointer(ptr, LockState.LIGHTWEIGHT)
        if (obj.header.cas(oldVal, newVal)) {
            lockRecordRegistry[ptr] = lr
            events.record(LockEvent.LightweightAcquired(obj, t))
            return
        }
        // CAS failed → pop and retry
        t.popLockRecord()
        monitorEnter(obj, t)
    }

    /**
     * 已经是轻量级状态时的三条路径:
     *
     *  1) 自己持有 → 直接重入,栈上摞一条 reentry marker (displacedHeader=null)
     *  2) 别人持有 + 头里仍指向别人的 LR → 自旋等它释放;每轮要重读 Mark Word,
     *     只有看到 NO_LOCK (持有者刚释放) 才尝试 CAS
     *  3) 自旋次数耗尽 → 触发膨胀,这是"乐观假设破灭"的瞬间
     */
    private fun handleLightweightLock(obj: SimObject, t: SimThread) {
        if (t.isInSyncBlock(obj)) {
            t.allocateLockRecord(obj, null)
            events.record(LockEvent.LightweightReenter(obj, t))
            return
        }
        val limit = spinPolicy.limit(obj)
        for (i in 0 until limit) {
            val oldVal = obj.header.value
            // 关键: B 只在看到 NO_LOCK 时才尝试 CAS。原因:
            //   轻量级锁的 CAS 永远是 "期望 NO_LOCK → 写入指向自己 LR 的指针",
            //   只要 Mark Word 还停在 LIGHTWEIGHT (= A 的 LR 指针在那),
            //   B 的 CAS 必然失败 (expected 对不上),硬试也是浪费 CPU。
            //
            // 所以"B 接手"这件事必然是两步 (对应状态机的 ⑤ + ⑥):
            //   ⑤ A 最外层 exit, exitLightweight 把 Mark Word CAS 回 NO_LOCK
            //   ⑥ B 的下一轮自旋读到 NO_LOCK,在这里 CAS 抢成功 → 变成新 owner
            // 两个 owner 之间永远要经过 NO_LOCK 这个空挡,没有"热替换"。
            //
            // 因此 LIGHTWEIGHT → LIGHTWEIGHT(换 owner) 的直接边不存在,
            // 状态机里画出来的 ⑥ 触发场景只有两个:
            //   • 这里: B 在自旋循环里等到 ⑤ 把 mark 改成 NO_LOCK,自己 CAS 接手
            //   • tryLightweightLock: 偏向锁全局关闭时首次 enter 直接 CAS 走轻量级
            if (LockState.from(oldVal) == LockState.NO_LOCK) {
                val lr = t.allocateLockRecord(obj, oldVal)
                val ptr = System.identityHashCode(lr).toLong() and 0xFFFFFFFFL
                val newVal = MarkWord.encodePointer(ptr, LockState.LIGHTWEIGHT)
                if (obj.header.cas(oldVal, newVal)) {
                    lockRecordRegistry[ptr] = lr
                    spinPolicy.onSpinSuccess(obj)
                    events.record(LockEvent.LightweightAcquired(obj, t))
                    return
                }
                // CAS 输给了另一个 spinner,LR 没用上,撤回再来
                t.popLockRecord()
            }
            events.record(LockEvent.SpinAttempt(obj, t, i + 1))
            Thread.onSpinWait()
        }
        spinPolicy.onSpinFailure(obj)
        inflateToHeavyweight(obj, t)
    }

    /**
     * 轻量级解锁要 CAS 把 Mark Word 写回最初的 displacedHeader,
     * 即 LIGHTWEIGHT → NO_LOCK,这是三种锁里**唯一一种 exit 时真的会改对象头**的。
     *
     * 三种锁 exit 时 Mark Word 行为对比:
     *   偏向锁:    不变 (永远 [tid|101],靠下次 revoke 才重置) → 赌"同一线程反复进出,exit 零成本"
     *   轻量级锁: **CAS 回 NO_LOCK** (本函数干的事)        → 必须让自旋的对方看见"我释放了"
     *   重量级锁: 不变 (一直指向 Monitor,只清 owner 字段)   → 一旦膨胀就不降级
     *
     * 为什么轻量级锁必须降级? 因为竞争线程在自旋读 Mark Word 等 NO_LOCK 出现,
     * 不改头 → 对方永远等不到信号 → 死循环。这跟偏向锁"赌 owner 反复重入"完全相反。
     *
     * 一个 corner case: 持锁期间对象被别人 INFLATE 成 HEAVYWEIGHT,
     * 这时 CAS 必失败 (期待的是 LIGHTWEIGHT 头,实际是 HEAVYWEIGHT) → fallthrough 走 exitHeavyweight。
     * 一旦走到这一步,对象就再也回不到 NO_LOCK 了 (重量级不降级)。
     */
    private fun exitLightweight(obj: SimObject, t: SimThread) {
        val lr = t.topLockRecord(obj) ?: error("no lock record for $obj on $t")
        if (lr.displacedHeader == null) {
            // sentinel/reentry: 只是出栈,不动 Mark Word
            t.stack.remove(lr)
            events.record(LockEvent.Release(obj, t, LockState.LIGHTWEIGHT))
            return
        }
        val expectedPtr = System.identityHashCode(lr).toLong() and 0xFFFFFFFFL
        val expectedVal = MarkWord.encodePointer(expectedPtr, LockState.LIGHTWEIGHT)
        val displaced = lr.displacedHeader!!
        if (obj.header.cas(expectedVal, displaced)) {
            t.stack.remove(lr)
            lockRecordRegistry.remove(expectedPtr)
            events.record(LockEvent.Release(obj, t, LockState.LIGHTWEIGHT))
            return
        }
        exitHeavyweight(obj, t)
        t.stack.remove(lr)
        lockRecordRegistry.remove(expectedPtr)
    }

    // ---------- heavyweight ----------

    /**
     * 膨胀:把对象从轻量级"长出"一个 ObjectMonitor。
     *
     * 关键是把当前持锁人 (栈里有 LR 的那个线程) 平移到 monitor.owner,
     * 把它已经累计的重入次数 (LR 个数 - 1) 写到 monitor.recursions ——
     * 否则原持锁人后续的解锁会变成"非 owner 解锁",check 直接抛异常。
     * 这一步必须在改写 Mark Word 之前完成。
     */
    private fun inflateToHeavyweight(obj: SimObject, t: SimThread) {
        events.record(LockEvent.InflateToHeavyweight(obj, t))
        val monitor = monitors.computeIfAbsent(obj.id) { ObjectMonitor(obj) }
        val mark = obj.header.value
        val state = LockState.from(mark)
        if (state == LockState.LIGHTWEIGHT) {
            val ptr = MarkWord.decodePointer(mark)
            val lr = lockRecordRegistry[ptr]
            val ownerThread = threadRegistry.values.firstOrNull { th -> th.isInSyncBlock(obj) }
            if (lr != null && ownerThread != null) {
                monitor.owner = ownerThread
                monitor.recursions = ownerThread.countLockRecords(obj) - 1
            }
        }
        val ptrToMonitor = System.identityHashCode(monitor).toLong() and 0xFFFFFFFFL
        obj.header.set(MarkWord.encodePointer(ptrToMonitor, LockState.HEAVYWEIGHT))
        handleHeavyweightLock(obj, t)
    }

    /**
     * 重量级抢锁 = OS 互斥的模型: 拿不到就排进 _EntryList 然后 park,
     * 对应 HotSpot 调 pthread_mutex_lock / futex,代价是用户态-内核态切换。
     * 模拟器用 Condition.await 来近似 park,唤醒由前一个 owner 的 exit 触发。
     */
    private fun handleHeavyweightLock(obj: SimObject, t: SimThread) {
        val monitor = monitorOf(obj)
        monitor.mutex.lock()
        try {
            if (monitor.owner === t) {
                monitor.recursions += 1
                events.record(LockEvent.HeavyweightReenter(obj, t))
                return
            }
            while (monitor.owner != null) {
                monitor.entryList.add(t)
                events.record(LockEvent.Park(obj, t))
                monitor.notEmpty.await()
                monitor.entryList.remove(t)
                events.record(LockEvent.Unpark(obj, t))
            }
            monitor.owner = t
            monitor.recursions = 0
            events.record(LockEvent.HeavyweightAcquired(obj, t))
        } finally {
            monitor.mutex.unlock()
        }
    }

    private fun exitHeavyweight(obj: SimObject, t: SimThread) {
        val monitor = monitorOf(obj)
        monitor.mutex.lock()
        try {
            check(monitor.owner === t) { "exit by non-owner: $t (owner=${monitor.owner})" }
            if (monitor.recursions > 0) {
                monitor.recursions -= 1
                return
            }
            // drop any leftover LRs for this obj on the owner's stack (legacy from bias/lightweight)
            t.stack.removeAll { it.owner === obj }
            monitor.owner = null
            events.record(LockEvent.Release(obj, t, LockState.HEAVYWEIGHT))
            monitor.notEmpty.signal()
        } finally {
            monitor.mutex.unlock()
        }
    }

    private fun monitorOf(obj: SimObject): ObjectMonitor =
        monitors.computeIfAbsent(obj.id) { ObjectMonitor(obj) }

    // ---------- helpers ----------

    fun synchronized(obj: SimObject, t: SimThread, block: () -> Unit) {
        monitorEnter(obj, t)
        try {
            block()
        } finally {
            monitorExit(obj, t)
        }
    }
}
