package com.iwatchme.startupruntime

import android.app.Application
import com.iwatchme.startupruntime.model.StartupTask
import com.iwatchme.startupruntime.session.StartupSession

class StartupRuntime private constructor(
    private val application: Application,
    private val tasks: List<StartupTask>,
    private val mainThreadFrameBudgetMs: Long,
) {
    fun start(): StartupSession {
        return StartupSession(
            application = application,
            tasks = tasks,
            mainThreadFrameBudgetMs = mainThreadFrameBudgetMs,
        ).also { it.start() }
    }

    class Builder(private val application: Application) {
        private val tasks = mutableListOf<StartupTask>()
        private var mainThreadFrameBudgetMs: Long = 8L

        fun addTask(task: StartupTask) = apply {
            tasks += task
        }

        fun addTasks(newTasks: Iterable<StartupTask>) = apply {
            tasks += newTasks
        }

        fun mainThreadFrameBudgetMs(frameBudgetMs: Long) = apply {
            mainThreadFrameBudgetMs = frameBudgetMs
        }

        fun build(): StartupRuntime {
            return StartupRuntime(
                application = application,
                tasks = tasks.toList(),
                mainThreadFrameBudgetMs = mainThreadFrameBudgetMs,
            )
        }
    }
}
