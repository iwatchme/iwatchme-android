package com.iwatchme.startuplauncher

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.text.TextUtils
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader


class Utils {
    companion object {
        var currentProcessName: String? = null

        fun isMainProcess(context: Context): Boolean {
            val processName = getCurProcessName(context)
            return if (processName != null && processName.contains(":")) {
                false
            } else processName != null && processName == context.packageName
        }

        private fun getCurProcessName(context: Context): String? {
            val processName = currentProcessName
            if (!TextUtils.isEmpty(processName)) {
                return processName
            }
            try {
                val pid = android.os.Process.myPid()
                val mActivityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                for (appProcess in mActivityManager.getRunningAppProcesses()) {
                    if (appProcess.pid === pid) {
                        currentProcessName = appProcess.processName
                        return currentProcessName
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            currentProcessName  = getCurProcessNameFromProc()
            return currentProcessName
        }


        private fun getCurProcessNameFromProc(): String? {
            var cmdlineReader: BufferedReader? = null
            try {
                cmdlineReader = BufferedReader(InputStreamReader(
                        FileInputStream(
                                "/proc/" + Process.myPid() + "/cmdline"),
                        "iso-8859-1"))
                val processName = StringBuilder()
                cmdlineReader.forEachLine {
                    processName.append(it)
                }
                return processName.toString()
            } catch (e: Throwable) {
                // ignore
            } finally {
                if (cmdlineReader != null) {
                    try {
                        cmdlineReader!!.close()
                    } catch (e: Exception) {
                        // ignore
                    }

                }
            }
            return null
        }
    }
}