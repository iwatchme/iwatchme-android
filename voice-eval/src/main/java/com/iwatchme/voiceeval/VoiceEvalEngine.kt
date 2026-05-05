package com.iwatchme.voiceeval

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.iwatchme.voiceeval.api.AudioFormatSpec
import com.iwatchme.voiceeval.api.EvalError
import com.iwatchme.voiceeval.api.EvalRequest
import com.iwatchme.voiceeval.api.EvalResult
import com.iwatchme.voiceeval.api.EvalState
import com.iwatchme.voiceeval.api.ResultSource
import com.iwatchme.voiceeval.api.SilenceLevel
import com.iwatchme.voiceeval.encoder.AudioEncoder
import com.iwatchme.voiceeval.encoder.WavEncoder
import com.iwatchme.voiceeval.internal.ChunkSlicer
import com.iwatchme.voiceeval.internal.DefaultScoreFactory
import com.iwatchme.voiceeval.recorder.AudioCapture
import com.iwatchme.voiceeval.recorder.DbCalculator
import com.iwatchme.voiceeval.recorder.SilenceDetector
import com.iwatchme.voiceeval.scoring.AudioChunk
import com.iwatchme.voiceeval.scoring.MockVoiceScorer
import com.iwatchme.voiceeval.scoring.ScoringOutcome
import com.iwatchme.voiceeval.scoring.VoiceScorer
import com.iwatchme.voiceeval.upload.AudioUploader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * voice-eval 模块的对外入口。通过 [Builder] 创建，可以跨多轮评测重复使用；
 * 但同一时刻只能跑一轮（状态机会强制这个约束）。
 *
 * 单轮架构：
 *
 *  ```
 *  AudioCapture ──PcmChunk──► WavEncoder ──bytes──► ChunkSlicer ──AudioChunk──► VoiceScorer
 *                      │                                  │
 *                      ├──► DbCalculator ──► EvalState.Recording
 *                      └──► SilenceDetector ──► 自动停止 / auto-stop
 *
 *                                                结束后 ──► AudioUploader（尽力而为）
 *  ```
 *
 * 上图右侧四个组件都是可插拔策略，见 [Builder]。
 * 单测 / 演示可以把真实的腾讯 / 七牛实现换成 [MockVoiceScorer]
 * 与本地 Mock 上传器，无需改动引擎本体。
 *
 * Public entry point of the voice-eval module. Created via [Builder] and
 * reusable across many evaluation rounds — but only one round may run at
 * a time per engine instance (the state machine enforces this).
 *
 * Architecture (one round):
 *
 *  ```
 *  AudioCapture ──PcmChunk──► WavEncoder ──bytes──► ChunkSlicer ──AudioChunk──► VoiceScorer
 *                      │                                  │
 *                      ├──► DbCalculator ──► EvalState.Recording
 *                      └──► SilenceDetector ──► auto-stop
 *
 *                                                on completion ──► AudioUploader (best-effort)
 *  ```
 *
 * All four components on the right side of that diagram are pluggable
 * strategies — see [Builder]. Tests and demos can swap real
 * Tencent/QiNiu integrations for [MockVoiceScorer] and a local mock
 * uploader without touching the engine.
 */
class VoiceEvalEngine private constructor(
    private val context: Context,
    private val format: AudioFormatSpec,
    private val encoderFactory: () -> AudioEncoder,
    private val scorer: VoiceScorer,
    private val uploader: AudioUploader?,
    private val chunkSizeBytes: Int,
    private val silenceThresholdDb: Int,
    private val silenceQuietForMs: Long,
    private val silenceAutoStopAfterMs: Long,
    private val scoringTimeoutMs: Long,
    private val outputDir: File,
) {

    // 引擎内部状态机的两个相位：空闲 / 运行中。
    // Internal two-phase state machine: idle vs. running.
    private enum class Phase { IDLE, RUNNING }

    // 当前相位；用于阻止并发评测（必须用 CAS 切换）。
    // Current phase; flipped with CAS to forbid concurrent evaluations on the same engine.
    private val phase = AtomicReference(Phase.IDLE)

    // 「请求停止」信号；外部 stop() 会 complete 它，让录音循环退出。
    // "Stop requested" signal; complete()'d by stop() so the capture loop exits cleanly.
    private val stopSignal = AtomicReference<CompletableDeferred<Unit>?>(null)

    /**
     * 跑一轮评测。返回的是一条冷 Flow：
     *
     *  - 被 collect 的瞬间才会启动麦克风。
     *  - 录音过程中持续发射 [EvalState.Recording]。
     *  - 终态恰好发射一次 [EvalState.Completed] 或 [EvalState.Failed]。
     *
     * 取消 collect 协程或调用 [stop] 都能让录音干净结束。
     * 在已有评测进行时再次调用 [evaluate] 会直接发出 Failed —— 请先 [stop] 或等其结束。
     *
     * 实现细节：
     *
     *  - 这里用 [channelFlow] 而非普通 `flow {}`，因为同时存在两个生产者：
     *    录音循环往外发 Recording 状态，打分任务在另一个 launch 里等结果。
     *    普通 `flow {}` 不允许在 builder 体外调用 emit。
     *  - 通过 `MutableSharedFlow`（replay=0）把编码器字节桥接给打分器；
     *    `extraBufferCapacity` 是当打分器消费速度慢于麦克风时的背压缓冲。
     *
     * Run one evaluation round. The returned cold flow:
     *
     *  - Starts the mic when collected.
     *  - Emits [EvalState.Recording] continuously while audio is captured.
     *  - Emits exactly one terminal [EvalState.Completed] or [EvalState.Failed].
     *
     * Cancelling the collecting coroutine, or calling [stop], ends the
     * recording cleanly. Calling [evaluate] while another round is in
     * flight emits a Failed state — call [stop] first or wait for completion.
     *
     * Implementation notes:
     *
     *  - We use [channelFlow] (not plain `flow`) because we have two
     *    concurrent producers — the capture loop emits Recording states
     *    while the scorer task awaits its outcome on a separate launch.
     *    `flow { }` would forbid emit() outside the builder's body.
     *  - `MutableSharedFlow` (replay = 0) bridges encoder bytes to the
     *    scorer. extraBufferCapacity acts as a backpressure cushion when
     *    the scorer is slower than the mic.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun evaluate(request: EvalRequest): Flow<EvalState> = channelFlow {
        if (!phase.compareAndSet(Phase.IDLE, Phase.RUNNING)) {
            send(EvalState.Failed(EvalError.IoFailure(IllegalStateException("engine busy"))))
            return@channelFlow
        }
        if (!hasMicPermission()) {
            phase.set(Phase.IDLE)
            send(EvalState.Failed(EvalError.PermissionDenied()))
            return@channelFlow
        }

        val stop = CompletableDeferred<Unit>()
        stopSignal.set(stop)

        try {
            send(EvalState.Preparing)

            val encoder = encoderFactory()
            val outputFile = File(
                outputDir,
                "rec_${request.id}_${System.currentTimeMillis()}.${encoder.fileExtension}",
            )
            encoder.open(format, outputFile)

            val slicer = ChunkSlicer(chunkSize = chunkSizeBytes)
            val silence = SilenceDetector(
                thresholdDb = silenceThresholdDb,
                quietForMs = silenceQuietForMs,
                autoStopAfterMs = silenceAutoStopAfterMs,
            )
            val chunkBus = MutableSharedFlow<AudioChunk>(
                replay = 0,
                extraBufferCapacity = 64,
                onBufferOverflow = BufferOverflow.SUSPEND,
            )

            // 打分任务与录音并发执行。这里用 async 包一层，
            // 这样录音结束后外层 Flow 可以 await 它的结果，并在外面统一应用
            // 「超时 / 兜底」规则。
            // The scorer runs concurrently with capture. We wrap its result in
            // an Async so the outer flow can await + apply timeout/fallback rules
            // after capture finishes.
            val scoringTask = async {
                val outcome = withTimeoutOrNull(scoringTimeoutMs) {
                    try {
                        scorer.score(request, chunkBus.asSharedFlow().untilEnd())
                    } catch (ce: CancellationException) {
                        // 必须把 CancellationException 透传出去，
                        // 否则 withTimeoutOrNull 与结构化并发都看不到取消信号。
                        // Must propagate so withTimeoutOrNull / structured concurrency
                        // see the cancellation; never swallow CE.
                        throw ce
                    } catch (t: Throwable) {
                        // 哨兵值：引擎把「负分」当作「打分器爆炸了，走默认兜底」的信号。
                        // Sentinel value — engine treats negative score as "scorer
                        // exploded, fall back to default".
                        ScoringOutcome(-1, emptyList())
                    }
                }
                ScoringTaskResult(
                    outcome = outcome,
                    timedOut = outcome == null,
                )
            }

            var startMs = -1L
            var endMs = -1L
            try {
                AudioCapture(format).stream()
                    .transformWhile { chunk ->
                        if (startMs == -1L) startMs = System.currentTimeMillis()
                        val now = System.currentTimeMillis()
                        val db = DbCalculator.compute(chunk.data, chunk.length)
                        emit(Triple(chunk, db, now - startMs))
                        !stop.isCompleted
                    }
                    .collect { (chunk, db, elapsed) ->
                        send(EvalState.Recording(currentDb = db, elapsedMs = elapsed))

                        val encoded = encoder.feed(chunk.data, chunk.length)
                        slicer.feed(encoded).forEach { chunkBus.emit(it) }

                        silence.observe(db, System.currentTimeMillis())?.let { signal ->
                            send(EvalState.SilenceHint(signal))
                            if (signal == SilenceLevel.AUTO_STOP) stop.complete(Unit)
                        }
                    }
            } finally {
                endMs = System.currentTimeMillis()
                val trailing = runCatching { encoder.finish() }.getOrDefault(ByteArray(0))
                runCatching { encoder.close() }
                // 即使录音被取消 / 异常退出，也必须发出「终止 chunk」让打分 Flow 收敛；
                // 否则打分器会永远等不到 isEnd 而卡死。
                // Always emit the terminating chunk so the scorer flow completes
                // — even on cancellation or capture errors. Without this, the
                // scorer would block forever waiting for isEnd.
                runCatching { chunkBus.emit(slicer.finish(trailing)) }
            }

            send(EvalState.Scoring)

            val taskResult = scoringTask.await()
            val outcome: ScoringOutcome
            val source: ResultSource
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

            val uploadedUrl = uploader?.let { up ->
                runCatching {
                    up.upload(outputFile, key = "voice-eval/${request.id}/${outputFile.name}")
                }.getOrNull()
            }

            val durationMs =
                if (startMs == -1L) 0
                else (endMs - startMs).coerceAtLeast(0)

            send(
                EvalState.Completed(
                    EvalResult(
                        request = request,
                        overallScore = outcome.overallScore,
                        words = outcome.words,
                        durationMs = durationMs,
                        localPath = outputFile.absolutePath,
                        uploadedUrl = uploadedUrl,
                        source = source,
                    ),
                ),
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            send(EvalState.Failed(t.toEvalError()))
        } finally {
            stopSignal.set(null)
            phase.set(Phase.IDLE)
        }
    }

    /**
     * 请求正在进行中的评测尽快收尾。幂等：空闲时调用为空操作。
     *
     * Asks the in-flight evaluation to wrap up. Idempotent. No-op if idle.
     */
    fun stop() {
        stopSignal.get()?.complete(Unit)
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private fun Throwable.toEvalError(): EvalError = when (this) {
        is EvalError -> this
        else -> EvalError.IoFailure(this)
    }

    /**
     * 把 chunk 总线（SharedFlow，原本永不结束）改造成「看到 isEnd=true 就结束」的 Flow，
     * 这正是 [VoiceScorer] 期望的流形态。
     *
     * Re-shape the chunk bus into a flow that completes after observing
     * the chunk with `isEnd=true`, which is what [VoiceScorer] expects.
     */
    private fun SharedFlow<AudioChunk>.untilEnd(): Flow<AudioChunk> = flow {
        this@untilEnd.collect { chunk ->
            emit(chunk)
            if (chunk.isEnd) return@collect
        }
    }

    private data class ScoringTaskResult(
        val outcome: ScoringOutcome?,
        val timedOut: Boolean,
    )

    // ---------------------------------------------------------------------
    // Builder
    // ---------------------------------------------------------------------

    /**
     * 链式（Fluent）配置器。所有可调参数都给了「接近生产」的默认值，
     * 所以最小调用形如：
     *
     *   `VoiceEvalEngine.Builder(ctx).build()`
     *
     * 就能拿到一个 Mock 实现下可用的引擎，足以跑 demo 和单测。
     *
     * Fluent configuration. All knobs default to "production-ish" values,
     * so a minimal call site
     *
     *   `VoiceEvalEngine.Builder(ctx).build()`
     *
     * already yields a working mock-backed engine for demos and tests.
     */
    class Builder(private val context: Context) {

        private var format: AudioFormatSpec = AudioFormatSpec()
        private var encoderFactory: () -> AudioEncoder = ::WavEncoder
        private var scorer: VoiceScorer = MockVoiceScorer()
        private var uploader: AudioUploader? = null
        private var chunkSizeBytes: Int = 4 * 1024
        private var silenceThresholdDb: Int = 55
        private var silenceQuietForMs: Long = 2_500
        private var silenceAutoStopAfterMs: Long = 5_000
        private var scoringTimeoutMs: Long = 5_000
        private var outputDir: File = File(context.filesDir, "voice-eval/recordings")

        fun audioFormat(spec: AudioFormatSpec): Builder = apply { this.format = spec }

        fun encoder(factory: () -> AudioEncoder): Builder = apply {
            this.encoderFactory = factory
        }

        fun scorer(scorer: VoiceScorer): Builder = apply { this.scorer = scorer }

        fun uploader(uploader: AudioUploader?): Builder = apply { this.uploader = uploader }

        fun chunkSize(bytes: Int): Builder = apply {
            require(bytes >= 1024) { "chunkSize must be >= 1024 bytes" }
            this.chunkSizeBytes = bytes
        }

        fun silenceAutoStop(
            thresholdDb: Int = 55,
            quietForMs: Long = 2_500,
            triggerAfterMs: Long = 5_000,
        ): Builder = apply {
            this.silenceThresholdDb = thresholdDb
            this.silenceQuietForMs = quietForMs
            this.silenceAutoStopAfterMs = triggerAfterMs
        }

        fun scoringTimeoutMs(ms: Long): Builder = apply { this.scoringTimeoutMs = ms }

        fun outputDir(dir: File): Builder = apply { this.outputDir = dir }

        fun build(): VoiceEvalEngine {
            outputDir.mkdirs()
            return VoiceEvalEngine(
                context = context.applicationContext,
                format = format,
                encoderFactory = encoderFactory,
                scorer = scorer,
                uploader = uploader,
                chunkSizeBytes = chunkSizeBytes,
                silenceThresholdDb = silenceThresholdDb,
                silenceQuietForMs = silenceQuietForMs,
                silenceAutoStopAfterMs = silenceAutoStopAfterMs,
                scoringTimeoutMs = scoringTimeoutMs,
                outputDir = outputDir,
            )
        }
    }
}
