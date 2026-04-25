package com.iwatchme.startuplab.governance

import android.content.Context
import androidx.startup.Initializer
import com.iwatchme.startuplab.core.StartupLog
import com.iwatchme.startuplab.core.StartupMode
import com.iwatchme.startuplab.core.StartupModeStore
import com.iwatchme.startuplab.scenario.StartupScenarioCatalog

object StartupProviderTracker {
    @Volatile
    private var providerInitMs: Long = 0L

    fun record(durationMs: Long) {
        providerInitMs = durationMs
    }

    fun currentDurationMs(): Long = providerInitMs
}

class StartupInitializerBridge : Initializer<Unit> {
    override fun create(context: Context) {
        val start = System.nanoTime()
        val mode = StartupModeStore.currentMode(context)
        val providerCostMs = when (mode) {
            StartupMode.LEGACY -> 1L
            StartupMode.OPTIMIZED -> StartupScenarioCatalog.startupBridgeProviderCostMs
        }
        Thread.sleep(providerCostMs)
        val durationMs = (System.nanoTime() - start) / 1_000_000L
        StartupProviderTracker.record(durationMs)
        ThirdPartySdkTracker.recordStartupBridge(durationMs)
        StartupLog.d("Provider bridge mode=${mode.label} duration=${durationMs}ms")
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
