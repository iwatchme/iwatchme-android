package com.iwatchme.plugin.manager

import android.content.Context
import android.os.RemoteException
import com.tencent.shadow.core.manager.installplugin.InstalledPlugin
import com.tencent.shadow.core.manager.installplugin.InstalledType
import com.tencent.shadow.core.manager.installplugin.PluginConfig
import com.tencent.shadow.dynamic.manager.PluginManagerThatUseDynamicLoader
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 直接对照 [vendor/Shadow sample-manager/FastPluginManager.java] 的 Kotlin 移植：
 * - 接收 zip 路径 → 调 [installPluginFromZip] 解压 / 校验 / 抽出 plugin/loader/runtime
 * - 并行 odex 三个 dex 以加速首次加载
 * - 抽 so 库到设备架构对应目录
 */
abstract class FastPluginManager(context: Context) : PluginManagerThatUseDynamicLoader(context) {

    private val pool = Executors.newFixedThreadPool(4)

    @Throws(Exception::class)
    fun installPlugin(zipPath: String, hash: String?, odex: Boolean): InstalledPlugin {
        val pluginConfig: PluginConfig = installPluginFromZip(File(zipPath), hash)
        val uuid = pluginConfig.UUID

        val futures = mutableListOf<java.util.concurrent.Future<*>>()
        val soFutures = mutableListOf<java.util.concurrent.Future<android.util.Pair<String, String>>>()

        if (pluginConfig.runTime != null && pluginConfig.pluginLoader != null) {
            futures += pool.submit {
                oDexPluginLoaderOrRunTime(uuid, InstalledType.TYPE_PLUGIN_RUNTIME, pluginConfig.runTime.file)
            }
            futures += pool.submit {
                oDexPluginLoaderOrRunTime(uuid, InstalledType.TYPE_PLUGIN_LOADER, pluginConfig.pluginLoader.file)
            }
        }
        for ((partKey, info) in pluginConfig.plugins) {
            val apkFile = info.file
            val extractSo = pool.submit<android.util.Pair<String, String>> { extractSo(uuid, partKey, apkFile) }
            futures += extractSo
            soFutures += extractSo
            if (odex) {
                futures += pool.submit { oDexPlugin(uuid, partKey, apkFile) }
            }
        }
        futures.forEach { it.get() }
        val soMap = HashMap<String, String>()
        soFutures.forEach { f ->
            val p = f.get()
            soMap[p.first] = p.second
        }
        onInstallCompleted(pluginConfig, soMap)
        return getInstalledPlugins(1)[0]
    }

    @Throws(RemoteException::class)
    protected fun callApplicationOnCreate(partKey: String) {
        val map = mPluginLoader.loadedPlugin
        val isCalled = map[partKey] as? Boolean ?: false
        if (!isCalled) {
            mPluginLoader.callApplicationOnCreate(partKey)
        }
    }

    @Throws(RemoteException::class, java.util.concurrent.TimeoutException::class,
        com.tencent.shadow.dynamic.host.FailedException::class)
    protected fun loadPlugin(uuid: String, partKey: String) {
        if (mPpsController == null) {
            bindPluginProcessService(getPluginProcessServiceName(partKey))
            waitServiceConnected(10, TimeUnit.SECONDS)
        }
        loadRunTime(uuid)
        loadPluginLoader(uuid)
        val map = mPluginLoader.loadedPlugin
        if (!map.containsKey(partKey)) {
            mPluginLoader.loadPlugin(partKey)
        }
    }

    protected abstract fun getPluginProcessServiceName(partKey: String): String
}
