package com.iwatchme.startuplab.governance

import android.content.Context
import androidx.startup.Initializer
import com.iwatchme.startuplab.core.StartupLog
import com.iwatchme.startuplab.scenario.StartupScenarioCatalog

class DeferredThirdPartySdkInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        val start = System.nanoTime()
        Thread.sleep(StartupScenarioCatalog.deferredSdkInitCostMs)
        val durationMs = (System.nanoTime() - start) / 1_000_000L
        ThirdPartySdkTracker.recordDeferredInitializer(durationMs)
        StartupLog.d("Deferred heavy SDK initializer completed duration=${durationMs}ms")
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return listOf(ThirdPartySdkGovernanceInitializer::class.java)
    }
}
