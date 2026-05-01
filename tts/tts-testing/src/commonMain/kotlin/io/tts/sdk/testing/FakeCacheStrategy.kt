package io.tts.sdk.testing

import io.tts.sdk.core.CacheFileInfo
import io.tts.sdk.core.TtsCacheStrategy
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FakeCacheStrategy(
    private val filesToEvict: (List<CacheFileInfo>) -> List<CacheFileInfo> = { emptyList() },
) : TtsCacheStrategy {

    data class Invocation(val files: List<CacheFileInfo>, val totalSize: Long, val maxSizeBytes: Long)

    private val _mutex = Mutex()
    private val _invocations = mutableListOf<Invocation>()

    suspend fun invocations(): List<Invocation> = _mutex.withLock { _invocations.toList() }

    override fun selectFilesToEvict(
        files: List<CacheFileInfo>,
        totalSize: Long,
        maxSizeBytes: Long,
    ): List<CacheFileInfo> {
        // Note: selectFilesToEvict is not suspend, so we use tryLock
        if (_mutex.tryLock()) {
            try {
                _invocations.add(Invocation(files, totalSize, maxSizeBytes))
            } finally {
                _mutex.unlock()
            }
        }
        return filesToEvict(files)
    }
}
