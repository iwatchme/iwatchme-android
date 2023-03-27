package com.iwatchme.customcoroutine.dag

import kotlinx.coroutines.delay
import kotlin.reflect.KClass

class TaskA : ITask {
    override suspend fun execute(context: TaskContext) {
        println("taskA start ${Thread.currentThread()}")
         delay(1000)
        println("taskA end")

    }

    override val dependencies: List<KClass<out ITask>> = mutableListOf()
}



class TaskB : ITask {
    override suspend fun execute(context: TaskContext) {
       println("taskB start ${Thread.currentThread()}")
        delay(2000)
       println( "taskB end")
    }

    override val dependencies: List<KClass<out ITask>> = mutableListOf(TaskA::class)
}

class TaskC : ITask {
    override suspend fun execute(context: TaskContext) {
       println( "taskC start ${Thread.currentThread()}")
        delay(5000)
       println( "taskC end")
    }

    override val dependencies: List<KClass<out ITask>> = mutableListOf(TaskD::class, TaskB::class)
}


class TaskD : ITask {
    override suspend fun execute(context: TaskContext) {
       println( "taskD start ${Thread.currentThread()}")
        delay(5000)
       println( "taskD end")
    }

    override val dependencies: List<KClass<out ITask>> = mutableListOf(TaskA::class)
}


class TaskE : ITask {
    override suspend fun execute(context: TaskContext) {
        println( "taskE start ${Thread.currentThread()}")
        delay(1000)
        println( "taskE end")
    }

    override val dependencies: List<KClass<out ITask>> = mutableListOf(TaskD::class, TaskB::class)
}