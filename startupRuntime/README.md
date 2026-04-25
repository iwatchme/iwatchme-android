# StartupRuntime

基于 DAG（有向无环图）的 Android 冷启动任务调度引擎。通过声明式的依赖关系和阶段分类，将传统的串行初始化改为事件驱动的并行调度，同时保护主线程帧率不受影响。

## 目录结构

```
startupRuntime/src/main/java/com/iwatchme/startupruntime/
├── StartupRuntime.kt                  # 入口 Builder，组装任务列表并创建 Session
├── model/
│   ├── StartupTask.kt                 # 任务抽象基类，声明 id / 依赖 / dispatcher / stage
│   ├── StartupTaskContext.kt          # 任务执行时的上下文，提供 Application、日志、动态 tag
│   ├── StartupStage.kt               # 任务阶段枚举：BLOCKING / NON_BLOCKING / IDLE / ON_DEMAND
│   ├── StartupDispatcher.kt          # 线程调度枚举：MAIN / IO / CPU
│   ├── StartupTaskStatus.kt          # 任务状态枚举：PENDING → RUNNING → COMPLETED / FAILED / TIMED_OUT / SKIPPED
│   ├── StartupAwaitResult.kt         # await 系列方法的返回值，包含完成状态和超时任务列表
│   └── StartupReport.kt              # 启动报告：包含 StartupSummary 和每个任务的 StartupTaskReport
├── session/
│   └── StartupSession.kt             # 核心调度引擎：建图、校验、事件驱动调度、await 等待
├── dispatch/
│   ├── StartupExecutors.kt           # 线程池定义：IO (CachedThreadPool) / CPU (固定线程池) / Main
│   └── MainThreadMicroBatcher.kt     # 主线程微批处理器，按帧预算控制单帧内执行量
├── analysis/
│   ├── StartupPathAnalyzer.kt        # 关键路径分析：拓扑排序后计算最长路径（耗时瓶颈链）
│   └── StartupComparators.kt         # 任务报告排序比较器
└── internal/
    └── ProcessUtils.kt               # 进程判断工具：区分主进程和子进程
```

## 核心概念

### StartupTask — 任务定义

每个启动任务继承 `StartupTask`，声明以下属性：

```kotlin
abstract class StartupTask {
    abstract val id: String                              // 唯一标识
    open val dependencies: Set<String> = emptySet()      // 依赖的任务 id 集合
    open val dispatcher: StartupDispatcher = IO           // 执行线程
    open val stage: StartupStage = NON_BLOCKING           // 所属阶段
    open val timeoutMs: Long? = null                      // 超时阈值
    open val mainProcessOnly: Boolean = true              // 是否仅主进程执行

    abstract suspend fun run(context: StartupTaskContext)  // 任务体
}
```

使用 `String` 而非 `Class` 作为依赖标识，避免模块间编译依赖。

### StartupStage — 四个阶段

Stage 决定的不是任务怎么执行，而是**任务什么时候必须完成**。四个阶段对应冷启动时间线上的不同节点：

```
Application.onCreate()
  │
  ├─ DAG 引擎启动，所有任务按依赖关系调度
  │
  ├─ BLOCKING ──────→ SplashScreen 等它们完成才退出
  │                    awaitCritical() 返回 → criticalReady = true → Splash 消失
  │                    【决定用户看到 Splash 的时长】
  │
  ├─ NON_BLOCKING ──→ Splash 不等它们，但 reportFullyDrawn() 等它们
  │                    awaitFullDrawnReady() 返回 → fullReady = true → reportFullyDrawn()
  │                    【决定 TTFD 指标】
  │
  ├─ IDLE ──────────→ reportFullyDrawn() 之后，MessageQueue 空闲时才开始
  │                    enableIdleDrain() 开闸 → 之前被挡住的 IDLE 任务开始执行
  │                    【不影响任何启动指标，纯后台预加载】
  │
  └─ ON_DEMAND ─────→ 没有人等它，用户触发特定功能时手动调用 triggerOnDemand()
                       【按需初始化，如分享、支付、地图等低频功能】
```

#### 各阶段对比

| Stage | 谁在等它 | 不完成会怎样 | 典型场景 |
|-------|---------|------------|---------|
| **BLOCKING** | SplashScreen | 用户一直看 Splash | 日志系统、崩溃上报、缓存首屏数据 |
| **NON_BLOCKING** | `reportFullyDrawn()` | Splash 正常退出，但 TTFD 指标变大 | 埋点预热、图片管线、网络刷新 |
| **IDLE** | 没人等 | 无影响 | 预加载下一页数据、低优 SDK 初始化 |
| **ON_DEMAND** | 没人等 | 用户触发时再初始化，首次使用略慢 | 分享 SDK、支付 SDK、地图 SDK |

#### IDLE 的两道门机制

IDLE 任务不是依赖满足就立刻执行，它有两道门控：

1. **DAG 依赖门**：和其他任务一样，`remainingDependencies` 归零才有资格调度
2. **idleEnabled 开关门**：即使依赖归零，`idleEnabled = false` 时也会被 `scheduleIfEligible()` 挡住

```kotlin
// StartupSession.scheduleIfEligible()
StartupStage.IDLE -> {
    if (!idleEnabled.get()) {    // ← 第二道门
        node.scheduled.set(false)
        return                    // 不调度，等开闸
    }
    dispatchNode(node)
}
```

开闸时机：调用方在 `reportFullyDrawn()` 之后通过 `Looper.addIdleHandler` 等待主线程空闲，然后调用 `session.enableIdleDrain()`：

```kotlin
fun enableIdleDrain() {
    idleEnabled.set(true)                              // 翻转开关
    nodeMap.values
        .filter { it.task.stage == IDLE && it.remainingDependencies.get() == 0 }
        .forEach(::scheduleIfEligible)                  // 重新调度之前被挡住的任务
}
```

### StartupDispatcher — 三种线程调度

Dispatcher 决定任务**在哪个线程上执行**：

| Dispatcher | 底层实现 | 适用场景 |
|-----------|---------|---------|
| **MAIN** | `MainThreadMicroBatcher` | 必须在主线程执行的任务（操作 View、Compose、创建 Handler） |
| **IO** | `CachedThreadPool`（弹性线程数） | IO 密集型任务（文件读写、网络请求、数据库） |
| **CPU** | `ThreadPoolExecutor`（核心数/2 ~ 核心数） | CPU 密集型任务（JSON 解析、加密计算、正则编译） |

IO 和 CPU 分开的原因：IO 任务大部分时间在等待（sleeping），可以开很多线程不浪费 CPU；CPU 任务真正占 CPU，线程数过多反而因上下文切换变慢。

#### 主线程微批处理器 (MainThreadMicroBatcher)

主线程任务不是直接 `Handler.post` 扔上去执行，而是通过微批处理器按帧预算控制节奏：

```
收到主线程任务 → 加入队列 → Handler.post 触发 drain
                                │
                                ▼
                        从队列取任务执行
                                │
                        执行完检查耗时 ──→ 未超帧预算 → 继续取下一个
                                │
                                └──→ 超过帧预算(默认16ms) → 停止，Handler.post 排到下一帧
```

这样即使有多个主线程任务，也不会连续霸占主线程导致掉帧。

### 任务调度流程

#### 建图阶段 (init)

```
传入 List<StartupTask>
    │
    ├─ validateTasks()
    │   ├─ 检查 id 唯一性
    │   ├─ 检查依赖是否都存在
    │   └─ 拓扑排序检测循环依赖
    │
    └─ buildGraph()
        ├─ 为每个 task 创建 TaskNode（包含 CompletableDeferred、AtomicInteger 等）
        └─ 根据 dependencies 建立 parent → children 反向边
```

#### 运行阶段 (start)

调度采用**事件驱动**模式，不是按层级分批提交，而是谁的依赖满足了谁就跑：

```
start()
  │
  └─ 找到所有 remainingDependencies == 0 的根节点
      └─ scheduleIfEligible(node)
          │
          ├─ 检查 Stage 门控（IDLE 要 idleEnabled，ON_DEMAND 要 triggered）
          │
          └─ dispatchNode(node)
              │
              ├─ MAIN → MainThreadMicroBatcher.submit()
              └─ IO/CPU → taskScope.launch(对应Dispatcher)
                  │
                  └─ executeNode(node)
                      ├─ Trace.beginSection("startup:${id}")
                      ├─ task.run(context)
                      ├─ 记录耗时、状态
                      ├─ node.completion.complete(Unit)     // ← CompletableDeferred 信号
                      └─ onTaskFinished(node)
                          │
                          └─ 遍历 children，decrementAndGet()
                              └─ remaining == 0 → scheduleIfEligible(child)  // 递归传播
```

关键设计：**不按层级分批等待，而是事件驱动逐级传播**。例如 Layer 0 有 A(10ms) 和 B(100ms) 两个根任务，A 完成后依赖 A 的下游立刻开始执行，不需要等 B 也完成。

#### 等待机制

使用 `CompletableDeferred<Unit>` 实现轻量级信号通知：

- 每个 `TaskNode` 持有一个 `CompletableDeferred`
- 任务完成时调用 `completion.complete(Unit)` 广播信号
- `awaitCritical` / `awaitFullDrawnReady` / `awaitIdle` 是 `suspend` 函数，通过 `withTimeoutOrNull { node.completion.await() }` 挂起等待
- 等待期间**挂起协程、释放线程**，不会阻塞线程干等

```kotlin
suspend fun awaitCritical(timeoutMs: Long = 3_000L): StartupAwaitResult {
    val blockingNodes = tasks.filter { it.stage == BLOCKING }.mapNotNull { nodeMap[it.id] }
    return awaitNodes(blockingNodes, timeoutMs) { ... }
}

private suspend fun awaitNodes(nodes: List<TaskNode>, timeoutMs: Long, ...): StartupAwaitResult {
    nodes.forEach { node ->
        val remaining = deadline - SystemClock.elapsedRealtime()
        val completed = withTimeoutOrNull(remaining) { node.completion.await() } != null
        // ...
    }
}
```

### 启动报告 (StartupReport)

每次启动完成后可通过 `session.createReport()` 生成报告，包含：

- **StartupSummary**：整体统计（criticalReadyAtMs、fullReadyAtMs、idleCompletedAtMs、关键路径等）
- **List\<StartupTaskReport\>**：每个任务的详细记录（状态、线程名、启动偏移、耗时、batch id 等）
- **关键路径分析**：`StartupPathAnalyzer.computeKeyPath()` 通过拓扑排序计算最长路径，定位启动瓶颈链

关键路径示例：

```
keyPath = 494ms -> log_bootstrap -> crash_config -> analytics_warmup
```

表示这三个任务串联起来是整个 DAG 中耗时最长的一条链路，优化启动速度应优先从这条路径入手。

## 使用示例

### 1. 定义任务

```kotlin
val tasks = listOf(
    object : StartupTask() {
        override val id = "log_bootstrap"
        override val dispatcher = StartupDispatcher.MAIN
        override val stage = StartupStage.BLOCKING

        override suspend fun run(context: StartupTaskContext) {
            LogSDK.init(context.application)
        }
    },
    object : StartupTask() {
        override val id = "crash_config"
        override val dependencies = setOf("log_bootstrap")
        override val dispatcher = StartupDispatcher.IO
        override val stage = StartupStage.BLOCKING

        override suspend fun run(context: StartupTaskContext) {
            CrashSDK.init(context.application)
        }
    },
    object : StartupTask() {
        override val id = "analytics_warmup"
        override val dependencies = setOf("crash_config")
        override val dispatcher = StartupDispatcher.CPU
        override val stage = StartupStage.NON_BLOCKING

        override suspend fun run(context: StartupTaskContext) {
            AnalyticsSDK.warmup(context.application)
        }
    },
    object : StartupTask() {
        override val id = "idle_preload"
        override val dependencies = setOf("analytics_warmup")
        override val dispatcher = StartupDispatcher.IO
        override val stage = StartupStage.IDLE

        override suspend fun run(context: StartupTaskContext) {
            PreloadManager.preload()
        }
    },
)
```

### 2. 构建并启动

```kotlin
// Application.onCreate()
val session = StartupRuntime.Builder(application)
    .mainThreadFrameBudgetMs(16L)
    .addTasks(tasks)
    .build()
    .start()
```

### 3. 等待关键任务

```kotlin
val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

// 等待 BLOCKING 任务完成 → 退出 SplashScreen
scope.launch {
    val result = session.awaitCritical(4_000L)
    // result.completed == true 表示全部完成
    // result.timedOutTaskIds 列出超时的任务
}

// 等待 BLOCKING + NON_BLOCKING 任务完成 → reportFullyDrawn()
scope.launch {
    val result = session.awaitFullDrawnReady(8_000L)
    if (result.completed) {
        activity.reportFullyDrawn()
    }
}
```

### 4. 启动 IDLE 任务

```kotlin
// reportFullyDrawn() 之后，等主线程空闲再开闸
Looper.myQueue().addIdleHandler {
    session.enableIdleDrain()
    scope.launch {
        session.awaitIdle(8_000L)
    }
    false
}
```

### 5. 按需触发 ON_DEMAND 任务

```kotlin
// 用户点击"分享"按钮时
session.triggerOnDemand("share_sdk")
```

## 依赖图示例

以下是一个典型的启动任务 DAG：

```
log_bootstrap (BLOCKING, MAIN, 24ms)
  ├──► crash_config (BLOCKING, IO, 420ms)
  │      └──► analytics_warmup (NON_BLOCKING, CPU, 260ms)
  ├──► cache_feed (BLOCKING, IO, 320ms)
  │      ├──► image_pipeline (NON_BLOCKING, IO, 240ms)
  │      └──► fresh_feed (NON_BLOCKING, IO, 420ms)
  │             └──► idle_preload (IDLE, IO, 220ms)
  ├──► compose_seed (BLOCKING, MAIN, 48ms)
  └──► strict_mode_audit (NON_BLOCKING, CPU, 140ms)
```

串行执行总耗时：所有任务耗时之和 ≈ 2092ms

DAG 并行调度：关键路径 `log_bootstrap(24) → crash_config(420) → analytics_warmup(260)` = 704ms，其他任务在此期间并行完成。

## Trace 集成

每个任务执行时自动包裹 `Trace.beginSection("startup:${id}")` / `Trace.endSection()`。使用 Perfetto 抓取 trace 后，可以在主线程和各工作线程的泳道中看到每个任务的精确耗时和调度时序。

配合 Macrobenchmark 的 `TraceSectionMetric`，可以在 CI 中自动采集每个任务的耗时：

```kotlin
metrics = listOf(
    StartupTimingMetric(),
    TraceSectionMetric("startup:log_bootstrap"),
    TraceSectionMetric("startup:crash_config"),
    TraceSectionMetric("startup:analytics_warmup"),
)
```
