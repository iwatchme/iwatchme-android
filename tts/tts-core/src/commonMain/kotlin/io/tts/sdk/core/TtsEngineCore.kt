package io.tts.sdk.core

import io.tts.sdk.internal.TtsSynthesisException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TtsEngineCore(
    private val pools: Map<Int, TtsSdkPool>,
    private val scope: CoroutineScope,
    private val cache: TtsCache? = null,
) {
    private fun poolFor(source: Int): TtsSdkPool =
        pools[source] ?: throw TtsSynthesisException(-1, "No SDK for source $source")

    suspend fun synthesizeOne(params: TtsSdkParams): String {
        val key = cache?.let { TtsCacheKey.from(params) }

        if (cache != null && key != null) {
            val cached = cache.get(key)
            if (cached != null) return cached
        }

        val path = poolFor(params.source).withSdk { sdk ->
            sdk.synthesize(params)
        }

        if (cache != null && key != null) {
            cache.put(key, path)
        }

        return path
    }

    fun streamOne(params: TtsSdkParams): Flow<ByteArray> = flow {
        poolFor(params.source).withSdk { sdk ->
            sdk.synthesizeStreaming(params).collect { emit(it) }
        }
    }

    suspend fun generateBatch(
        paramsList: List<TtsSdkParams>,
        onProgress: ((Int, Int) -> Unit)?,
    ): List<Result<String>> {
        val total = paramsList.size
        val mutex = Mutex()
        var completed = 0

        return coroutineScope {
            paramsList.map { params ->
                async(ioDispatcher) {
                    try {
                        val path = synthesizeOne(params)
                        val n = mutex.withLock { ++completed }
                        onProgress?.invoke(n, total)
                        Result.success(path)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
            }.awaitAll()
        }
    }

    fun cancelAll() {
        scope.coroutineContext[Job]?.children?.forEach { it.cancel() }
    }

    suspend fun release() {
        pools.values.forEach { it.release() }
    }
}
