package com.iwatchme.startuplab

import com.iwatchme.startuplab.scenario.StartupComparisonSimulator
import com.iwatchme.startuplab.scenario.StartupScenarioCatalog
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupComparisonSimulatorTest {
    @Test
    fun optimizedModelBeatsLegacyModel() {
        val comparison = StartupComparisonSimulator.compare(StartupScenarioCatalog.workload)

        assertTrue(comparison.optimized.criticalReadyMs < comparison.baseline.criticalReadyMs)
        assertTrue(comparison.optimized.fullReadyMs < comparison.baseline.fullReadyMs)
        assertTrue(comparison.optimized.mainThreadLongestBlockMs < comparison.baseline.mainThreadLongestBlockMs)
    }
}
