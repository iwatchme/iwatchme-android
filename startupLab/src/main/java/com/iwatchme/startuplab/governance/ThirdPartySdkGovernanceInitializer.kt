package com.iwatchme.startuplab.governance

import android.content.Context
import androidx.startup.Initializer
import com.iwatchme.startuplab.core.StartupLog
import com.iwatchme.startuplab.core.StartupMode
import com.iwatchme.startuplab.core.StartupModeStore
import com.iwatchme.startuplab.scenario.StartupScenarioCatalog

class ThirdPartySdkGovernanceInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        if (StartupModeStore.currentMode(context) != StartupMode.OPTIMIZED) {
            return
        }
        val start = System.nanoTime()
        Thread.sleep(StartupScenarioCatalog.governanceInitializerCostMs)
        val durationMs = (System.nanoTime() - start) / 1_000_000L
        ThirdPartySdkTracker.recordGovernanceInitializer(durationMs)
        StartupLog.d("ThirdParty SDK governance initializer duration=${durationMs}ms")
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return listOf(StartupInitializerBridge::class.java)
    }
}
