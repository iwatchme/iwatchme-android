package com.iwatchme.startuplab.model

data class StartupSimulationTaskResult(
    val id: String,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long,
)

data class StartupSimulationSummary(
    val providerCostMs: Long,
    val criticalReadyMs: Long,
    val fullReadyMs: Long,
    val eagerWorkMs: Long,
    val mainThreadLongestBlockMs: Long,
    val mainThreadBatchCount: Int,
    val tasks: List<StartupSimulationTaskResult>,
)

data class StartupComparison(
    val baseline: StartupSimulationSummary,
    val optimized: StartupSimulationSummary,
) {
    val criticalImprovementMs: Long
        get() = baseline.criticalReadyMs - optimized.criticalReadyMs

    val fullImprovementMs: Long
        get() = baseline.fullReadyMs - optimized.fullReadyMs

    val mainThreadImprovementMs: Long
        get() = baseline.mainThreadLongestBlockMs - optimized.mainThreadLongestBlockMs
}
