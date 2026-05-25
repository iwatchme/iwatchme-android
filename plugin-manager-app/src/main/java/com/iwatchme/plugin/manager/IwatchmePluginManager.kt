package com.iwatchme.plugin.manager

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.iwatchme.plugin.manager.IwatchmePluginConstants.FROM_ID_CLOSE
import com.iwatchme.plugin.manager.IwatchmePluginConstants.FROM_ID_NOOP
import com.iwatchme.plugin.manager.IwatchmePluginConstants.FROM_ID_START_ACTIVITY
import com.iwatchme.plugin.manager.IwatchmePluginConstants.KEY_ACTIVITY_CLASSNAME
import com.iwatchme.plugin.manager.IwatchmePluginConstants.KEY_EXTRAS
import com.iwatchme.plugin.manager.IwatchmePluginConstants.KEY_PLUGIN_PART_KEY
import com.iwatchme.plugin.manager.IwatchmePluginConstants.KEY_PLUGIN_ZIP_PATH
import com.iwatchme.plugin.manager.IwatchmePluginConstants.PART_KEY_PLUGIN_MAIN
import com.tencent.shadow.dynamic.host.EnterCallback
import java.util.concurrent.Executors

/**
 * 业务侧 PluginManager 实现。在加载完成后通过 [com.tencent.shadow.dynamic.host.PluginManagerThatUseDynamicLoader.mPluginLoader]
 * 启动插件 Activity。对照 sample-manager/SamplePluginManager.java 的 Kotlin 移植。
 */
class IwatchmePluginManager(context: Context) : FastPluginManager(context) {

    private val executor = Executors.newSingleThreadExecutor()

    /** PluginManager 别名，用于区分不同 PluginManager 实现的数据存储路径 */
    override fun getName(): String = "iwatchme-dynamic-manager"

    /** 宿主中注册的 PluginProcessService 实现的类名 —— 必须与 :app manifest 中 service 一致 */
    override fun getPluginProcessServiceName(partKey: String): String =
        "com.iwatchme.host.shadow.IwatchmePluginProcessService"

    override fun enter(context: Context, fromId: Long, bundle: Bundle, callback: EnterCallback?) {
        when (fromId.toInt()) {
            FROM_ID_NOOP -> Unit
            FROM_ID_START_ACTIVITY -> onStartActivity(context, bundle, callback)
            FROM_ID_CLOSE -> close()
            else -> throw IllegalArgumentException("unsupported fromId=$fromId")
        }
    }

    private fun onStartActivity(context: Context, bundle: Bundle, callback: EnterCallback?) {
        val zipPath = requireNotNull(bundle.getString(KEY_PLUGIN_ZIP_PATH)) { "zipPath is null" }
        val partKey = bundle.getString(KEY_PLUGIN_PART_KEY) ?: PART_KEY_PLUGIN_MAIN
        val className = requireNotNull(bundle.getString(KEY_ACTIVITY_CLASSNAME)) { "className is null" }
        val extras = bundle.getBundle(KEY_EXTRAS)

        executor.execute {
            try {
                val installed = installPlugin(zipPath, null, true)
                loadPlugin(installed.UUID, partKey)
                callApplicationOnCreate(partKey)

                val pluginIntent = Intent().apply {
                    setClassName(context.packageName, className)
                    if (extras != null) replaceExtras(extras)
                }
                val intent = mPluginLoader.convertActivityIntent(pluginIntent)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                mPluginLoader.startActivityInPluginProcess(intent)
            } catch (e: Exception) {
                throw RuntimeException(e)
            } finally {
                callback?.onCloseLoadingView()
            }
        }
    }
}
