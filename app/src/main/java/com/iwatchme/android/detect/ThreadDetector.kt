package com.iwatchme.android.detect

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

data class ThreadEvent(
    val id: Long,
    val type: String,
    val callerClass: String,
    val detail: String,
    val threadName: String,
    val timestamp: Long,
    val stackTrace: String,
)

object ThreadDetector {
    private const val TAG = "ThreadDetector"
    private val detecting = ThreadLocal<Boolean>()
    private val idGenerator = AtomicLong(0)
    private val _events = CopyOnWriteArrayList<ThreadEvent>()

    val events: List<ThreadEvent> get() = _events

    fun clear() {
        _events.clear()
    }

    @JvmStatic
    fun onThreadInit(callerClass: String, descriptor: String) {
        if (detecting.get() == true) return
        detecting.set(true)
        try {
            val currentThread = Thread.currentThread().name
            val stackTrace = Throwable().stackTrace
                .drop(1)
                .take(5)
                .joinToString("\n    ") { it.toString() }
            _events.add(
                ThreadEvent(
                    id = idGenerator.getAndIncrement(),
                    type = "Thread",
                    callerClass = callerClass.replace('/', '.'),
                    detail = descriptor,
                    threadName = currentThread,
                    timestamp = System.currentTimeMillis(),
                    stackTrace = stackTrace,
                )
            )
            Log.d(TAG, "Thread.<init> | caller=$callerClass | desc=$descriptor | thread=$currentThread")
        } finally {
            detecting.set(false)
        }
    }

    @JvmStatic
    fun onExecutorInit(callerClass: String, descriptor: String) {
        if (detecting.get() == true) return
        detecting.set(true)
        try {
            val currentThread = Thread.currentThread().name
            val stackTrace = Throwable().stackTrace
                .drop(1)
                .take(5)
                .joinToString("\n    ") { it.toString() }
            _events.add(
                ThreadEvent(
                    id = idGenerator.getAndIncrement(),
                    type = "Executor",
                    callerClass = callerClass.replace('/', '.'),
                    detail = descriptor,
                    threadName = currentThread,
                    timestamp = System.currentTimeMillis(),
                    stackTrace = stackTrace,
                )
            )
            Log.d(TAG, "ThreadPoolExecutor.<init> | caller=$callerClass | desc=$descriptor | thread=$currentThread")
        } finally {
            detecting.set(false)
        }
    }

    @JvmStatic
    fun onExecutorFactoryCall(callerClass: String, methodName: String) {
        if (detecting.get() == true) return
        detecting.set(true)
        try {
            val currentThread = Thread.currentThread().name
            val stackTrace = Throwable().stackTrace
                .drop(1)
                .take(5)
                .joinToString("\n    ") { it.toString() }
            _events.add(
                ThreadEvent(
                    id = idGenerator.getAndIncrement(),
                    type = "Factory",
                    callerClass = callerClass.replace('/', '.'),
                    detail = methodName,
                    threadName = currentThread,
                    timestamp = System.currentTimeMillis(),
                    stackTrace = stackTrace,
                )
            )
            Log.d(TAG, "Executors.$methodName | caller=$callerClass | thread=$currentThread")
        } finally {
            detecting.set(false)
        }
    }
}
