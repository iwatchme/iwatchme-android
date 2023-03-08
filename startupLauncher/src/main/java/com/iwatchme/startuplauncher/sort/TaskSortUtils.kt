package com.iwatchme.startuplauncher.sort

import com.iwatchme.startuplauncher.task.Task

class TaskSortUtils {


    companion object {
        @Synchronized
        fun getSortResult(originTasks: List<Task>,
                          originClsTasks: List<Class<out Task>>
        ): List<Task> {

            var graph = Graph(originTasks.size)
            var dependSet = hashSetOf<Int>()

            originTasks.forEachIndexed { index, task ->
                if (task.dependsOn()?.isEmpty() == false) {
                    task.dependsOn()?.forEach {
                        var i = originClsTasks.indexOf(it)
                        dependSet.add(i)
                        graph.addEdge(i, index)
                    }
                }
            }
            var sortResult: List<Int> = graph.topoligicalSort()


            return getResultTask(originTasks, sortResult, dependSet)

        }


        private fun getResultTask(allTask: List<Task>, sortResult: List<Int>, dependSet: Set<Int>): List<Task> {
            var ret: MutableList<Task> = mutableListOf()
            var dependentTasks: MutableList<Task> = mutableListOf()
            var undependentTasks: MutableList<Task> = mutableListOf()

            sortResult.forEach {
                if (dependSet.contains(it)) {
                    dependentTasks.add(allTask[it])
                } else {
                    undependentTasks.add(allTask[it])
                }
            }

            ret.addAll(dependentTasks)
            ret.addAll(undependentTasks)
            return ret
        }
    }
}