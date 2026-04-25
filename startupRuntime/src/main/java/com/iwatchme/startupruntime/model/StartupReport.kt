package com.iwatchme.startupruntime.model

data class StartupTaskReport(
    val id: String,
    val description: String,
    val stage: StartupStage,
    val dispatcher: StartupDispatcher,
    val dependencies: Set<String>,
    val status: StartupTaskStatus,
    val threadName: String?,
    val startOffsetMs: Long?,
    val durationMs: Long?,
    val batchId: Int?,
    val tags: Set<String>,
    val errorMessage: String?,
)

data class StartupSummary(
    val startedAtMs: Long,
    val criticalReadyAtMs: Long?,
    val fullReadyAtMs: Long?,
    val idleStartedAtMs: Long?,
    val idleCompletedAtMs: Long?,
    val totalTaskCount: Int,
    val completedTaskCount: Int,
    val failedTaskCount: Int,
    val timedOutTaskCount: Int,
    val skippedTaskCount: Int,
    val keyPathDurationMs: Long,
    val keyPathTaskIds: List<String>,
    val notes: List<String>,
)

data class StartupReport(
    val summary: StartupSummary,
    val tasks: List<StartupTaskReport>,
) {
    fun toPrettyString(): String {
        val lines = mutableListOf<String>()
        lines += "Startup Summary"
        lines += "startedAt=${summary.startedAtMs}"
        lines += "criticalReady=${summary.criticalReadyAtMs ?: -1}"
        lines += "fullReady=${summary.fullReadyAtMs ?: -1}"
        lines += "idleStarted=${summary.idleStartedAtMs ?: -1}"
        lines += "idleCompleted=${summary.idleCompletedAtMs ?: -1}"
        lines += "tasks=${summary.completedTaskCount}/${summary.totalTaskCount} completed, failed=${summary.failedTaskCount}, timedOut=${summary.timedOutTaskCount}, skipped=${summary.skippedTaskCount}"
        lines += "keyPath=${summary.keyPathDurationMs}ms -> ${summary.keyPathTaskIds.joinToString(" -> ")}"
        if (summary.notes.isNotEmpty()) {
            lines += "notes="
            summary.notes.forEach { note -> lines += "- $note" }
        }
        lines += "tasks="
        tasks.sortedWith(compareBy<StartupTaskReport>({ it.startOffsetMs ?: Long.MAX_VALUE }, { it.id }))
            .forEach { task ->
                lines += "- ${task.id} [${task.stage}/${task.dispatcher}] status=${task.status} start=${task.startOffsetMs ?: -1}ms duration=${task.durationMs ?: -1}ms batch=${task.batchId ?: -1} thread=${task.threadName ?: "n/a"} deps=${task.dependencies.joinToString()}"
            }
        return lines.joinToString(separator = "\n")
    }
}
