package com.iwatchme.host.shadow.safety

import android.util.Log

/**
 * 插件崩溃计数 + 自动回滚阈值判断。三道防线之一。
 *
 * 用法：壳子 Activity / 加载流程 catch 到异常时调用 [reportCrash]，下次取版本前调 [shouldRollback] 决定是否切回稳定版。
 * 真正的回滚切换由 [PluginVersionRegistry] 持久化记录上一个稳定版本号；本类只做"计数 + 决策"。
 */
class PluginCrashGuard(
    private val maxCrashesInWindow: Int = 3,
    private val windowMillis: Long = 60 * 60 * 1000L,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val crashes = mutableMapOf<String, MutableList<Long>>()

    @Synchronized
    fun reportCrash(partKey: String, throwable: Throwable, where: String) {
        Log.e(TAG, "plugin crash partKey=$partKey at=$where", throwable)
        val list = crashes.getOrPut(partKey) { mutableListOf() }
        list += now()
        list.removeAll { it < now() - windowMillis }
    }

    @Synchronized
    fun shouldRollback(partKey: String): Boolean {
        val list = crashes[partKey] ?: return false
        list.removeAll { it < now() - windowMillis }
        return list.size >= maxCrashesInWindow
    }

    @Synchronized
    fun reset(partKey: String) {
        crashes.remove(partKey)
    }

    companion object {
        private const val TAG = "PluginCrashGuard"
    }
}
