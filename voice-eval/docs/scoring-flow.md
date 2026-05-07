# 引擎打分流程深度解析

`VoiceEvalEngine.evaluate()` 是整个模块的「胶水函数」。这篇文档拆解它的并发设计、超时与兜底机制、以及流式上传与整体打分之间的关系。

## 一次评测的完整时序

```
collect() 触发
   │
   ▼
[Preparing]  ── 权限检查、AudioRecord 初始化、文件创建
   │
   ▼
   ┌──────────────────────────────────────────────────────────────┐
   │  录音协程                              打分协程（async 启动）  │
   │  ─────────────                       ──────────────────────   │
   │  AudioCapture → DbCalculator         scorer.score(           │
   │       │             │                    request,            │
   │       │             ▼                    chunkBus.untilEnd() │
   │       │        emit(Recording)        )                      │
   │       ▼                               // 内部按 SOE 协议      │
   │  WavEncoder.feed                      // 一片片喂给后端       │
   │       │                                                       │
   │       ▼                                                       │
   │  ChunkSlicer.feed(4KB)  ─────► chunkBus (MutableSharedFlow)──┘
   │       │
   │       ├─► SilenceDetector.observe → emit(SilenceHint)
   │       │
   │       └─► AUTO_STOP / stop() / collect 取消 ──► 退出循环
   │
   │  ──────────── finally：encoder.finish() + slicer.finish(isEnd=true) ──────────┐
   │                                                                              │
   ▼                                                                              ▼
[Scoring]                                                              （打分协程结束，回填 outcome）
   │
   ▼
应用「超时/兜底」规则 → 选 ResultSource
   │
   ▼
（可选）AudioUploader.upload(...)（best-effort，失败不影响打分）
   │
   ▼
[Completed / Failed]
```

## 三个关键设计选择

### 1. 为什么用 `channelFlow`，不是普通 `flow {}`

录音循环和打分协程是**两个并发生产者**，都要往返回的 Flow 里 `emit` 状态：
- 录音循环 emit `EvalState.Recording`、`EvalState.SilenceHint`、`EvalState.Scoring`；
- 终态由外层在 `await()` 后统一 emit。

普通 `flow {}` 不允许在 builder 体外调用 `emit()`，会抛
`IllegalStateException: Flow invariant is violated`。`channelFlow` 内部用 channel 桥接，
允许多个协程并发 `send()`，正是这种场景的标准选择。

### 2. 为什么用 `MutableSharedFlow` 桥接编码器与打分器

```kotlin
val chunkBus = MutableSharedFlow<AudioChunk>(
    replay = 0,
    extraBufferCapacity = 64,
    onBufferOverflow = BufferOverflow.SUSPEND,
)
```

- `replay = 0`：打分器只关心**当前**录音的 chunk，不需要历史回放。
- `extraBufferCapacity = 64`：当打分器消费速度慢于麦克风时的**背压缓冲**。
  4KB 一片、100ms 网络延迟下，64 片缓冲约等于 8 秒余量，足够吸收抖动。
- `onBufferOverflow = SUSPEND`：缓冲打满时让录音循环挂起，而不是丢帧。
  对评测来说，丢帧 > 等待。

### 3. 为什么 `finally` 里要强制 emit 终止 chunk

```kotlin
} finally {
    val trailing = runCatching { encoder.finish() }.getOrDefault(ByteArray(0))
    runCatching { encoder.close() }
    runCatching { chunkBus.emit(slicer.finish(trailing)) }
}
```

`ChunkSlicer.finish()` 永远返回一个 `isEnd=true` 的 chunk，即使内容为空。
**必须**把它 emit 出去，否则打分器内部 `chunkBus.untilEnd()` 永远等不到 `isEnd`，
打分协程会无限挂起。

无论录音是正常结束、被 `stop()` 触发、被 collect 协程取消、还是抛了异常，
`finally` 都会执行 —— 这是引擎能够清理干净的关键。

## 三层防御：`async` + `withTimeoutOrNull` + `try/catch`

打分子任务这段：

```kotlin
val scoringTask = async {
    val outcome = withTimeoutOrNull(scoringTimeoutMs) {
        try {
            scorer.score(request, chunkBus.asSharedFlow().untilEnd())
        } catch (ce: CancellationException) {
            throw ce               // ① 透传取消信号
        } catch (t: Throwable) {
            ScoringOutcome(-1, emptyList())   // ② 异常 → 哨兵值
        }
    }                              // ③ 超时 → null
    ScoringTaskResult(outcome, timedOut = outcome == null)
}
```

每一层只负责一类失败模式，主流程因此只剩一个干净的 `when`。

### ① `async { ... }` —— 把打分送到后台
和录音循环并发跑，返回 `Deferred`，让外层在录音 `finally` 之后再 `await()` 拿结果。
没这一层就只能「先录完再打分」，丢失流式上传的延迟优势。

### ② `withTimeoutOrNull(scoringTimeoutMs)` —— 超时不抛错
- 普通 `withTimeout` 超时会抛 `TimeoutCancellationException`，把外层一起干掉。
- `withTimeoutOrNull` 超时返回 `null`，让引擎得以**平静地走超时兜底**。

### ③ try-catch 双分支
**`CancellationException` 必须重新抛**：协程协作式取消的载体，吞掉它会导致：
- `withTimeoutOrNull` 收不到取消信号 ⇒ 超时机制失效；
- 外层 `channelFlow` 不知道被取消 ⇒ 资源泄漏。

**其他 `Throwable` 写入哨兵值 `-1`**：业务异常（HTTP 5xx、协议错误、JSON 解析失败……）
不让它们冲出去，而是把负分作为信号传给外层。

## 三种最终落点

执行完整段后，`ScoringTaskResult` 只可能是这三种之一：

| 情形 | `withTimeoutOrNull` | `outcome.overallScore` | 外层处理 |
|---|---|---|---|
| 真实成功 | 非 null | ≥ 0（真实分） | `ResultSource.SCORER` |
| 打分器抛错 | 非 null | `-1`（哨兵） | `ResultSource.DEFAULT_FALLBACK` |
| 打分超时 | `null` | — | `ResultSource.TIMEOUT_FALLBACK` |

外层用 `when` 分支挑兜底分数：

```kotlin
when {
    taskResult.timedOut -> {
        outcome = DefaultScoreFactory.build(request)
        source = ResultSource.TIMEOUT_FALLBACK
    }
    taskResult.outcome != null && taskResult.outcome.overallScore < 0 -> {
        outcome = DefaultScoreFactory.build(request)
        source = ResultSource.DEFAULT_FALLBACK
    }
    else -> {
        outcome = taskResult.outcome!!
        source = ResultSource.SCORER
    }
}
```

`DefaultScoreFactory` 给一个 60–70 的随机分 —— 故意保留随机性，固定 65 会被用户察觉到失败模式并加以利用。

`source` 字段最终透传到 `EvalResult.source`，UI 可以据此决定是否展示「请重试」。

## 流式上传 vs 整体打分

这是一个常见的混淆点：**音频是一片一片流式上传的，但打分结果只在最后一次性返回**。

### 接口契约决定节奏

```kotlin
suspend fun score(
    request: EvalRequest,
    chunks: Flow<AudioChunk>,    // ← 输入：流（多片）
): ScoringOutcome                // ← 输出：单个值（一次）
```

- 输入 `Flow<AudioChunk>`：一片一片来；
- 输出单个 `ScoringOutcome`：流进、整体出。

### 为什么这样设计

| 流式上传 | 整体打分 |
|---|---|
| 边录边传，结束后只剩一帧的网络往返，**总耗时 ≈ 录音时长**，而不是「录音时长 + 上传时长」 | 发音评测要做**音素对齐**（比如判断 "school" 里的 /sk/ 是否清晰），跨多片才能完成，按片打分没业务意义 |
| 网络抖动可以分摊到整段录音，更鲁棒 | UI 上只需要一个「Scoring → Completed」的过渡，逻辑简单 |

### 想做「逐词实时反馈」要怎么改

需要把协议改为流式返回：

```kotlin
interface VoiceScorer {
    fun score(request: EvalRequest, chunks: Flow<AudioChunk>): Flow<PartialScore>
}
```

每识别完一个词就 emit 一次 `PartialScore`，UI 可以实时高亮已读对/读错的词。
但**当前架构是「流入 / 整体出」**，对应主流商用 API 的形态（包括腾讯 SOE）。

详见 [`soe-protocol.md`](soe-protocol.md) 里对真实 SOE 协议形态的解析。
