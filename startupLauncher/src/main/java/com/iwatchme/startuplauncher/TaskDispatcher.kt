package com.iwatchme.startuplauncher

import android.app.Application
import com.iwatchme.startuplauncher.sort.TaskSortUtils
import com.iwatchme.startuplauncher.task.Task
import com.iwatchme.startuplauncher.task.isNeedWait
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

class TaskDispatcher {

    var allTask: MutableList<Task> = mutableListOf()

    var clsAllTask: MutableList<Class<out Task>> = mutableListOf()

    var finishAllTask: MutableList<Class<out Task>> = mutableListOf()

    var mainThreadTask: MutableList<Task> = mutableListOf()

    var dependenciesMap: MutableMap<Class<out Task>, MutableList<Task>> = mutableMapOf()

    var waitingTasks: MutableList<Task> = mutableListOf()

    var mWaitingTaskCount: AtomicInteger = AtomicInteger()

    var mFutures: MutableList<Future<*>> = mutableListOf()


    lateinit var countDownLatch: CountDownLatch;


    fun addTask(task: Task): TaskDispatcher {

        allTask.add(task)
        clsAllTask.add(task.javaClass)
        collectDependencies(task)

        if (task.isNeedWait()) {
            waitingTasks.add(task)
            mWaitingTaskCount.getAndIncrement()
        }

        return this
    }


    fun start() {
        if (allTask.size > 0) {
            allTask = TaskSortUtils.getSortResult(allTask, clsAllTask).toMutableList()
            countDownLatch = CountDownLatch(mWaitingTaskCount.get())

            sendAndExecuteAsyncTasks()

            executeMainThreadTasks()
        }
    }

    fun aWait() {

        if (mWaitingTaskCount.get() > 0) {
            countDownLatch.countDown()
        }

    }


    private fun sendAndExecuteAsyncTasks() {
        allTask.forEach {
            if (it.onlyRunOnMainProcess() && !isInMainProcess) {
                markTaskDone(it)
            } else {
                sendRealTask(it)
            }
        }
    }

    private fun executeMainThreadTasks() {
        mainThreadTask.forEach {
            DispathcherRunnable(it, this).run()

        }


    }

    private fun sendRealTask(task: Task) {
        if (task.onlyRunOnMainThread()) {
            mainThreadTask.add(task)
        } else {
            var future = task.runOn().submit(DispathcherRunnable(task, this))
            mFutures.add(future)
        }
    }

    fun satisfyChildren(task: Task) {
        var dependendTasks = dependenciesMap[task.javaClass]
        dependendTasks?.forEach {
            it.satisfy()
        }
    }


    fun markTaskDone(task: Task) {
        if (task.isNeedWait()) {
            finishAllTask.add(task::class.java)
            waitingTasks.remove(task)
            countDownLatch.countDown()
            mWaitingTaskCount.getAndIncrement()

        }

    }

    private fun collectDependencies(task: Task) {
        task.dependsOn()?.forEach {
            if (dependenciesMap[it] == null) {
                dependenciesMap[it] = mutableListOf()
            }
            dependenciesMap[it]?.add(task)
        }
    }


    companion object {
        var ctx: Application? = null
        var isInitialized: Boolean = false
        var isInMainProcess: Boolean = false


        fun init(context: Application) {
            if (ctx == null) {
                ctx = context
                isInitialized = true
                isInMainProcess = Utils.isMainProcess(ctx!!)
            }

        }

        fun createInstance(): TaskDispatcher {
            if (!isInitialized) {
                throw  IllegalStateException("please call init first")
            }

            return TaskDispatcher()
        }
    }
}