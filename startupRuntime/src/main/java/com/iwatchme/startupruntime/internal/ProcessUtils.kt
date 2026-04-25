package com.iwatchme.startupruntime.internal

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader

internal object ProcessUtils {
    @Volatile
    private var cachedProcessName: String? = null

    fun isMainProcess(context: Context): Boolean {
        val processName = currentProcessName(context) ?: return false
        return !processName.contains(":") && processName == context.packageName
    }

    private fun currentProcessName(context: Context): String? {
        cachedProcessName?.let { return it }
        try {
            val pid = Process.myPid()
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            activityManager?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName?.let { processName ->
                cachedProcessName = processName
                return processName
            }
        } catch (_: Exception) {
            // Ignore and fall back to /proc.
        }
        val procName = currentProcessNameFromProc()
        cachedProcessName = procName
        return procName
    }

    private fun currentProcessNameFromProc(): String? {
        return try {
            BufferedReader(
                InputStreamReader(
                    FileInputStream("/proc/${Process.myPid()}/cmdline"),
                    Charsets.ISO_8859_1,
                ),
            ).use { reader ->
                buildString {
                    reader.forEachLine(::append)
                }
            }
        } catch (_: Throwable) {
            null
        }
    }
}
