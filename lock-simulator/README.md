# lock-simulator

Java/HotSpot 锁状态机的 Kotlin 模拟实现 —— 把 `synchronized` 背后的偏向锁 / 轻量级锁 / 重量级锁升级机制变成可运行、可观测、可测的代码。

## 运行

```bash
# 跑所有单测,日志实时打到 stdout
./gradlew :lock-simulator:test

# 跑 demo,把 5 个典型场景的状态机迁移过程一次性演示出来
./gradlew :lock-simulator:runDemo
```

## 锁状态机(最终正确版本)

### 4 个状态

| 状态 | Mark Word 编码 (低 3 位是标志位) | 含义 |
|---|---|---|
| NO_LOCK | `[hashCode \| 001]` | 无锁,头里存着 hashCode 等正经数据 |
| BIASED | `[threadId \| 101]` | 偏向锁,记录上次访问的线程 |
| LIGHTWEIGHT | `[LR* \| 000]` | 轻量级锁,头里是指向当前 owner 栈帧 LockRecord 的指针 |
| HEAVYWEIGHT | `[Monitor* \| 010]` | 重量级锁,头里是指向 ObjectMonitor 的指针 |

### 完整状态转移图

```
                            ┌──────────────────────────┐
                ┌──────────▶│         NO_LOCK          │
                │           │     [hashCode | 001]      │
                │           └──┬────────────────────┬──┘
                │              │                     │
                │       ① 首次 enter             ⑥ NO_LOCK → LIGHTWEIGHT
                │       (偏向锁开启)              (任意线程 CAS 写自己 LR 指针)
                │       CAS 写 tid                │
                │              │                  │  触发场景:
                │              │                  │   • B 自旋读到 NO_LOCK
                │              │                  │     后 CAS 抢成功
                │              │                  │   • 偏向锁关闭,首次
                │              │                  │     enter 直接走轻量级
                │              ▼                  │
                │   ┌──────────────────────────┐  │
                │   │          BIASED           │  │
                │   │       [tid | 101]         │  │
                │   │                           │  │   ┌─── ② 同线程 ─┐
                │   │                           │◀─┼───┤   enter / exit│
                │   └──┬───────────────────────┘  │   │ Mark Word 不变 │
                │      │                          │   │ 栈 push/pop    │
                │      │                          │   │   sentinel     │
                │      │                          │   └─── ⮌ ─────────┘
                │      │ ③ 别线程 enter            │
                │      │ → 进 STW revoke           │
                │      │                          │
                │      ▼                          │
                │  ┌───┴────────────┐             │
                │  │                │             │
                │ ③a owner 不在    ③b owner 还在  │
                │  CS / 已死        CS            │
                │  Mark Word        栈底 sentinel │
                │  → NO_LOCK        → 真 LR       │
                │      │                │         │
                └──────┘                │         │
                                        ▼         ▼
                            ┌──────────────────────────┐
                ┌──────────▶│       LIGHTWEIGHT         │
                │           │       [LR* | 000]         │
                │           │                           │   ┌─── ④ owner ─┐
                │           │                           │◀──┤  重入 / 内层 │
                │           │                           │   │   exit       │
                │           └──┬───────────────────┬───┘   │ Mark Word 不变 │
                │              │                    │      │ 栈 push/pop    │
                │              │                    │      │   sentinel     │
                │      ⑤ owner 最外层 exit       ⑦ 自旋耗尽 │   └─── ⮌ ─────┘
                │      CAS LIGHT → NO_LOCK       INFLATE
                │      ★ 唯一真正"降级"的边        分配 Monitor
                │              │                    │
                └──────────────┘                    │
                                                    ▼
                                       ┌──────────────────────────┐
                                       │       HEAVYWEIGHT         │
                                       │      [Monitor* | 010]     │
                                       │                           │   ┌─── ⑧ 任意 ─┐
                                       │                           │◀──┤  enter/exit/│
                                       │   ★★ 不降级 ★★           │   │   重入       │
                                       │   (HotSpot 运行期           │   │ Mark Word    │
                                       │    不会回到 LIGHT/         │   │ 永不变,只动 │
                                       │    BIAS/NO_LOCK)            │   │ monitor 字段│
                                       │                           │   └─── ⮌ ─────┘
                                       └──────────────────────────┘
```

### 8 条边的代码对照

每条边都能精准锁到代码行(`LockEngine.kt`):

| 边 | 状态转移 | 触发条件 | 代码位置 | 改 Mark Word 的方式 |
|---|---|---|---|---|
| ① | NO_LOCK → BIASED | 首次 enter,偏向锁开启 | `tryBiasedLock` L90 | **CAS** |
| ② | BIASED → BIASED | 同线程 enter / exit | `handleBiasedLock` L109 / `monitorExit` L58 | 不动头(栈 push/pop sentinel) |
| ③a | BIASED → NO_LOCK | revoke,原 owner 已退/已死 | `revokeBiasedLock` L166 / L169 | `set` (STW 内独占) |
| ③b | BIASED → LIGHTWEIGHT | revoke,原 owner 还在 CS | `revokeBiasedLock` L180 | `set` (STW 内独占) |
| ④ | LIGHTWEIGHT → LIGHTWEIGHT | owner 重入 / 内层 exit | `handleLightweightLock` L229 / `exitLightweight` L285 | 不动头 |
| ⑤ | **LIGHTWEIGHT → NO_LOCK** | owner 最外层 exit | `exitLightweight` L292 | **CAS** |
| ⑥ | **NO_LOCK → LIGHTWEIGHT** | 任意线程 CAS 写自己 LR 指针 | `tryLightweightLock` L209 / `handleLightweightLock` L249 | **CAS** |
| ⑦ | LIGHTWEIGHT → HEAVYWEIGHT | 自旋耗尽,INFLATE | `inflateToHeavyweight` L328 | `set` |
| ⑧ | HEAVYWEIGHT → HEAVYWEIGHT | 任意 enter / exit / 重入 | (从不动 obj.header) | **永不变**,只动 monitor 字段 |

### 不存在的边

以下转移**故意没有**,源码里也确实找不到对应路径:

| 不存在 | 原因 |
|---|---|
| `BIASED(A) → BIASED(B)` 直接换偏向 | tid 不匹配会去 revoke,不会直接 CAS 改 tid(否则 A、B 同时进临界区) |
| `LIGHTWEIGHT(A) → LIGHTWEIGHT(B)` 直接换 owner | CAS 永远 expected=NO_LOCK,B 看见 LIGHTWEIGHT 不会尝试 CAS。owner 切换必经 NO_LOCK 中转 |
| `LIGHTWEIGHT → BIASED` 反向降级 | 升上去就回不来 |
| `HEAVYWEIGHT → 任何其它` | HotSpot 运行期不降级("重量持锁不回头") |
| `NO_LOCK → HEAVYWEIGHT` 直跳 | inflate 只从 LIGHTWEIGHT 触发 |

## 三个核心 insight

### 1. 真正的 CAS 只在 3 条边上发生

`①`、`⑤`、`⑥` —— 都跟 owner 切换 / 状态进出的边界相关。其他边要么是 STW 内独占 `set`,要么完全不改 Mark Word。

### 2. NO_LOCK 是中央枢纽

- `①` 和 `⑥` 都从 NO_LOCK 出发往下
- `③a` 和 `⑤` 都汇回 NO_LOCK
- LIGHTWEIGHT 状态下 owner 切换**必经 NO_LOCK 中转**

### 3. 三种锁的 exit 行为完全不对称

| 锁类型 | exit 改 Mark Word? | 设计赌注 |
|---|---|---|
| 偏向锁 | **不改** (永远 `[tid\|101]`) | 赌"同一线程反复进出,exit 零成本" |
| 轻量级锁 | **CAS 回 NO_LOCK** | 必须让自旋的对方看见"我释放了" |
| 重量级锁 | 不改 (一直指向 Monitor) | 一旦膨胀就不降级 |

## 典型场景轨迹

跑 `./gradlew :lock-simulator:runDemo` 能看到 5 个场景的实时事件流。

**场景 A —— 单线程理想态**(无竞争):

```
NO_LOCK ──①──▶ BIASED ──②──▶ ②──▶ ②──▶ ...
                          (反复进出 Mark Word 一动不动)
```

**场景 B —— 中等竞争,owner 释放快**(走完 ⑤+⑥ 循环):

```
NO_LOCK ──①──▶ BIASED ──③b──▶ LIGHT(A) ──⑤──▶ NO_LOCK ──⑥──▶ LIGHT(B) ──⑤──▶ NO_LOCK
```

**场景 C —— 高竞争,owner 占着不放**(终态 HEAVYWEIGHT):

```
NO_LOCK ──①──▶ BIASED ──③b──▶ LIGHTWEIGHT ──⑦──▶ HEAVYWEIGHT ──⑧──▶ ⑧──▶ ... (永远)
```

## 关键设计细节

### LockRecord (栈上锁记录)

每个 `synchronized(obj)` 入口都在线程栈上"挖一块"`LockRecord`:

- **真 LR** (`displacedHeader != null`): 存被替换前的 Mark Word 原值,解锁时 CAS 写回
- **sentinel LR** (`displacedHeader == null`): 占位符,只为让 `isInSyncBlock()` 扫栈能感知到深度,出栈时直接 pop 不动 Mark Word

同一个 obj 一摞 LR,**只有栈底那条是真 LR**,负责恢复 Mark Word;上面摞着的全是 sentinel,exit 时一路 pop,只有最外层那次才动对象头。

### sentinel 一词

借自计算机里常见的"哨兵"概念 —— C 字符串末尾的 `\0`、链表的哨兵节点、数据库的墓碑标记…… 共同点是"只占位、不携带真实数据"。

### 偏向锁撤销的三种 case

`B.monitorEnter(obj)` 触发 STW revoke 时,JVM 扫 A 的栈决定怎么撤:

1. **A 已死** → Mark Word 重置 NO_LOCK
2. **A 没在 CS** → 同上(③a 路径)
3. **A 还在 CS** → 升级 LIGHTWEIGHT(③b 路径)。把 A 栈底的 sentinel 改造成真 LR(`displacedHeader = NO_LOCK.tag`),Mark Word 改成指向它的指针。这样 A 后续的 exit 自动按"轻量级重入栈布局"语义走。

### 为什么从 BIASED 不能直接到 HEAVYWEIGHT

技术上可以,但 HotSpot 选择经 LIGHTWEIGHT 中转,理由是:

- 如果 A 马上就出来(ns 级),让 B 自旋等等比 park/unpark 切两次内核态便宜得多
- 直接膨胀等于"一上来就认输",浪费了 A 可能马上出来的便宜机会

策略: 先赌 A 快出来(走轻量级),赌输了再升重量级。

## 模块结构

```
lock-simulator/
├── build.gradle.kts                       # Kotlin JVM 模块
├── src/main/kotlin/com/iwatchme/locksim/
│   ├── MarkWord.kt          # 64-bit 状态字 + 标志位编解码 + LockState 枚举
│   ├── SimObject.kt         # 模拟对象 (持有 MarkWord)
│   ├── SimThread.kt         # 模拟线程栈 + LockRecord 分配
│   ├── LockRecord.kt        # 栈帧里的锁记录 (真 LR / sentinel)
│   ├── ObjectMonitor.kt     # 重量级锁: owner / recursions / entryList
│   ├── LockEngine.kt        # 状态机引擎: monitorEnter/Exit 总分派
│   ├── Safepoint.kt         # 简化的 STW 安全点
│   ├── SpinPolicy.kt        # 自适应自旋次数
│   ├── Events.kt            # 状态迁移事件 + 实时日志
│   ├── Demo.kt              # 5 个典型场景的可运行 demo
│   └── jit/
│       ├── Op.kt            # 方法 IR (NewObj/Lock/Unlock/Work/...)
│       ├── EscapeAnalyzer.kt # 简版逃逸分析
│       ├── JitOptimizer.kt   # 锁消除 + 锁粗化 两个 pass
│       └── MethodRunner.kt   # 执行 IR,统计 lock 调用次数
└── src/test/kotlin/com/iwatchme/locksim/
    ├── BiasedLockTest.kt
    ├── BiasToLightweightTest.kt
    ├── LightweightToHeavyTest.kt
    ├── LockElisionTest.kt
    └── LockCoarseningTest.kt
```

## 跟真实 HotSpot 的差距

- ✗ 真实 Mark Word 在 C++ HotSpot 里,通过 Unsafe 改不了。这里全用 `AtomicLong` 模拟
- ✗ 真实 STW 走 polling page + safepoint 协议,这里用一把进程级 `ReentrantLock` 近似
- ✗ 真实偏向锁不分配 LockRecord (扫栈帧找 obj 引用),这里用 sentinel 凑数
- ✗ 没实现批量重偏向 / 批量撤销 (20 / 40 阈值),JDK 15+ 已默认关闭这套机制
- ✗ 逃逸分析只识别 return / 写字段 / 跨线程传递三种"明确逃逸",HotSpot 用 Connection Graph

学习目的够用,真要看实现细节请去看 HotSpot 源码 `share/runtime/synchronizer.cpp` 和 `objectMonitor.cpp`。

## 参考

- 锁升级文章:`docs/Java 锁机制深度解析:从 JVM 原理到面试实战.md`
- JEP 374: Deprecate and Disable Biased Locking
