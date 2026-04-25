package com.iwatchme.startupruntime.model

data class StartupAwaitResult(
    val completed: Boolean,
    val awaitedTaskIds: List<String>,
    val timedOutTaskIds: List<String>,
    val durationMs: Long,
)
