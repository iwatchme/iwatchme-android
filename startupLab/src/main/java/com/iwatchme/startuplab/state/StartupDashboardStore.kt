package com.iwatchme.startuplab.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iwatchme.startuplab.core.StartupMode
import com.iwatchme.startuplab.governance.ThirdPartySdkSnapshot
import com.iwatchme.startuplab.governance.ThirdPartySdkTracker
import com.iwatchme.startuplab.model.StartupComparison
import com.iwatchme.startuplab.scenario.StartupScenarioCatalog
import com.iwatchme.startupruntime.model.StartupReport

data class StartupDashboardState(
    val mode: StartupMode = StartupMode.OPTIMIZED,
    val nextLaunchMode: StartupMode = StartupMode.LEGACY,
    val actualReport: StartupReport? = null,
    val comparison: StartupComparison? = null,
    val providerInitMs: Long = 0L,
    val sdkSnapshot: ThirdPartySdkSnapshot = ThirdPartySdkSnapshot(),
    val criticalReady: Boolean = false,
    val fullReady: Boolean = false,
    val idleReady: Boolean = false,
    val feedItems: List<String> = emptyList(),
    val feedSource: String = "Booting",
    val deferredInitStatus: String = "Pending",
    val notes: List<String> = emptyList(),
)

object StartupDashboardStore {
    var state by mutableStateOf(
        StartupDashboardState(
            mode = StartupMode.OPTIMIZED,
            nextLaunchMode = StartupMode.LEGACY,
            feedItems = StartupScenarioCatalog.cachedFeed,
            feedSource = "Cache",
        ),
    )
        private set

    fun reset(providerInitMs: Long, mode: StartupMode) {
        val sdkSnapshot = ThirdPartySdkTracker.snapshot()
        state = StartupDashboardState(
            mode = mode,
            nextLaunchMode = mode.opposite(),
            providerInitMs = providerInitMs,
            sdkSnapshot = sdkSnapshot,
            feedItems = StartupScenarioCatalog.cachedFeed,
            feedSource = "Cache",
            deferredInitStatus = if (mode == StartupMode.LEGACY) "Already eager-initialized" else "Pending",
            notes = listOf(
                "Startup mode: ${mode.label}",
                "InitializationProvider bridge: ${providerInitMs}ms",
                "Third-party SDK path: ${sdkSnapshot.initSource}",
                "ContentProvider governance: ${sdkSnapshot.governanceState}",
                "Provider model: 1 heavy SDK provider vs 1 InitializationProvider",
            ),
        )
    }

    fun update(transform: (StartupDashboardState) -> StartupDashboardState) {
        state = transform(state)
    }

    fun appendNote(note: String) {
        update { current -> current.copy(notes = current.notes + note) }
    }
}
