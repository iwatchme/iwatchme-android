package com.iwatchme.startuplab.scenario

import com.iwatchme.startuplab.model.StartupWorkloadSpec
import com.iwatchme.startupruntime.model.StartupDispatcher
import com.iwatchme.startupruntime.model.StartupStage

object StartupScenarioCatalog {
    const val legacyHeavyProviderCostMs: Long = 420L
    const val startupBridgeProviderCostMs: Long = 12L
    const val governanceInitializerCostMs: Long = 8L
    const val deferredSdkInitCostMs: Long = 360L
    const val legacyTaskMultiplier: Long = 2L
    const val mainBatchBudgetMs: Long = 16L
    const val interBatchGapMs: Long = 16L

    val legacyProviderCostMs: Long
        get() = legacyHeavyProviderCostMs

    val optimizedProviderCostMs: Long
        get() = startupBridgeProviderCostMs + governanceInitializerCostMs

    fun legacyTaskDuration(spec: StartupWorkloadSpec): Long = spec.durationMs * legacyTaskMultiplier

    val workload: List<StartupWorkloadSpec> = listOf(
        StartupWorkloadSpec(
            id = "log_bootstrap",
            title = "日志系统预热",
            durationMs = 24L,
            dispatcher = StartupDispatcher.MAIN,
            stage = StartupStage.BLOCKING,
        ),
        StartupWorkloadSpec(
            id = "crash_config",
            title = "崩溃配置加载",
            durationMs = 420L,
            dispatcher = StartupDispatcher.IO,
            stage = StartupStage.BLOCKING,
            dependencies = setOf("log_bootstrap"),
        ),
        StartupWorkloadSpec(
            id = "cache_feed",
            title = "缓存首屏数据",
            durationMs = 320L,
            dispatcher = StartupDispatcher.IO,
            stage = StartupStage.BLOCKING,
            dependencies = setOf("log_bootstrap"),
        ),
        StartupWorkloadSpec(
            id = "compose_seed",
            title = "首屏 Compose 种子准备",
            durationMs = 48L,
            dispatcher = StartupDispatcher.MAIN,
            stage = StartupStage.BLOCKING,
            dependencies = setOf("log_bootstrap"),
        ),
        StartupWorkloadSpec(
            id = "strict_mode_audit",
            title = "StrictMode 启动审计",
            durationMs = 140L,
            dispatcher = StartupDispatcher.CPU,
            stage = StartupStage.NON_BLOCKING,
            dependencies = setOf("log_bootstrap"),
        ),
        StartupWorkloadSpec(
            id = "analytics_warmup",
            title = "埋点模块预热",
            durationMs = 260L,
            dispatcher = StartupDispatcher.CPU,
            stage = StartupStage.NON_BLOCKING,
            dependencies = setOf("crash_config"),
        ),
        StartupWorkloadSpec(
            id = "image_pipeline",
            title = "图片管线初始化",
            durationMs = 240L,
            dispatcher = StartupDispatcher.IO,
            stage = StartupStage.NON_BLOCKING,
            dependencies = setOf("cache_feed"),
        ),
        StartupWorkloadSpec(
            id = "fresh_feed",
            title = "网络首屏刷新",
            durationMs = 420L,
            dispatcher = StartupDispatcher.IO,
            stage = StartupStage.NON_BLOCKING,
            dependencies = setOf("cache_feed"),
        ),
        StartupWorkloadSpec(
            id = "idle_preload",
            title = "空闲预加载",
            durationMs = 220L,
            dispatcher = StartupDispatcher.IO,
            stage = StartupStage.IDLE,
            dependencies = setOf("fresh_feed"),
        ),
    )

    val cachedFeed = listOf(
        "Cache headline · last session snapshot",
        "Cache cards render before network refresh",
        "Cache-first keeps TTID and TTFD separate",
    )

    val freshFeed = listOf(
        "Fresh headline · network payload is ready",
        "Microbatch kept main thread slices under control",
        "Full report triggered after startup graph settled",
    )
}
