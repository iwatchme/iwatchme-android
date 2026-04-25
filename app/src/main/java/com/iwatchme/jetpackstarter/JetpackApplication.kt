package com.iwatchme.jetpackstarter

import android.app.Application
import com.iwatchme.startuplab.orchestration.JetpackStartupManager

class JetpackApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        JetpackStartupManager.start(this)
    }
}
