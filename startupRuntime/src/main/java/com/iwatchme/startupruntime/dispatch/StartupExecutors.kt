package com.iwatchme.startupruntime.dispatch

import com.iwatchme.startupruntime.model.StartupDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal object StartupExecutors {
    private val threadCounter = AtomicInteger(1)

    private fun namedFactory(prefix: String): ThreadFactory = ThreadFactory { runnable ->
        Thread(runnable, "$prefix-${threadCounter.getAndIncrement()}")
    }

    private val ioExecutor = Executors.newCachedThreadPool(namedFactory("startup-io"))

    private val cpuExecutor = ThreadPoolExecutor(
        maxOf(2, Runtime.getRuntime().availableProcessors() / 2),
        maxOf(2, Runtime.getRuntime().availableProcessors()),
        30L,
        TimeUnit.SECONDS,
        LinkedBlockingQueue(),
        namedFactory("startup-cpu"),
    )

    val io: ExecutorCoroutineDispatcher = ioExecutor.asCoroutineDispatcher()

    val cpu: ExecutorCoroutineDispatcher = cpuExecutor.asCoroutineDispatcher()

    val main: MainCoroutineDispatcher = Dispatchers.Main.immediate

    fun dispatcherFor(dispatcher: StartupDispatcher): CoroutineDispatcher {
        return when (dispatcher) {
            StartupDispatcher.MAIN -> main
            StartupDispatcher.IO -> io
            StartupDispatcher.CPU -> cpu
        }
    }
}
