package com.iwatchme.startuplab.model

import com.iwatchme.startupruntime.model.StartupDispatcher
import com.iwatchme.startupruntime.model.StartupStage

data class StartupWorkloadSpec(
    val id: String,
    val title: String,
    val durationMs: Long,
    val dispatcher: StartupDispatcher,
    val stage: StartupStage,
    val dependencies: Set<String> = emptySet(),
)
