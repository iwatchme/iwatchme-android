package com.iwatchme.startupruntime.analysis

import com.iwatchme.startupruntime.model.StartupTaskReport

object StartupPathAnalyzer {
    fun computeKeyPath(taskReports: List<StartupTaskReport>): Pair<Long, List<String>> {
        if (taskReports.isEmpty()) return 0L to emptyList()
        val tasksById = taskReports.associateBy { it.id }
        val durationMap = taskReports.associate { it.id to (it.durationMs ?: 0L) }
        val pathDuration = mutableMapOf<String, Long>()
        val pathTasks = mutableMapOf<String, List<String>>()
        val orderedIds = topologicalOrder(taskReports)
        orderedIds.forEach { taskId ->
            val task = tasksById.getValue(taskId)
            val bestParent = task.dependencies
                .filter { it in tasksById }
                .maxByOrNull { pathDuration[it] ?: 0L }
            val parentDuration = bestParent?.let { pathDuration[it] ?: 0L } ?: 0L
            val parentPath = bestParent?.let { pathTasks[it].orEmpty() }.orEmpty()
            pathDuration[taskId] = parentDuration + durationMap.getValue(taskId)
            pathTasks[taskId] = parentPath + taskId
        }
        val bestTaskId = pathDuration.maxByOrNull { it.value }?.key ?: return 0L to emptyList()
        return pathDuration.getValue(bestTaskId) to pathTasks.getValue(bestTaskId)
    }

    private fun topologicalOrder(taskReports: List<StartupTaskReport>): List<String> {
        val ids = taskReports.map { it.id }.toSet()
        val indegree = taskReports.associate { task ->
            task.id to task.dependencies.count { it in ids }
        }.toMutableMap()
        val queue = ArrayDeque(taskReports.filter { indegree.getValue(it.id) == 0 }.map { it.id }.sorted())
        val orderedIds = mutableListOf<String>()
        while (queue.isNotEmpty()) {
            val taskId = queue.removeFirst()
            orderedIds += taskId
            taskReports.filter { taskId in it.dependencies }.sortedBy { it.id }.forEach { child ->
                val next = indegree.getValue(child.id) - 1
                indegree[child.id] = next
                if (next == 0) {
                    queue.addLast(child.id)
                }
            }
        }
        return orderedIds
    }
}
