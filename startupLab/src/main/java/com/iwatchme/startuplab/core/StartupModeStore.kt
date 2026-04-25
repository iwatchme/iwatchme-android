package com.iwatchme.startuplab.core

import android.content.Context

object StartupModeStore {
    private const val PREFS_NAME = "startup_lab_prefs"
    private const val KEY_MODE = "startup_mode"

    fun currentMode(context: Context): StartupMode {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MODE, StartupMode.OPTIMIZED.name)
        return StartupMode.entries.firstOrNull { it.name == stored } ?: StartupMode.OPTIMIZED
    }

    fun persistMode(context: Context, mode: StartupMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }
}
