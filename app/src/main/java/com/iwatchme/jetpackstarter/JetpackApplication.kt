package com.iwatchme.jetpackstarter

import android.app.Application
import android.content.Intent
import android.os.Process
import android.util.Log
import com.iwatchme.crashlib.CrashLib
import com.iwatchme.crashlib.ICrashCallback
import com.iwatchme.crashlib.SignalConst
import kotlin.system.exitProcess

class JetpackApplication : Application() {

    private val context  by  lazy { this }

    override fun onCreate() {
        super.onCreate()
        CrashLib.initCrashSdk(
            intArrayOf(
                SignalConst.SIGQUIT,
                SignalConst.SIGABRT,
                SignalConst.SIGSEGV
            ), object : ICrashCallback {
                override fun checkIsAnr(): Boolean {
                    return false
                }

                override fun onHandleAnr() {

                }

                override fun onHandleCrash(signal: Int, nativeStackTrace: String) {
                    Log.e("CrashLib", "signal: ${signal}; trace: $nativeStackTrace")
                    val restart: Intent? =
                        context.packageManager.getLaunchIntentForPackage(context.packageName)
                    restart?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    restart?.action = "restart"
                    context.startActivity(restart)
                    Process.killProcess(Process.myPid())
                    exitProcess(0)
                }

            })
    }
}