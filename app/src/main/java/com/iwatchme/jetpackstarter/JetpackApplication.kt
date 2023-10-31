package com.iwatchme.jetpackstarter

import android.app.Application
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
                    TODO("Not yet implemented")
                }

                override fun onHandleAnr() {
                    TODO("Not yet implemented")
                }

                override fun onHandleCrash() {
                    TODO("Not yet implemented")
                }

            })
    }
}