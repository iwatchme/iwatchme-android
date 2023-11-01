package com.iwatchme.jetpackstarter

import android.app.Application
import android.util.Log
import com.iwatchme.crashlib.CrashLib
import com.iwatchme.crashlib.ICrashCallback
import com.iwatchme.crashlib.SignalConst

class JetpackApplication : Application() {


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
                    Log.e("CrashLib", "s212121ignal: ${signal}; trace: $nativeStackTrace")
                }

            })
    }
}