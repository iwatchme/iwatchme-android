package com.iwatchme.jetpackstarter.demo

import com.iwatchme.jetpackstarter.demo.crash.CrashLibDemoScreen
import com.iwatchme.startuplab.ui.StartupInspectorScreen

object DemoRegistry {

    private val registeredDemos: List<DemoEntry> = listOf(
        DemoEntry(
            route = "demo/startup-inspector",
            title = "Startup Inspector",
            description = "Inspect provider cost, phase breakdown, timeline, and mode comparison for the current cold start.",
            content = {
                StartupInspectorScreen()
            },
        ),
        DemoEntry(
            route = "demo/crashlib",
            title = "CrashLib Demo",
            description = "Initialize CrashLib manually and trigger a native crash for verification.",
            content = {
                CrashLibDemoScreen()
            },
        ),
    )

    init {
        val duplicatedRoutes = registeredDemos
            .groupBy { it.route }
            .filterValues { it.size > 1 }
            .keys
        require(duplicatedRoutes.isEmpty()) {
            "Duplicate demo routes found: ${duplicatedRoutes.joinToString()}"
        }
    }

    val demos: List<DemoEntry>
        get() = registeredDemos
}
