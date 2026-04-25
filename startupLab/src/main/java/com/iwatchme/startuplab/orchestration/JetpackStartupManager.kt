package com.iwatchme.startuplab.orchestration

import android.app.Application
import android.os.Looper
import android.os.StrictMode
import androidx.startup.AppInitializer
import com.iwatchme.startuplab.core.StartupLog
import com.iwatchme.startuplab.core.StartupMode
import com.iwatchme.startuplab.core.StartupModeStore
import com.iwatchme.startuplab.governance.DeferredThirdPartySdkInitializer
import com.iwatchme.startuplab.governance.StartupProviderTracker
import com.iwatchme.startuplab.governance.ThirdPartySdkTracker
import com.iwatchme.startuplab.scenario.StartupComparisonSimulator
import com.iwatchme.startuplab.scenario.StartupScenarioCatalog
import com.iwatchme.startuplab.state.StartupDashboardStore
import com.iwatchme.startupruntime.model.StartupDispatcher
import com.iwatchme.startupruntime.model.StartupReport
import com.iwatchme.startupruntime.StartupRuntime
import com.iwatchme.startupruntime.session.StartupSession
import com.iwatchme.startupruntime.model.StartupStage
import com.iwatchme.startupruntime.model.StartupTask
import com.iwatchme.startupruntime.model.StartupTaskContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object JetpackStartupManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var session: StartupSession? = null
    private lateinit var startupMode: StartupMode
    @Volatile
    private var latestReport: StartupReport? = null
    @Volatile
    private var fullyDrawnReported = false
    @Volatile
    private var idleDrainStarted = false

    fun start(application: Application) {
        session = null
        latestReport = null
        fullyDrawnReported = false
        idleDrainStarted = false
        startupMode = StartupModeStore.currentMode(application)
        if (application.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyLog()
                    .build(),
            )
        }
        StartupDashboardStore.reset(
            providerInitMs = StartupProviderTracker.currentDurationMs(),
            mode = startupMode,
        )
        StartupLog.d("Application start mode=${startupMode.label}")
        StartupDashboardStore.appendNote("Third-party SDK snapshot: ${ThirdPartySdkTracker.snapshot().initSource}")
        Thread {
            val comparison = StartupComparisonSimulator.compare(StartupScenarioCatalog.workload)
            StartupDashboardStore.update { current -> current.copy(comparison = comparison) }
            StartupDashboardStore.appendNote(
                "Simulation baseline vs optimized ready: critical ${comparison.baseline.criticalReadyMs}ms -> ${comparison.optimized.criticalReadyMs}ms",
            )
        }.start()
        when (startupMode) {
            StartupMode.OPTIMIZED -> startOptimized(application)
            StartupMode.LEGACY -> startLegacy()
        }
    }

    private fun startOptimized(application: Application) {
        session = StartupRuntime.Builder(application)
            .mainThreadFrameBudgetMs(StartupScenarioCatalog.mainBatchBudgetMs)
            .addTasks(createTasks())
            .build()
            .start()
        scope.launch {
            val result = requireNotNull(session).awaitCritical(4_000L)
            StartupDashboardStore.update { current -> current.copy(criticalReady = result.completed) }
            StartupDashboardStore.appendNote("Optimized critical startup ready in ${result.durationMs}ms")
        }
        scope.launch {
            val activeSession = requireNotNull(session)
            val result = activeSession.awaitFullDrawnReady(8_000L)
            val report = activeSession.createReport()
            latestReport = report
            StartupDashboardStore.update { current ->
                current.copy(
                    actualReport = report,
                    sdkSnapshot = ThirdPartySdkTracker.snapshot(),
                    fullReady = result.completed,
                )
            }
            StartupDashboardStore.appendNote("Optimized full report ready in ${result.durationMs}ms")
        }
    }

    private fun startLegacy() {
        session = null
        val result = LegacyStartupRunner.run()
        latestReport = result.report
        StartupDashboardStore.update { current ->
            current.copy(
                actualReport = result.report,
                sdkSnapshot = ThirdPartySdkTracker.snapshot(),
                criticalReady = true,
                fullReady = true,
            )
        }
        StartupDashboardStore.appendNote("Legacy startup kept the same switch and entry point, but still blocked serially on each task.")
    }

    fun currentSession(): StartupSession? = session

    fun currentReport(): StartupReport? = latestReport ?: session?.createReport()

    fun markFullyDrawnReported() {
        fullyDrawnReported = true
        StartupDashboardStore.appendNote("reportFullyDrawn() called; waiting for MessageQueue idle to drain idle tasks.")
        if (idleDrainStarted) {
            return
        }
        idleDrainStarted = true
        Looper.myQueue().addIdleHandler {
            StartupDashboardStore.appendNote("MessageQueue is idle; starting deferred idle tasks.")
            when (startupMode) {
                StartupMode.OPTIMIZED -> startOptimizedIdleDrain()
                StartupMode.LEGACY -> startLegacyIdleDrain()
            }
            false
        }
    }

    fun isFullyDrawnReported(): Boolean = fullyDrawnReported

    fun initializeDeferredSdk(application: Application) {
        if (startupMode == StartupMode.LEGACY) {
            StartupDashboardStore.appendNote("Legacy mode already initialized the SDK inside ContentProvider.")
            StartupDashboardStore.update { current -> current.copy(deferredInitStatus = "Already eager-initialized") }
            return
        }
        StartupDashboardStore.update { current -> current.copy(deferredInitStatus = "Running") }
        Thread {
            AppInitializer.getInstance(application).initializeComponent(DeferredThirdPartySdkInitializer::class.java)
            StartupDashboardStore.update { current ->
                current.copy(
                    sdkSnapshot = ThirdPartySdkTracker.snapshot(),
                    deferredInitStatus = "Completed",
                )
            }
            StartupDashboardStore.appendNote("Deferred SDK init executed via AppInitializer.initializeComponent().")
        }.start()
    }

    fun scheduleNextMode(application: Application): StartupMode {
        val nextMode = startupMode.opposite()
        StartupModeStore.persistMode(application, nextMode)
        StartupDashboardStore.update { current -> current.copy(nextLaunchMode = nextMode) }
        StartupDashboardStore.appendNote("Next cold start mode set to ${nextMode.label}. Relaunch the app to apply.")
        StartupLog.d("Next cold start mode set to ${nextMode.label}")
        return nextMode
    }

    private fun createTasks(): List<StartupTask> {
        val runtimeTasks = StartupScenarioCatalog.workload.map { spec ->
            object : StartupTask() {
                override val id: String = spec.id
                override val dependencies: Set<String> = spec.dependencies
                override val dispatcher: StartupDispatcher = spec.dispatcher
                override val stage: StartupStage = spec.stage
                override val description: String = spec.title
                override val timeoutMs: Long = spec.durationMs + 80L

                override suspend fun run(context: StartupTaskContext) {
                    StartupLog.d(
                        "OPTIMIZED task body start id=${spec.id} stage=${spec.stage} dispatcher=${spec.dispatcher} deps=${spec.dependencies.joinToString()} thread=${Thread.currentThread().name}",
                    )
                    delay(spec.durationMs)
                    when (spec.id) {
                        "cache_feed" -> StartupDashboardStore.update { current ->
                            current.copy(feedItems = StartupScenarioCatalog.cachedFeed, feedSource = "Cache")
                        }
                        "fresh_feed" -> StartupDashboardStore.update { current ->
                            current.copy(feedItems = StartupScenarioCatalog.freshFeed, feedSource = "Network")
                        }
                        "idle_preload" -> StartupDashboardStore.appendNote("Idle preload finished")
                        else -> Unit
                    }
                    StartupLog.d(
                        "OPTIMIZED task body finish id=${spec.id} duration=${spec.durationMs}ms thread=${Thread.currentThread().name}",
                    )
                    context.log("${spec.title} finished in ${spec.durationMs}ms")
                }
            }
        }
        return runtimeTasks
    }

    private fun startOptimizedIdleDrain() {
        val activeSession = session ?: return
        activeSession.enableIdleDrain()
        scope.launch {
            val result = activeSession.awaitIdle(8_000L)
            val idleReport = activeSession.createReport()
            latestReport = idleReport
            StartupDashboardStore.update { current ->
                current.copy(
                    actualReport = idleReport,
                    sdkSnapshot = ThirdPartySdkTracker.snapshot(),
                    idleReady = result.completed,
                )
            }
            StartupDashboardStore.appendNote(
                if (result.completed) {
                    "Optimized idle tasks completed after reportFullyDrawn."
                } else {
                    "Optimized idle tasks timed out after reportFullyDrawn."
                },
            )
        }
    }

    private fun startLegacyIdleDrain() {
        val report = latestReport ?: return
        Thread {
            val idleReport = LegacyStartupRunner.runIdle(report)
            latestReport = idleReport
            StartupDashboardStore.update { current ->
                current.copy(
                    actualReport = idleReport,
                    sdkSnapshot = ThirdPartySdkTracker.snapshot(),
                    idleReady = true,
                )
            }
        }.start()
    }
}
