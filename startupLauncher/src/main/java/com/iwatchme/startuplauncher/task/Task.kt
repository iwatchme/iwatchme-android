package com.iwatchme.startuplauncher.task

import com.iwatchme.startuplauncher.DispatcherExecutor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService

open class Task : ITask {

    @Volatile
    var isRunning : Boolean = false

    @Volatile
    var isWaiting : Boolean  = false

    @Volatile
    var isFinished : Boolean = false

    private var countDown: CountDownLatch =
            CountDownLatch(if(dependsOn() == null) 0 else dependsOn()!!.size )


    override fun dependsOn(): List<Class<out Task>>? {
        return null
    }

    override fun run() {
    }

    override fun onlyRunOnMainThread(): Boolean {
       return false
    }

    override fun onlyRunOnMainProcess(): Boolean {
        return  true
    }

    override fun runOn(): ExecutorService {
       return DispatcherExecutor.ioExecutor
    }

    fun waitAllDependsFinish() {
        try {
            countDown.await()
        } catch (e: InterruptedException) {

        }
    }

    fun satisfy() {
        countDown.countDown()
    }
}


fun Task.isNeedWait(): Boolean =
    !onlyRunOnMainThread() && needWait()
