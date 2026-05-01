package io.ai.sdk.asr

import io.ai.sdk.core.ioDispatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AsrEngine private constructor(
    private val sdk: IAsrSdk,
    private val scope: CoroutineScope,
) {
    suspend fun recognize(audioData: ByteArray, language: String? = null, contentType: String = "audio/mpeg"): AsrResult {
        val params = AsrParams(audioData, language, contentType)
        return try {
            sdk.recognize(params)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AsrResult(error = e)
        }
    }

    suspend fun recognizeBatch(
        items: List<AsrParams>,
        onProgress: ((Int, Int) -> Unit)? = null,
    ): List<AsrResult> {
        if (items.isEmpty()) return emptyList()
        val total = items.size
        val mutex = Mutex()
        var completed = 0
        return coroutineScope {
            items.map { params ->
                async(ioDispatcher) {
                    val result = try {
                        sdk.recognize(params)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AsrResult(error = e)
                    }
                    val n = mutex.withLock { ++completed }
                    onProgress?.invoke(n, total)
                    result
                }
            }.awaitAll()
        }
    }

    suspend fun close() {
        sdk.release()
        scope.cancel()
    }

    class Builder {
        private var sdk: IAsrSdk? = null
        private var coroutineScope: CoroutineScope? = null

        fun sdk(sdk: IAsrSdk): Builder { this.sdk = sdk; return this }
        fun coroutineScope(scope: CoroutineScope): Builder { this.coroutineScope = scope; return this }

        fun build(): AsrEngine {
            val asrSdk = requireNotNull(sdk) { "IAsrSdk must be set" }
            val scope = coroutineScope ?: CoroutineScope(SupervisorJob() + ioDispatcher)
            return AsrEngine(asrSdk, scope)
        }
    }
}
