package com.iwatchme.customcoroutine.dag

import kotlin.reflect.KClass

interface ITask {
    suspend fun execute(context: TaskContext)
    val dependencies: List<KClass<out ITask>>
}


class TaskContext {

    private val taskMap = mutableMapOf<String, Any>()

    fun <T : Any> setData(key: String, value: T) {
        taskMap[key] = value
    }


    fun <T> getData(key: String): T? {
        return taskMap[key] as? T
    }

}