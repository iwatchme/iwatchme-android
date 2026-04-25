package com.iwatchme.startuplab.scenario

import com.iwatchme.startuplab.model.StartupComparison
import com.iwatchme.startuplab.model.StartupSimulationSummary
import com.iwatchme.startuplab.model.StartupSimulationTaskResult
import com.iwatchme.startuplab.model.StartupWorkloadSpec
import com.iwatchme.startupruntime.model.StartupDispatcher
import com.iwatchme.startupruntime.model.StartupStage

object StartupComparisonSimulator {
    fun compare(workload: List<StartupWorkloadSpec>): StartupComparison {
        return StartupComparison(
            baseline = simulateBaseline(workload),
            optimized = simulateOptimized(workload),
        )
    }

    private fun simulateBaseline(workload: List<StartupWorkloadSpec>): StartupSimulationSummary {
        val eagerTasks = workload.filter { it.stage == StartupStage.BLOCKING || it.stage == StartupStage.NON_BLOCKING }
        var currentTime = StartupScenarioCatalog.legacyProviderCostMs
        val results = mutableListOf<StartupSimulationTaskResult>()
        topologicalOrder(eagerTasks).forEach { task ->
            currentTime = maxOf(currentTime, maxDependencyEnd(task, results))
            val start = currentTime
            val duration = StartupScenarioCatalog.legacyTaskDuration(task)
            val end = start + duration
            results += StartupSimulationTaskResult(
                id = task.id,
                title = task.title,
                startMs = start,
                endMs = end,
                durationMs = duration,
            )
            currentTime = end
        }
        val fullReady = results.maxOfOrNull { it.endMs } ?: StartupScenarioCatalog.legacyProviderCostMs
        val eagerWork = eagerTasks.sumOf { StartupScenarioCatalog.legacyTaskDuration(it) }
        return StartupSimulationSummary(
            providerCostMs = StartupScenarioCatalog.legacyProviderCostMs,
            criticalReadyMs = fullReady,
            fullReadyMs = fullReady,
            eagerWorkMs = eagerWork,
            mainThreadLongestBlockMs = eagerWork + StartupScenarioCatalog.legacyProviderCostMs,
            mainThreadBatchCount = 1,
            tasks = results,
        )
    }

    private fun simulateOptimized(workload: List<StartupWorkloadSpec>): StartupSimulationSummary {
        val eagerTasks = workload.filter { it.stage == StartupStage.BLOCKING || it.stage == StartupStage.NON_BLOCKING }
        val resourceAvailable = mutableMapOf(
            StartupDispatcher.MAIN to StartupScenarioCatalog.optimizedProviderCostMs,
            StartupDispatcher.IO to StartupScenarioCatalog.optimizedProviderCostMs,
            StartupDispatcher.CPU to StartupScenarioCatalog.optimizedProviderCostMs,
        )
        val results = mutableListOf<StartupSimulationTaskResult>()
        topologicalOrder(eagerTasks).forEach { task ->
            val depEnd = maxDependencyEnd(task, results)
            val start = maxOf(depEnd, resourceAvailable.getValue(task.dispatcher))
            val end = start + task.durationMs
            results += StartupSimulationTaskResult(
                id = task.id,
                title = task.title,
                startMs = start,
                endMs = end,
                durationMs = task.durationMs,
            )
            resourceAvailable[task.dispatcher] = end
        }
        val criticalReady = results
            .filter { result -> workload.first { it.id == result.id }.stage == StartupStage.BLOCKING }
            .maxOfOrNull { it.endMs }
            ?: StartupScenarioCatalog.optimizedProviderCostMs
        val fullReady = results.maxOfOrNull { it.endMs } ?: StartupScenarioCatalog.optimizedProviderCostMs
        val eagerWork = eagerTasks.sumOf { it.durationMs }
        val mainTasks = results.filter { result -> workload.first { it.id == result.id }.dispatcher == StartupDispatcher.MAIN }
        val batches = computeBatches(mainTasks)
        return StartupSimulationSummary(
            providerCostMs = StartupScenarioCatalog.optimizedProviderCostMs,
            criticalReadyMs = criticalReady,
            fullReadyMs = fullReady,
            eagerWorkMs = eagerWork,
            mainThreadLongestBlockMs = batches.maxOfOrNull { it } ?: 0L,
            mainThreadBatchCount = batches.size,
            tasks = results,
        )
    }

    private fun computeBatches(mainTasks: List<StartupSimulationTaskResult>): List<Long> {
        if (mainTasks.isEmpty()) return emptyList()
        val sorted = mainTasks.sortedBy { it.startMs }
        val batches = mutableListOf<Long>()
        var current = 0L
        sorted.forEach { task ->
            if (current == 0L) {
                current = task.durationMs
                return@forEach
            }
            if (current + task.durationMs <= StartupScenarioCatalog.mainBatchBudgetMs) {
                current += task.durationMs
            } else {
                batches += current
                current = task.durationMs
            }
        }
        if (current > 0L) {
            batches += current
        }
        return batches
    }

    private fun maxDependencyEnd(
        task: StartupWorkloadSpec,
        results: List<StartupSimulationTaskResult>,
    ): Long {
        if (task.dependencies.isEmpty()) {
            return 0L
        }
        return results.filter { it.id in task.dependencies }.maxOf { it.endMs }
    }

    private fun topologicalOrder(tasks: List<StartupWorkloadSpec>): List<StartupWorkloadSpec> {
        val byId = tasks.associateBy { it.id }
        val indegree = tasks.associate { it.id to it.dependencies.count { dependency -> dependency in byId } }.toMutableMap()
        val queue = ArrayDeque(tasks.filter { indegree.getValue(it.id) == 0 }.map { it.id }.sorted())
        val ordered = mutableListOf<StartupWorkloadSpec>()
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            ordered += byId.getValue(id)
            tasks.filter { id in it.dependencies }.sortedBy { it.id }.forEach { child ->
                val next = indegree.getValue(child.id) - 1
                indegree[child.id] = next
                if (next == 0) {
                    queue.addLast(child.id)
                }
            }
        }
        return ordered
    }
}
