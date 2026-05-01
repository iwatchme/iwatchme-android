package io.ai.sdk.testing

import io.ai.sdk.internal.AiSdkException
import io.ai.sdk.translation.ITranslationSdk
import io.ai.sdk.translation.TranslationParams
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

class FakeTranslationSdk(
    private val behavior: AiSdkScenario = AiSdkScenario.Success,
    private val delay: Duration = Duration.ZERO,
    private val fakeTranslation: String = "translated text",
) : ITranslationSdk {

    private val _mutex = Mutex()
    private val _translateCalls = mutableListOf<TranslationParams>()
    private var _released = false

    suspend fun translateCalls(): List<TranslationParams> = _mutex.withLock { _translateCalls.toList() }
    val isReleased: Boolean get() = _released

    override suspend fun translate(params: TranslationParams): String {
        _mutex.withLock { _translateCalls.add(params) }
        if (delay > Duration.ZERO) kotlinx.coroutines.delay(delay.inWholeMilliseconds)
        return when (behavior) {
            AiSdkScenario.Success -> fakeTranslation
            AiSdkScenario.Error -> throw AiSdkException(retCode = -1)
            AiSdkScenario.NetworkError -> throw AiSdkException(retCode = -2, "Network error")
            AiSdkScenario.HangForever -> suspendCancellableCoroutine { /* never resumes */ }
        }
    }

    override fun release() {
        _released = true
    }
}
