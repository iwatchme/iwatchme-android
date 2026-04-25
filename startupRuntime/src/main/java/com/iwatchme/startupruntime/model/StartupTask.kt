package com.iwatchme.startupruntime.model

abstract class StartupTask {
    abstract val id: String

    open val dependencies: Set<String> = emptySet()

    open val dispatcher: StartupDispatcher = StartupDispatcher.IO

    open val stage: StartupStage = StartupStage.NON_BLOCKING

    open val timeoutMs: Long? = null

    open val tags: Set<String> = emptySet()

    open val mainProcessOnly: Boolean = true

    open val description: String = id

    abstract suspend fun run(context: StartupTaskContext)
}
