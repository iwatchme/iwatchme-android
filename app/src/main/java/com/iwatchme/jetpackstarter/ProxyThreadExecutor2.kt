package com.iwatchme.jetpackstarter

import android.util.Log
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.*
import java.util.concurrent.atomic.AtomicInteger

open class ProxyThreadExecutor2 : ThreadPoolExecutor {

//    private val cpuThreadPoolExecutor = ThreadPoolExecutor(
//        getCoreNum(),
//        getCoreNum(),
//        0L,
//        MILLISECONDS,
//        LinkedBlockingQueue<Runnable>()
//    )

    private val atomicBoolean = AtomicInteger(0)

    private val ioThreadPoolExecutor = Executors.newCachedThreadPool(ThreadFactory {
        Thread(it, "ioThreadPoolExecutor ${atomicBoolean.getAndIncrement()}")
    })


    constructor(
        corePoolSize: Int,
        maximumPoolSize: Int,
        keepAliveTime: Long,
        unit: TimeUnit?,
        workQueue: BlockingQueue<Runnable>?,
        className: String?,
    ) : super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue) {
        Log.e("Frank" ,"init: $className $corePoolSize")
    }


    override fun <T : Any?> submit(p0: Callable<T>): Future<T> {
        return ioThreadPoolExecutor.submit(p0)
    }

    override fun execute(p0: Runnable) {
        return ioThreadPoolExecutor.execute(p0)
    }


}