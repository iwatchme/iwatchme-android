package com.iwatchme.host.shadow.safety

import android.content.Context
import android.content.SharedPreferences

/**
 * 持久化「当前版本 / 上一稳定版本」用于回滚。最简实现：SharedPreferences。
 * 业务可换成 DataStore；接口保持稳定即可。
 */
class PluginVersionRegistry(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("shadow_plugin_versions", Context.MODE_PRIVATE)

    fun currentVersion(partKey: String): Int =
        sp.getInt(keyCurrent(partKey), -1)

    fun lastStableVersion(partKey: String): Int =
        sp.getInt(keyStable(partKey), -1)

    fun setCurrent(partKey: String, versionCode: Int) {
        sp.edit().putInt(keyCurrent(partKey), versionCode).apply()
    }

    /** 插件运行超过 stableThresholdMillis 不崩，业务可调此方法把当前版本标记为稳定。*/
    fun markCurrentAsStable(partKey: String) {
        val current = currentVersion(partKey)
        if (current > 0) {
            sp.edit().putInt(keyStable(partKey), current).apply()
        }
    }

    private fun keyCurrent(partKey: String) = "current_$partKey"
    private fun keyStable(partKey: String) = "stable_$partKey"
}
