package com.iwatchme.customcoroutine.dag

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlin.reflect.KClass


class Pipeline {
    private val dag = DAG()

    init {
        dag.addTask(TaskA()).addTask(TaskB()).addTask(TaskC()).addTask(TaskD()).addTask(TaskE())
    }

    suspend fun execute() {
        // Concrete implementation of Pipeline execution
        dag.execute()
    }
}


internal class DAG {

    private suspend fun allDependenciesCompleted(task: ITask, completedTasks: MutableSet<KClass<out ITask>>, taskCompletionChannel: Channel<KClass<out ITask>>): Boolean {
        val dependencies = task.dependencies // Replace with your actual function to get dependencies of a task
        return dependencies.all { dependency ->
            if (completedTasks.contains(dependency)) {
                true
            } else {
                select<Boolean> {
                    taskCompletionChannel.onReceive { completedTask ->
                        completedTasks.add(completedTask)
                        completedTask == dependency
                    }
                    onTimeout(0) {
                        false
                    }
                }
            }
        }
    }

    private val tasks = mutableListOf<ITask>()

    fun addTask(task: ITask) = apply {
        tasks.add(task)
    }

    suspend fun execute() {
        val sortedTasks = topologicalSort(tasks)
        val scope = CoroutineScope(Dispatchers.Default)
        val completedTasks = mutableSetOf<KClass<out ITask>>()
        val taskCompletionChannel = Channel<KClass<out ITask>>(Channel.UNLIMITED)

        for (task in sortedTasks) {
            scope.launch {
                while (!allDependenciesCompleted(task, completedTasks, taskCompletionChannel)) {
                }
                task.execute(TaskContext())
                taskCompletionChannel.send(task::class)
            }
        }


        while(sortedTasks.size != completedTasks.size) {
            val completedTask = taskCompletionChannel.receive()
            completedTasks.add(completedTask)
        }

        taskCompletionChannel.close()
    }


    private fun topologicalSort(tasks: List<ITask>): List<ITask> {
        val sorted = mutableListOf<ITask>()
        val visited = mutableListOf<ITask>()

        fun visit(task: ITask) {
            if (task !in visited) {
                visited.add(task)
                for (dependency in task.dependencies) {
                    val depTask = tasks.find { it::class == dependency }
                    if (depTask != null) {
                        visit(depTask)
                    }
                }
                sorted.add(task)
            }
        }

        for (task in tasks) {
            visit(task)
        }

        return sorted
    }
}