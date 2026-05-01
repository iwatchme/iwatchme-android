package io.ai.sdk.translation

import io.ai.sdk.core.ioDispatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TranslationEngine private constructor(
    private val sdk: ITranslationSdk,
    private val scope: CoroutineScope,
) {
    suspend fun translate(text: String, sourceLang: String, targetLang: String): TranslationResult {
        val params = TranslationParams(text, sourceLang, targetLang)
        return try {
            val translated = sdk.translate(params)
            TranslationResult(text, translated, sourceLang, targetLang)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TranslationResult(text, "", sourceLang, targetLang, e)
        }
    }

    suspend fun translateBatch(
        items: List<TranslationParams>,
        onProgress: ((Int, Int) -> Unit)? = null,
    ): List<TranslationResult> {
        if (items.isEmpty()) return emptyList()
        val total = items.size
        val mutex = Mutex()
        var completed = 0
        return coroutineScope {
            items.map { params ->
                async(ioDispatcher) {
                    val result = translate(params.text, params.sourceLang, params.targetLang)
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
        private var sdk: ITranslationSdk? = null
        private var coroutineScope: CoroutineScope? = null

        fun sdk(sdk: ITranslationSdk): Builder { this.sdk = sdk; return this }
        fun coroutineScope(scope: CoroutineScope): Builder { this.coroutineScope = scope; return this }

        fun build(): TranslationEngine {
            val translationSdk = requireNotNull(sdk) { "ITranslationSdk must be set" }
            val scope = coroutineScope ?: CoroutineScope(SupervisorJob() + ioDispatcher)
            return TranslationEngine(translationSdk, scope)
        }
    }
}
