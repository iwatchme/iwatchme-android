package com.iwatchme.startuplauncher

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

object DispatcherExecutor {

    class CustomThreadFactory : ThreadFactory {
        var poolNum = AtomicInteger(1)
        var threadNum = AtomicInteger(1)
        var group: ThreadGroup
        var namePrefix: String

        init {
            group = System.getSecurityManager().run {
                if (this != null) {
                    this.threadGroup
                } else {
                    Thread.currentThread().threadGroup
                }
            }

            namePrefix = "TaskDispatcherPool-" +
                    poolNum.getAndIncrement() +
                    "-Thread-"

        }

        override fun newThread(r: Runnable?): Thread {

            var thread: Thread = Thread(group, r, namePrefix +
                    threadNum.getAndIncrement()
            )

            return thread
        }

    }


     var ioExecutor: ExecutorService

    var cpuExecutor: ThreadPoolExecutor

    private val CPU_COUNT = Runtime.getRuntime().availableProcessors()

    private val CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 5))

    init {
        ioExecutor = Executors.newCachedThreadPool()
        cpuExecutor = ThreadPoolExecutor(
            CORE_POOL_SIZE, CORE_POOL_SIZE, 5, TimeUnit.SECONDS,
                LinkedBlockingQueue<Runnable>(), CustomThreadFactory(), RejectedExecutionHandler { r, executor -> Executors.newCachedThreadPool().execute(r) }
        )
    }



}