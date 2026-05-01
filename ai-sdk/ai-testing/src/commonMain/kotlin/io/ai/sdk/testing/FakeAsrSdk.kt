package io.ai.sdk.testing

import io.ai.sdk.asr.AsrParams
import io.ai.sdk.asr.AsrResult
import io.ai.sdk.asr.IAsrSdk
import io.ai.sdk.internal.AiSdkException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

class FakeAsrSdk(
    private val behavior: AiSdkScenario = AiSdkScenario.Success,
    private val delay: Duration = Duration.ZERO,
    private val fakeResult: AsrResult = AsrResult(text = "recognized text", wordCount = 2),
) : IAsrSdk {

    private val _mutex = Mutex()
    private val _recognizeCalls = mutableListOf<AsrParams>()
    private var _released = false

    suspend fun recognizeCalls(): List<AsrParams> = _mutex.withLock { _recognizeCalls.toList() }
    val isReleased: Boolean get() = _released

    override suspend fun recognize(params: AsrParams): AsrResult {
        _mutex.withLock { _recognizeCalls.add(params) }
        if (delay > Duration.ZERO) kotlinx.coroutines.delay(delay.inWholeMilliseconds)
        return when (behavior) {
            AiSdkScenario.Success -> fakeResult
            AiSdkScenario.Error -> throw AiSdkException(retCode = -1)
            AiSdkScenario.NetworkError -> throw AiSdkException(retCode = -2, "Network error")
            AiSdkScenario.HangForever -> suspendCancellableCoroutine { /* never resumes */ }
        }
    }

    override fun release() {
        _released = true
    }
}
