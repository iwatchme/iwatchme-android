package com.iwatchme.jetpackstarter.demo

import com.iwatchme.jetpackstarter.demo.crash.CrashLibDemoScreen
import com.iwatchme.jetpackstarter.demo.renderengine.RenderEngineDemoScreen
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
        DemoEntry(
            route = "demo/render-engine",
            title = "Render Engine",
            description = "Video playback powered by FFmpeg + MediaCodec hardware decoding with OpenGL ES render tree.",
            content = {
                RenderEngineDemoScreen()
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
