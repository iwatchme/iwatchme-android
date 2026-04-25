package com.iwatchme.startupruntime.dispatch

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class MainThreadMicroBatcher(
    private val frameBudgetMs: Long,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + StartupExecutors.main)
    private val queue = ConcurrentLinkedQueue<suspend (Int) -> Unit>()
    private val draining = AtomicBoolean(false)
    private val batchCounter = AtomicInteger(0)

    private companion object {
        const val TAG = "StartupRuntime"
    }

    fun submit(task: suspend (Int) -> Unit) {
        queue.add(task)
        scheduleDrain()
    }

    private fun scheduleDrain() {
        if (draining.compareAndSet(false, true)) {
            handler.post {
                scope.launch {
                    drain()
                }
            }
        }
    }

    private suspend fun drain() {
        val batchId = batchCounter.incrementAndGet()
        val batchStart = SystemClock.uptimeMillis()
        Log.d(TAG, "main batch start id=$batchId queued=${queue.size} budget=${frameBudgetMs}ms")
        while (true) {
            val next = queue.poll() ?: break
            next(batchId)
            if (SystemClock.uptimeMillis() - batchStart >= frameBudgetMs) {
                break
            }
        }
        draining.set(false)
        Log.d(TAG, "main batch finish id=$batchId elapsed=${SystemClock.uptimeMillis() - batchStart}ms remaining=${queue.size}")
        if (queue.isNotEmpty()) {
            scheduleDrain()
        }
    }
}
