package com.iwatchme.host.shadow

import android.util.Log
import com.tencent.shadow.core.common.ILoggerFactory
import com.tencent.shadow.core.common.Logger

/**
 * 把 Shadow 内部用的 [Logger] 桥接到 Android Logcat。仿 sample-host 的 AndroidLogLoggerFactory。
 */
object AndroidLogLoggerFactory : ILoggerFactory {
    override fun getLogger(name: String): Logger = AndroidLogger(name)
}

private class AndroidLogger(private val name: String) : Logger {
    override fun getName(): String = name

    override fun isTraceEnabled(): Boolean = Log.isLoggable(name, Log.VERBOSE)
    override fun isDebugEnabled(): Boolean = Log.isLoggable(name, Log.DEBUG)
    override fun isInfoEnabled(): Boolean = Log.isLoggable(name, Log.INFO)
    override fun isWarnEnabled(): Boolean = Log.isLoggable(name, Log.WARN)
    override fun isErrorEnabled(): Boolean = Log.isLoggable(name, Log.ERROR)

    override fun trace(msg: String) { Log.v(name, msg) }
    override fun trace(format: String, arg: Any?) { Log.v(name, format.formatSafely(arg)) }
    override fun trace(format: String, arg1: Any?, arg2: Any?) { Log.v(name, format.formatSafely(arg1, arg2)) }
    override fun trace(format: String, vararg arguments: Any?) { Log.v(name, format.formatSafely(*arguments)) }
    override fun trace(msg: String, t: Throwable?) { Log.v(name, msg, t) }

    override fun debug(msg: String) { Log.d(name, msg) }
    override fun debug(format: String, arg: Any?) { Log.d(name, format.formatSafely(arg)) }
    override fun debug(format: String, arg1: Any?, arg2: Any?) { Log.d(name, format.formatSafely(arg1, arg2)) }
    override fun debug(format: String, vararg arguments: Any?) { Log.d(name, format.formatSafely(*arguments)) }
    override fun debug(msg: String, t: Throwable?) { Log.d(name, msg, t) }

    override fun info(msg: String) { Log.i(name, msg) }
    override fun info(format: String, arg: Any?) { Log.i(name, format.formatSafely(arg)) }
    override fun info(format: String, arg1: Any?, arg2: Any?) { Log.i(name, format.formatSafely(arg1, arg2)) }
    override fun info(format: String, vararg arguments: Any?) { Log.i(name, format.formatSafely(*arguments)) }
    override fun info(msg: String, t: Throwable?) { Log.i(name, msg, t) }

    override fun warn(msg: String) { Log.w(name, msg) }
    override fun warn(format: String, arg: Any?) { Log.w(name, format.formatSafely(arg)) }
    override fun warn(format: String, vararg arguments: Any?) { Log.w(name, format.formatSafely(*arguments)) }
    override fun warn(format: String, arg1: Any?, arg2: Any?) { Log.w(name, format.formatSafely(arg1, arg2)) }
    override fun warn(msg: String, t: Throwable?) { Log.w(name, msg, t) }

    override fun error(msg: String) { Log.e(name, msg) }
    override fun error(format: String, arg: Any?) { Log.e(name, format.formatSafely(arg)) }
    override fun error(format: String, arg1: Any?, arg2: Any?) { Log.e(name, format.formatSafely(arg1, arg2)) }
    override fun error(format: String, vararg arguments: Any?) { Log.e(name, format.formatSafely(*arguments)) }
    override fun error(msg: String, t: Throwable?) { Log.e(name, msg, t) }
}

// 不能用 runCatching —— Kotlin 会生成 CallableReference，在 :plugin 进程（API 37+ 多 dex + 进程隔离）触发
// IllegalAccessError 把进程秒崩
private fun String.formatSafely(vararg args: Any?): String {
    return try {
        format(*args)
    } catch (_: Throwable) {
        this
    }
}
