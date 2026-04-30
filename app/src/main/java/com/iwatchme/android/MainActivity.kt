package com.iwatchme.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.iwatchme.android.shell.IWatchMeApp
import com.iwatchme.startuplab.orchestration.JetpackStartupManager
import com.iwatchme.startuplab.state.StartupDashboardStore

class MainActivity : ComponentActivity() {
    @Volatile
    private var contentVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            !StartupDashboardStore.state.criticalReady
        }
        super.onCreate(savedInstanceState)
        setContent {
            IWatchMeApp(
                onContentVisible = {
                    contentVisible = true
                    maybeReportFullyDrawn()
                },
            )
        }
        Thread {
            while (!StartupDashboardStore.state.fullReady) {
                Thread.sleep(16L)
            }
            runOnUiThread(::maybeReportFullyDrawn)
        }.start()
    }

    private fun maybeReportFullyDrawn() {
        if (!contentVisible || !StartupDashboardStore.state.fullReady || JetpackStartupManager.isFullyDrawnReported()) {
            return
        }
        reportFullyDrawn()
        JetpackStartupManager.markFullyDrawnReported()
    }
}
