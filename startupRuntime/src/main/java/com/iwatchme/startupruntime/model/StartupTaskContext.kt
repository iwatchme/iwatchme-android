package com.iwatchme.startupruntime.model

import android.app.Application
import android.os.SystemClock
import com.iwatchme.startupruntime.session.StartupSession

class StartupTaskContext internal constructor(
    val application: Application,
    val session: StartupSession,
) {
    fun now(): Long = SystemClock.elapsedRealtime()

    fun log(message: String) {
        session.recordNote(message)
    }

    fun addTag(taskId: String, tag: String) {
        session.addDynamicTag(taskId, tag)
    }
}
