package com.iwatchme.startupruntime.session

import android.app.Application
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import com.iwatchme.startupruntime.analysis.StartupPathAnalyzer
import com.iwatchme.startupruntime.analysis.startupTaskReportComparator
import com.iwatchme.startupruntime.dispatch.MainThreadMicroBatcher
import com.iwatchme.startupruntime.dispatch.StartupExecutors
import com.iwatchme.startupruntime.internal.ProcessUtils
import com.iwatchme.startupruntime.model.StartupAwaitResult
import com.iwatchme.startupruntime.model.StartupDispatcher
import com.iwatchme.startupruntime.model.StartupReport
import com.iwatchme.startupruntime.model.StartupStage
import com.iwatchme.startupruntime.model.StartupSummary
import com.iwatchme.startupruntime.model.StartupTask
import com.iwatchme.startupruntime.model.StartupTaskContext
import com.iwatchme.startupruntime.model.StartupTaskReport
import com.iwatchme.startupruntime.model.StartupTaskStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class StartupSession internal constructor(
    internal val application: Application,
    internal val tasks: List<StartupTask>,
    private val mainThreadFrameBudgetMs: Long,
) {
    private data class TaskNode(
        val task: StartupTask,
        val children: MutableList<TaskNode> = mutableListOf(),
        val remainingDependencies: AtomicInteger = AtomicInteger(task.dependencies.size),
        val completion: CompletableDeferred<Unit> = CompletableDeferred(),
        val scheduled: AtomicBoolean = AtomicBoolean(false),
        val triggered: AtomicBoolean = AtomicBoolean(false),
    )

    private data class MutableRecord(
        val task: StartupTask,
        @Volatile var status: StartupTaskStatus = StartupTaskStatus.PENDING,
        @Volatile var threadName: String? = null,
        @Volatile var startOffsetMs: Long? = null,
        @Volatile var durationMs: Long? = null,
        @Volatile var batchId: Int? = null,
        @Volatile var errorMessage: String? = null,
        val tags: MutableSet<String> = task.tags.toMutableSet(),
    )

    private val startedAtMs = SystemClock.elapsedRealtime()
    private val notes = mutableListOf<String>()
    private val recordLock = Any()
    private val nodeMap = LinkedHashMap<String, TaskNode>()
    private val recordMap = ConcurrentHashMap<String, MutableRecord>()
    private val mainBatcher by lazy(LazyThreadSafetyMode.NONE) {
        MainThreadMicroBatcher(mainThreadFrameBudgetMs)
    }
    private val taskScope = CoroutineScope(SupervisorJob())
    private val idleEnabled = AtomicBoolean(false)
    private val criticalReadyRecorded = AtomicBoolean(false)
    private val fullReadyRecorded = AtomicBoolean(false)
    private val idleCompleteRecorded = AtomicBoolean(false)
    private val waitingIdleCount = AtomicInteger(0)

    @Volatile
    private var criticalReadyAtMs: Long? = null

    @Volatile
    private var fullReadyAtMs: Long? = null

    @Volatile
    private var idleStartedAtMs: Long? = null

    @Volatile
    private var idleCompletedAtMs: Long? = null

    private companion object {
        const val TAG = "StartupRuntime"
    }

    init {
        validateTasks(tasks)
        buildGraph(tasks)
    }

    fun start() {
        Log.d(
            TAG,
            "session start tasks=${tasks.size} roots=${
                nodeMap.values.filter { it.remainingDependencies.get() == 0 }.joinToString { it.task.id }
            } budget=${mainThreadFrameBudgetMs}ms",
        )
        nodeMap.values
            .filter { it.remainingDependencies.get() == 0 }
            .forEach(::scheduleIfEligible)
    }

    suspend fun awaitCritical(timeoutMs: Long = 3_000L): StartupAwaitResult {
        val blockingNodes = tasks.filter { it.stage == StartupStage.BLOCKING }.mapNotNull { nodeMap[it.id] }
        Log.d(TAG, "await critical start tasks=${blockingNodes.joinToString { it.task.id }} timeout=${timeoutMs}ms")
        return awaitNodes(blockingNodes, timeoutMs) {
            if (criticalReadyRecorded.compareAndSet(false, true)) {
                criticalReadyAtMs = SystemClock.elapsedRealtime() - startedAtMs
            }
        }
    }

    suspend fun awaitFullDrawnReady(timeoutMs: Long = 5_000L): StartupAwaitResult {
        val nodes = tasks.filter { it.stage == StartupStage.BLOCKING || it.stage == StartupStage.NON_BLOCKING }
            .mapNotNull { nodeMap[it.id] }
        Log.d(TAG, "await full start tasks=${nodes.joinToString { it.task.id }} timeout=${timeoutMs}ms")
        return awaitNodes(nodes, timeoutMs) {
            if (fullReadyRecorded.compareAndSet(false, true)) {
                fullReadyAtMs = SystemClock.elapsedRealtime() - startedAtMs
            }
        }
    }

    fun enableIdleDrain() {
        idleEnabled.set(true)
        idleStartedAtMs = SystemClock.elapsedRealtime() - startedAtMs
        Log.d(TAG, "idle drain enabled at=${idleStartedAtMs}ms")
        nodeMap.values
            .filter { it.task.stage == StartupStage.IDLE && it.remainingDependencies.get() == 0 }
            .forEach(::scheduleIfEligible)
        maybeMarkIdleComplete()
    }

    suspend fun awaitIdle(timeoutMs: Long = 5_000L): StartupAwaitResult {
        val idleNodes = tasks.filter { it.stage == StartupStage.IDLE }.mapNotNull { nodeMap[it.id] }
        Log.d(TAG, "await idle start tasks=${idleNodes.joinToString { it.task.id }} timeout=${timeoutMs}ms")
        return awaitNodes(idleNodes, timeoutMs) {
            maybeMarkIdleComplete()
        }
    }

    fun triggerOnDemand(vararg taskIds: String) {
        taskIds.forEach { taskId ->
            val node = nodeMap[taskId] ?: return@forEach
            if (node.task.stage != StartupStage.ON_DEMAND) {
                return@forEach
            }
            node.triggered.set(true)
            Log.d(TAG, "on-demand trigger task=${node.task.id} depsRemaining=${node.remainingDependencies.get()}")
            if (node.remainingDependencies.get() == 0) {
                scheduleIfEligible(node)
            }
        }
    }

    fun createReport(): StartupReport {
        val taskReports = tasks.map { task ->
            val record = recordMap.getValue(task.id)
            StartupTaskReport(
                id = task.id,
                description = task.description,
                stage = task.stage,
                dispatcher = task.dispatcher,
                dependencies = task.dependencies,
                status = record.status,
                threadName = record.threadName,
                startOffsetMs = record.startOffsetMs,
                durationMs = record.durationMs,
                batchId = record.batchId,
                tags = record.tags.toSet(),
                errorMessage = record.errorMessage,
            )
        }.sortedWith(Comparator(::startupTaskReportComparator))
        val keyPath = StartupPathAnalyzer.computeKeyPath(taskReports)
        val summary = StartupSummary(
            startedAtMs = startedAtMs,
            criticalReadyAtMs = criticalReadyAtMs,
            fullReadyAtMs = fullReadyAtMs,
            idleStartedAtMs = idleStartedAtMs,
            idleCompletedAtMs = idleCompletedAtMs,
            totalTaskCount = tasks.size,
            completedTaskCount = taskReports.count { it.status == StartupTaskStatus.COMPLETED },
            failedTaskCount = taskReports.count { it.status == StartupTaskStatus.FAILED },
            timedOutTaskCount = taskReports.count { it.status == StartupTaskStatus.TIMED_OUT },
            skippedTaskCount = taskReports.count { it.status == StartupTaskStatus.SKIPPED },
            keyPathDurationMs = keyPath.first,
            keyPathTaskIds = keyPath.second,
            notes = synchronized(recordLock) { notes.toList() },
        )
        return StartupReport(summary = summary, tasks = taskReports)
    }

    internal fun recordNote(message: String) {
        synchronized(recordLock) {
            notes += message
        }
    }

    internal fun addDynamicTag(taskId: String, tag: String) {
        recordMap[taskId]?.tags?.add(tag)
    }

    private fun validateTasks(tasks: List<StartupTask>) {
        val duplicates = tasks.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate startup task ids: ${duplicates.joinToString()}" }
        val ids = tasks.map { it.id }.toSet()
        tasks.forEach { task ->
            val missingDeps = task.dependencies - ids
            require(missingDeps.isEmpty()) { "Task ${task.id} has missing dependencies: ${missingDeps.joinToString()}" }
        }
        val indegree = tasks.associate { it.id to 0 }.toMutableMap()
        tasks.forEach { task ->
            task.dependencies.forEach { _ -> indegree[task.id] = indegree.getValue(task.id) + 1 }
        }
        val queue = ArrayDeque(indegree.filterValues { it == 0 }.keys)
        var visited = 0
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            visited++
            tasks.filter { current in it.dependencies }.forEach { child ->
                val next = indegree.getValue(child.id) - 1
                indegree[child.id] = next
                if (next == 0) {
                    queue.addLast(child.id)
                }
            }
        }
        require(visited == tasks.size) { "Startup task graph contains a cycle" }
    }

    private fun buildGraph(tasks: List<StartupTask>) {
        tasks.forEach { task ->
            val node = TaskNode(task = task)
            nodeMap[task.id] = node
            recordMap[task.id] = MutableRecord(task)
            if (task.stage == StartupStage.IDLE) {
                waitingIdleCount.incrementAndGet()
            }
        }
        nodeMap.values.forEach { node ->
            node.task.dependencies.forEach { dependencyId ->
                nodeMap.getValue(dependencyId).children += node
            }
        }
    }

    private fun scheduleIfEligible(node: TaskNode) {
        if (!node.scheduled.compareAndSet(false, true)) {
            return
        }
        if (node.task.mainProcessOnly && !ProcessUtils.isMainProcess(application)) {
            Log.d(TAG, "skip task=${node.task.id} reason=non-main-process")
            recordMap.getValue(node.task.id).status = StartupTaskStatus.SKIPPED
            node.completion.complete(Unit)
            onTaskFinished(node)
            return
        }
        when (node.task.stage) {
            StartupStage.ON_DEMAND -> {
                if (!node.triggered.get()) {
                    node.scheduled.set(false)
                    Log.d(TAG, "defer task=${node.task.id} stage=ON_DEMAND reason=waiting-trigger")
                    return
                }
                dispatchNode(node)
            }
            StartupStage.IDLE -> {
                if (!idleEnabled.get()) {
                    node.scheduled.set(false)
                    Log.d(TAG, "defer task=${node.task.id} stage=IDLE reason=idle-not-enabled")
                    return
                }
                dispatchNode(node)
            }
            else -> dispatchNode(node)
        }
    }

    private fun dispatchNode(node: TaskNode) {
        Log.d(
            TAG,
            "dispatch task=${node.task.id} stage=${node.task.stage} dispatcher=${node.task.dispatcher} deps=${node.task.dependencies.joinToString()}",
        )
        when (node.task.dispatcher) {
            StartupDispatcher.MAIN -> mainBatcher.submit { batchId -> executeNode(node, batchId) }
            StartupDispatcher.IO,
            StartupDispatcher.CPU,
            -> taskScope.launch(StartupExecutors.dispatcherFor(node.task.dispatcher)) {
                executeNode(node, null)
            }
        }
    }

    private suspend fun executeNode(node: TaskNode, batchId: Int?) {
        val record = recordMap.getValue(node.task.id)
        record.status = StartupTaskStatus.RUNNING
        record.threadName = Thread.currentThread().name
        record.startOffsetMs = SystemClock.elapsedRealtime() - startedAtMs
        record.batchId = batchId
        val start = SystemClock.elapsedRealtime()
        val traceName = "startup:${node.task.id}"
        Log.d(
            TAG,
            "start task=${node.task.id} stage=${node.task.stage} dispatcher=${node.task.dispatcher} batch=${batchId ?: "-"} thread=${record.threadName} at=${record.startOffsetMs}ms",
        )
        Trace.beginSection(traceName)
        try {
            node.task.run(StartupTaskContext(application = application, session = this))
            val duration = SystemClock.elapsedRealtime() - start
            record.durationMs = duration
            val timeoutMs = node.task.timeoutMs
            record.status = if (timeoutMs != null && duration > timeoutMs) {
                StartupTaskStatus.TIMED_OUT
            } else {
                StartupTaskStatus.COMPLETED
            }
            Log.d(
                TAG,
                "finish task=${node.task.id} status=${record.status} duration=${duration}ms batch=${batchId ?: "-"} thread=${record.threadName}",
            )
        } catch (throwable: Throwable) {
            record.durationMs = SystemClock.elapsedRealtime() - start
            record.status = StartupTaskStatus.FAILED
            record.errorMessage = throwable.message ?: throwable::class.java.simpleName
            recordNote("${node.task.id} failed: ${record.errorMessage}")
            Log.d(
                TAG,
                "finish task=${node.task.id} status=${record.status} duration=${record.durationMs}ms error=${record.errorMessage}",
            )
        } finally {
            Trace.endSection()
            node.completion.complete(Unit)
            onTaskFinished(node)
        }
    }

    private fun onTaskFinished(node: TaskNode) {
        node.children.forEach { child ->
            val remaining = child.remainingDependencies.decrementAndGet()
            Log.d(TAG, "dependency resolved parent=${node.task.id} child=${child.task.id} remaining=${remaining}")
            if (remaining == 0) {
                scheduleIfEligible(child)
            }
        }
        if (node.task.stage == StartupStage.IDLE) {
            if (waitingIdleCount.decrementAndGet() == 0) {
                maybeMarkIdleComplete()
            }
        }
    }

    private fun maybeMarkIdleComplete() {
        if (!idleEnabled.get()) {
            return
        }
        if (waitingIdleCount.get() == 0 && idleCompleteRecorded.compareAndSet(false, true)) {
            idleCompletedAtMs = SystemClock.elapsedRealtime() - startedAtMs
            Log.d(TAG, "idle complete at=${idleCompletedAtMs}ms")
        }
    }

    private suspend fun awaitNodes(
        nodes: List<TaskNode>,
        timeoutMs: Long,
        onSuccess: () -> Unit,
    ): StartupAwaitResult {
        val start = SystemClock.elapsedRealtime()
        val deadline = start + timeoutMs
        val timedOutIds = mutableListOf<String>()
        nodes.forEach { node ->
            val remaining = deadline - SystemClock.elapsedRealtime()
            val completed = if (remaining <= 0L) {
                false
            } else {
                withTimeoutOrNull(remaining) { node.completion.await() } != null
            }
            if (!completed) {
                timedOutIds += node.task.id
            }
        }
        val completed = timedOutIds.isEmpty()
        if (completed) {
            onSuccess()
        }
        val result = StartupAwaitResult(
            completed = completed,
            awaitedTaskIds = nodes.map { it.task.id },
            timedOutTaskIds = timedOutIds,
            durationMs = SystemClock.elapsedRealtime() - start,
        )
        Log.d(
            TAG,
            "await result completed=${result.completed} duration=${result.durationMs}ms timedOut=${timedOutIds.joinToString()}",
        )
        return result
    }

}
