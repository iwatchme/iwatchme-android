package io.ai.sdk.testing

import io.ai.sdk.internal.AiSdkException
import io.ai.sdk.tts.ITtsSdk
import io.ai.sdk.tts.TtsSdkParams
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

class FakeTtsSdk(
    private val behavior: AiSdkScenario = AiSdkScenario.Success,
    private val handledSources: Set<Int> = setOf(1, 2, 3),
    private val delay: Duration = Duration.ZERO,
    private val fakeFilePath: String = "/fake/tts/output.mp3",
    private val streamChunks: List<ByteArray> = emptyList(),
) : ITtsSdk {

    private val _initializeCallCount = atomic(0)
    private val _releaseCallCount = atomic(0)
    private val _synthesizeMutex = Mutex()
    private val _synthesizeCalls = mutableListOf<TtsSdkParams>()

    val initializeCallCount: Int get() = _initializeCallCount.value
    val releaseCallCount: Int get() = _releaseCallCount.value
    suspend fun synthesizeCalls(): List<TtsSdkParams> = _synthesizeMutex.withLock { _synthesizeCalls.toList() }

    override fun handles(source: Int) = source in handledSources

    override suspend fun initialize() {
        _initializeCallCount.incrementAndGet()
    }

    override fun release() {
        _releaseCallCount.incrementAndGet()
    }

    override suspend fun synthesize(params: TtsSdkParams): String {
        _synthesizeMutex.withLock { _synthesizeCalls.add(params) }
        if (delay > Duration.ZERO) kotlinx.coroutines.delay(delay.inWholeMilliseconds)
        return when (behavior) {
            AiSdkScenario.Success -> fakeFilePath
            AiSdkScenario.Error -> throw AiSdkException(retCode = -1)
            AiSdkScenario.NetworkError -> throw AiSdkException(retCode = -2, "Network error")
            AiSdkScenario.HangForever -> suspendCancellableCoroutine { /* never resumes */ }
        }
    }

    override fun synthesizeStreaming(params: TtsSdkParams): Flow<ByteArray> = flow {
        if (delay > Duration.ZERO) kotlinx.coroutines.delay(delay.inWholeMilliseconds)
        when (behavior) {
            AiSdkScenario.Error -> throw AiSdkException(retCode = -1)
            AiSdkScenario.NetworkError -> throw AiSdkException(retCode = -2, "Network error")
            AiSdkScenario.HangForever -> suspendCancellableCoroutine<Nothing> { /* never resumes */ }
            AiSdkScenario.Success -> streamChunks.forEach { emit(it) }
        }
    }
}
