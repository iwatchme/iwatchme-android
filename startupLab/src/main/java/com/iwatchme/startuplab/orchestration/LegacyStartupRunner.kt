package com.iwatchme.startuplab.orchestration

import android.os.SystemClock
import com.iwatchme.startuplab.core.StartupLog
import com.iwatchme.startuplab.scenario.StartupScenarioCatalog
import com.iwatchme.startuplab.state.StartupDashboardStore
import com.iwatchme.startuplab.model.StartupWorkloadSpec
import com.iwatchme.startupruntime.model.StartupDispatcher
import com.iwatchme.startupruntime.analysis.StartupPathAnalyzer
import com.iwatchme.startupruntime.model.StartupReport
import com.iwatchme.startupruntime.model.StartupStage
import com.iwatchme.startupruntime.model.StartupSummary
import com.iwatchme.startupruntime.model.StartupTaskReport
import com.iwatchme.startupruntime.model.StartupTaskStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.iwatchme.startuplab.workload.generated.WorkloadRunner

object LegacyStartupRunner {
    data class Result(
        val report: StartupReport,
        val criticalReadyAtMs: Long,
        val fullReadyAtMs: Long,
    )

    fun run(): Result {
        val startedAtMs = SystemClock.elapsedRealtime()
        val eagerTasks = topologicalOrder(
            StartupScenarioCatalog.workload.filter { it.stage == StartupStage.BLOCKING || it.stage == StartupStage.NON_BLOCKING },
        )
        val taskReports = LinkedHashMap<String, StartupTaskReport>()
        val notes = mutableListOf<String>()
        var criticalReadyAtMs = 0L

        runBlocking {
            eagerTasks.forEach { spec ->
                val report = executeSerialTask(
                    spec = spec,
                    startedAtMs = startedAtMs,
                    tags = setOf("legacy", "serial"),
                )
                taskReports[spec.id] = report
                notes += "Legacy orchestrator blocked on ${spec.id} until completion"
                if (spec.stage == StartupStage.BLOCKING) {
                    criticalReadyAtMs = SystemClock.elapsedRealtime() - startedAtMs
                }
            }
        }

        StartupScenarioCatalog.workload
            .filter { it.stage == StartupStage.IDLE || it.stage == StartupStage.ON_DEMAND }
            .forEach { spec ->
                taskReports[spec.id] = StartupTaskReport(
                    id = spec.id,
                    description = spec.title,
                    stage = spec.stage,
                    dispatcher = spec.dispatcher,
                    dependencies = spec.dependencies,
                    status = StartupTaskStatus.PENDING,
                    threadName = null,
                    startOffsetMs = null,
                    durationMs = null,
                    batchId = null,
                    tags = setOf("legacy", if (spec.stage == StartupStage.IDLE) "idle" else "on-demand", "serial"),
                    errorMessage = null,
                )
            }

        val fullReadyAtMs = SystemClock.elapsedRealtime() - startedAtMs
        val report = reportFrom(
            startedAtMs = startedAtMs,
            taskReports = taskReports,
            notes = notes,
            criticalReadyAtMs = criticalReadyAtMs,
            fullReadyAtMs = fullReadyAtMs,
            idleStartedAtMs = null,
            idleCompletedAtMs = null,
        )
        StartupLog.d("LEGACY summary critical=${criticalReadyAtMs}ms full=${fullReadyAtMs}ms")
        return Result(
            report = report,
            criticalReadyAtMs = criticalReadyAtMs,
            fullReadyAtMs = fullReadyAtMs,
        )
    }

    fun runIdle(previousReport: StartupReport): StartupReport {
        val idleTask = StartupScenarioCatalog.workload.firstOrNull { it.stage == StartupStage.IDLE } ?: return previousReport
        val updatedTask = runBlocking {
            executeSerialTask(
                spec = idleTask,
                startedAtMs = previousReport.summary.startedAtMs,
                tags = setOf("legacy", "idle", "serial"),
            )
        }
        StartupDashboardStore.appendNote("Legacy idle preload finished after reportFullyDrawn")
        val report = reportFrom(
            startedAtMs = previousReport.summary.startedAtMs,
            taskReports = previousReport.tasks.associateBy { it.id }.toMutableMap().apply {
                put(idleTask.id, updatedTask)
            },
            notes = previousReport.summary.notes + "Legacy idle task completed after MessageQueue became idle",
            criticalReadyAtMs = previousReport.summary.criticalReadyAtMs ?: 0L,
            fullReadyAtMs = previousReport.summary.fullReadyAtMs ?: 0L,
            idleStartedAtMs = updatedTask.startOffsetMs,
            idleCompletedAtMs = updatedTask.startOffsetMs?.plus(updatedTask.durationMs ?: 0L),
        )
        StartupLog.d("LEGACY idle finish task=${idleTask.id} duration=${updatedTask.durationMs}ms")
        return report
    }

    fun runOnDemand(previousReport: StartupReport, taskId: String): StartupReport {
        val task = StartupScenarioCatalog.workload.firstOrNull { it.id == taskId } ?: return previousReport
        val updatedTask = runBlocking {
            executeSerialTask(
                spec = task,
                startedAtMs = previousReport.summary.startedAtMs,
                tags = setOf("legacy", "on-demand", "serial"),
            )
        }
        StartupDashboardStore.appendNote("Legacy on-demand task completed: ${task.id}")
        val report = reportFrom(
            startedAtMs = previousReport.summary.startedAtMs,
            taskReports = previousReport.tasks.associateBy { it.id }.toMutableMap().apply {
                put(task.id, updatedTask)
            },
            notes = previousReport.summary.notes + "Legacy on-demand task completed: ${task.id}",
            criticalReadyAtMs = previousReport.summary.criticalReadyAtMs ?: 0L,
            fullReadyAtMs = previousReport.summary.fullReadyAtMs ?: 0L,
            idleStartedAtMs = previousReport.summary.idleStartedAtMs,
            idleCompletedAtMs = previousReport.summary.idleCompletedAtMs,
        )
        StartupLog.d("LEGACY on-demand finish task=${task.id} duration=${updatedTask.durationMs}ms")
        return report
    }

    private suspend fun executeSerialTask(
        spec: StartupWorkloadSpec,
        startedAtMs: Long,
        tags: Set<String>,
    ): StartupTaskReport {
        val startOffset = SystemClock.elapsedRealtime() - startedAtMs
        val durationTarget = StartupScenarioCatalog.legacyTaskDuration(spec)
        return when (spec.dispatcher) {
            StartupDispatcher.MAIN -> {
                StartupLog.d(
                    "LEGACY serial start task=${spec.id} stage=${spec.stage} dispatcher=${spec.dispatcher} deps=${spec.dependencies.joinToString()} thread=${Thread.currentThread().name}",
                )
                WorkloadRunner.runForTask(spec.id)
                Thread.sleep(durationTarget)
                applyTaskEffects(spec)
                val durationMs = SystemClock.elapsedRealtime() - startedAtMs - startOffset
                StartupLog.d(
                    "LEGACY serial finish task=${spec.id} duration=${durationMs}ms thread=${Thread.currentThread().name}",
                )
                completedReport(
                    spec = spec,
                    startOffset = startOffset,
                    durationMs = durationMs,
                    threadName = Thread.currentThread().name,
                    tags = tags,
                )
            }

            StartupDispatcher.IO,
            StartupDispatcher.CPU,
            -> withContext(dispatcherFor(spec.dispatcher)) {
                StartupLog.d(
                    "LEGACY serial start task=${spec.id} stage=${spec.stage} dispatcher=${spec.dispatcher} deps=${spec.dependencies.joinToString()} thread=${Thread.currentThread().name}",
                )
                WorkloadRunner.runForTask(spec.id)
                delay(durationTarget)
                applyTaskEffects(spec)
                val durationMs = SystemClock.elapsedRealtime() - startedAtMs - startOffset
                StartupLog.d(
                    "LEGACY serial finish task=${spec.id} duration=${durationMs}ms thread=${Thread.currentThread().name}",
                )
                completedReport(
                    spec = spec,
                    startOffset = startOffset,
                    durationMs = durationMs,
                    threadName = Thread.currentThread().name,
                    tags = tags,
                )
            }
        }
    }

    private fun applyTaskEffects(spec: StartupWorkloadSpec) {
        when (spec.id) {
            "cache_feed" -> StartupDashboardStore.update { current ->
                current.copy(feedItems = StartupScenarioCatalog.cachedFeed, feedSource = "Cache")
            }

            "fresh_feed" -> StartupDashboardStore.update { current ->
                current.copy(feedItems = StartupScenarioCatalog.freshFeed, feedSource = "Network")
            }
        }
    }

    private fun reportFrom(
        startedAtMs: Long,
        taskReports: Map<String, StartupTaskReport>,
        notes: List<String>,
        criticalReadyAtMs: Long,
        fullReadyAtMs: Long,
        idleStartedAtMs: Long?,
        idleCompletedAtMs: Long?,
    ): StartupReport {
        val sortedTasks = taskReports.values.sortedBy { it.startOffsetMs ?: Long.MAX_VALUE }
        val keyPath = StartupPathAnalyzer.computeKeyPath(sortedTasks)
        val summary = StartupSummary(
            startedAtMs = startedAtMs,
            criticalReadyAtMs = criticalReadyAtMs,
            fullReadyAtMs = fullReadyAtMs,
            idleStartedAtMs = idleStartedAtMs,
            idleCompletedAtMs = idleCompletedAtMs,
            totalTaskCount = sortedTasks.size,
            completedTaskCount = sortedTasks.count { it.status == StartupTaskStatus.COMPLETED },
            failedTaskCount = sortedTasks.count { it.status == StartupTaskStatus.FAILED },
            timedOutTaskCount = sortedTasks.count { it.status == StartupTaskStatus.TIMED_OUT },
            skippedTaskCount = sortedTasks.count { it.status == StartupTaskStatus.SKIPPED },
            keyPathDurationMs = keyPath.first,
            keyPathTaskIds = keyPath.second,
            notes = notes + "Legacy execution order: ${sortedTasks.filter { it.status == StartupTaskStatus.COMPLETED }.joinToString(" -> ") { it.id }}",
        )
        return StartupReport(summary = summary, tasks = sortedTasks)
    }

    private fun dispatcherFor(dispatcher: StartupDispatcher): CoroutineDispatcher {
        return when (dispatcher) {
            StartupDispatcher.MAIN -> Dispatchers.Main.immediate
            StartupDispatcher.IO -> Dispatchers.IO
            StartupDispatcher.CPU -> Dispatchers.Default
        }
    }

    private fun completedReport(
        spec: StartupWorkloadSpec,
        startOffset: Long,
        durationMs: Long,
        threadName: String,
        tags: Set<String>,
    ): StartupTaskReport {
        return StartupTaskReport(
            id = spec.id,
            description = spec.title,
            stage = spec.stage,
            dispatcher = spec.dispatcher,
            dependencies = spec.dependencies,
            status = StartupTaskStatus.COMPLETED,
            threadName = threadName,
            startOffsetMs = startOffset,
            durationMs = durationMs,
            batchId = null,
            tags = tags,
            errorMessage = null,
        )
    }

    private fun topologicalOrder(tasks: List<StartupWorkloadSpec>): List<StartupWorkloadSpec> {
        val byId = tasks.associateBy { it.id }
        val indegree = tasks.associate { task -> task.id to task.dependencies.count { it in byId } }.toMutableMap()
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
