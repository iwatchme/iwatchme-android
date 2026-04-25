package com.iwatchme.startuplab.core

import android.util.Log

object StartupLog {
    const val TAG: String = "StartupLab"

    fun d(message: String) {
        Log.d(TAG, message)
    }
}
