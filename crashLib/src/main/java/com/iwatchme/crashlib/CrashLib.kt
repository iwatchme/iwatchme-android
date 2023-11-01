package com.iwatchme.crashlib

object CrashLib {

    private var crashCallback: ICrashCallback? = null


    init {
        System.loadLibrary("crashlib")
    }


    fun initCrashSdk(signals: IntArray, crashCallback: ICrashCallback) {
        this.crashCallback = crashCallback
        registerSignals(signals)
    }


    @JvmStatic
    fun callFromNative(signal: Int, nativeStackTrace: String) {
        crashCallback?.run {
            if (signal == SignalConst.SIGQUIT && checkIsAnr()) {
                onHandleAnr()
                return
            }
            onHandleCrash(signal, nativeStackTrace)
        }

    }

    private external fun registerSignals(signals: IntArray): Boolean

    external fun raiseError()

}

interface ICrashCallback {
    fun checkIsAnr(): Boolean
    fun onHandleAnr()
    fun onHandleCrash(signal: Int, nativeStackTrace: String)
}

class SignalConst {
    companion object {
        const val SIGHUP = 1
        const val SIGINT = 2
        const val SIGQUIT = 3
        const val SIGILL = 4
        const val SIGTRAP = 5
        const val SIGABRT = 6
        const val SIGBUS = 7
        const val SIGSEGV = 11
    }
}