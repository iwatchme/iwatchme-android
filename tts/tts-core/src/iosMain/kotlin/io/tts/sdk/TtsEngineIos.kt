package io.tts.sdk

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion

/**
 * iOS-friendly wrapper around [TtsEngine] that replaces
 * Kotlin Flow / suspend with callback-based APIs.
 *
 * Swift 侧通过此类调用 TTS 功能，再用 Combine 封装即可。
 */
class TtsEngineIos(private val engine: TtsEngine) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var previewJob: Job? = null

    /**
     * 流式预览 — 每个 PCM 片段通过 [onEvent] 回调，结束/出错也通过回调通知。
     * 返回的 [Cancellable] 可用于取消。
     */
    fun preview(
        text: String,
        source: Int,
        voice: TtsVoiceParams,
        onEvent: (TtsEvent) -> Unit,
    ): Cancellable {
        previewJob?.cancel()
        val job = scope.launch {
            engine.preview(text, source, voice)
                .catch { e ->
                    onEvent(TtsEvent.Error(-1, e.message))
                }
                .collect { event ->
                    onEvent(event)
                }
        }
        previewJob = job
        return Cancellable { job.cancel() }
    }

    /**
     * 批量合成 — 完成后通过 [onComplete] 回调返回结果列表。
     * [onProgress] 可选，报告进度。
     */
    fun generate(
        items: List<TtsItem>,
        voice: TtsVoiceParams,
        onProgress: ((Int, Int) -> Unit)? = null,
        onComplete: (List<TtsGenerateResult>) -> Unit,
        onError: (String) -> Unit,
    ): Cancellable {
        val job = scope.launch {
            try {
                val results = engine.generate(items, voice, onProgress)
                onComplete(results)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.message ?: "Unknown error")
            }
        }
        return Cancellable { job.cancel() }
    }

    fun cancel() {
        previewJob?.cancel()
        engine.cancel()
    }

    fun close() {
        scope.launch {
            engine.close()
        }
        scope.cancel()
    }
}

/**
 * Swift 可调用的取消句柄。
 */
class Cancellable(private val onCancel: () -> Unit) {
    fun cancel() = onCancel()
}
