package io.tts.sdk

import io.tts.sdk.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class TtsEngine private constructor(
    private val core: TtsEngineCore,
    private val cache: TtsCache,
    private val cacheDirPath: String,
    private val scope: CoroutineScope,
) {
    fun preview(text: String, source: Int, voice: TtsVoiceParams): Flow<TtsEvent> = flow {
        val params = TtsSdkParams(
            text = text,
            source = source,
            voiceType = voice.voiceType,
            engineName = voice.engineName,
            volume = voice.volume,
            rate = voice.rate,
            pitch = voice.pitch,
            effect = voice.effect,
            effectValue = voice.effectValue,
            sampleRate = voice.sampleRate,
            encodeType = voice.encodeType,
            cacheDirPath = cacheDirPath,
        )

        try {
            core.streamOne(params).collect { pcm ->
                emit(TtsEvent.AudioChunk(pcm))
            }
            emit(TtsEvent.StreamEnd)
        } catch (e: CancellationException) {
            emit(TtsEvent.Cancelled)
            throw e
        } catch (e: io.tts.sdk.internal.TtsSynthesisException) {
            emit(TtsEvent.Error(e.retCode, e.message))
        } catch (e: Exception) {
            emit(TtsEvent.Error(-1, e.message))
        }
    }

    suspend fun generate(
        items: List<TtsItem>,
        voice: TtsVoiceParams,
        onProgress: ((completed: Int, total: Int) -> Unit)? = null,
    ): List<TtsGenerateResult> {
        if (items.isEmpty()) return emptyList()

        val uniqueItems = items.distinctBy { it.text to it.source }
        val paramsList = uniqueItems.map { item ->
            TtsSdkParams.from(item, voice, item.source, cacheDirPath)
        }

        val results = core.generateBatch(paramsList, onProgress)

        val resultMap = mutableMapOf<Pair<String, Int>, Result<String>>()
        uniqueItems.forEachIndexed { index, uniqueItem ->
            resultMap[uniqueItem.text to uniqueItem.source] = results[index]
        }

        return items.map { item ->
            val result = resultMap[item.text to item.source]!!
            TtsGenerateResult(
                text = item.text,
                source = item.source,
                filePath = result.getOrNull(),
                error = result.exceptionOrNull() as? Exception,
            )
        }
    }

    fun cancel() {
        core.cancelAll()
    }

    suspend fun close() {
        core.release()
        scope.cancel()
    }

    class Builder {
        private val sdkRegistrations = mutableMapOf<Int, SdkRegistration>()
        private var cacheDirPath: String? = null
        private var coroutineScope: CoroutineScope? = null
        private var cacheStrategy: TtsCacheStrategy? = null

        fun sdk(source: Int, factory: suspend () -> ITtsSdk, poolSize: Int = 1): Builder {
            sdkRegistrations[source] = SdkRegistration(factory, poolSize)
            return this
        }

        fun sdk(source: Int, sdk: ITtsSdk): Builder = sdk(source, { sdk }, poolSize = 1)

        fun cacheDirPath(path: String): Builder {
            cacheDirPath = path
            return this
        }

        fun cacheStrategy(strategy: TtsCacheStrategy): Builder {
            cacheStrategy = strategy
            return this
        }

        fun coroutineScope(scope: CoroutineScope): Builder {
            coroutineScope = scope
            return this
        }

        fun build(): TtsEngine {
            val dirPath = requireNotNull(cacheDirPath) { "cacheDirPath must be set" }
            val scope = coroutineScope ?: CoroutineScope(SupervisorJob() + ioDispatcher)
            val pools = sdkRegistrations.map { (source, reg) ->
                source to TtsSdkPool(factory = reg.factory, maxSize = reg.poolSize)
            }.toMap()
            val strategy = cacheStrategy ?: LruCacheStrategy()
            val cache = TtsCache(dirPath, strategy = strategy)
            val core = TtsEngineCore(pools, scope, cache)
            return TtsEngine(core, cache, dirPath, scope)
        }

        private data class SdkRegistration(
            val factory: suspend () -> ITtsSdk,
            val poolSize: Int,
        )
    }
}
