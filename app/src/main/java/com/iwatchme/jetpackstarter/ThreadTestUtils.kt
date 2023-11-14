package com.iwatchme.jetpackstarter

import android.util.Log
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class ThreadTestUtils {


    fun test() {
        Thread {
            Log.e("Frank", Thread.currentThread().name)
        }.start()
    }


    fun test2() {
        val executor  = ThreadPoolExecutor(0, Integer.MAX_VALUE,
            60L, TimeUnit.SECONDS,
            SynchronousQueue<Runnable>()
        )

        executor.submit {
            Log.e("Frank", "2: "+ Thread.currentThread().name)
        }
    }
}