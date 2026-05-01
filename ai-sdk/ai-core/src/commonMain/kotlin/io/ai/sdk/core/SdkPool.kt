package io.ai.sdk.core

import io.ai.sdk.internal.AiSdkException
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SdkPool<T>(
    private val factory: suspend () -> T,
    val maxSize: Int,
    private val initializer: (suspend (T) -> Unit)? = null,
    private val releaser: (suspend (T) -> Unit)? = null,
) {
    private val available = Channel<T>(Channel.UNLIMITED)
    private val _createdCount = atomic(0)
    private val _checkedOutCount = atomic(0)
    private val allMutex = Mutex()
    private val all = mutableListOf<T>()
    private val _closing = atomic(false)

    suspend fun <R> withSdk(block: suspend (T) -> R): R {
        check(!_closing.value) { "Pool is shutting down, no new checkouts allowed" }
        val sdk = acquire()
        _checkedOutCount.incrementAndGet()
        var discarded = false
        return try {
            block(sdk)
        } catch (e: AiSdkException) {
            allMutex.withLock { all.remove(sdk) }
            _createdCount.decrementAndGet()
            discarded = true
            runCatching { releaser?.invoke(sdk) }
            throw e
        } finally {
            if (!discarded && !_closing.value) {
                available.send(sdk)
            }
            _checkedOutCount.decrementAndGet()
        }
    }

    private suspend fun acquire(): T {
        available.tryReceive().getOrNull()?.let { return it }

        while (true) {
            val current = _createdCount.value
            if (current >= maxSize) break
            if (_createdCount.compareAndSet(current, current + 1)) {
                return try {
                    val sdk = factory()
                    initializer?.invoke(sdk)
                    allMutex.withLock { all.add(sdk) }
                    sdk
                } catch (e: Exception) {
                    _createdCount.decrementAndGet()
                    throw e
                }
            }
        }

        return available.receive()
    }

    suspend fun release() {
        _closing.value = true
        available.close()
        while (_checkedOutCount.value > 0) {
            delay(10)
        }
        val snapshot = allMutex.withLock { all.toList().also { all.clear() } }
        snapshot.forEach { runCatching { releaser?.invoke(it) } }
    }

    internal fun createdCount(): Int = _createdCount.value
}
