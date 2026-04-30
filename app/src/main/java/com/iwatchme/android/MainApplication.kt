package com.iwatchme.android

import android.app.Application
import com.iwatchme.startuplab.orchestration.JetpackStartupManager

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        JetpackStartupManager.start(this)
    }
}
