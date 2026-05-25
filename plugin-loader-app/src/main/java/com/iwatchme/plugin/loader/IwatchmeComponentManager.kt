package com.iwatchme.plugin.loader

import android.content.ComponentName
import android.content.Context
import com.tencent.shadow.core.loader.infos.ContainerProviderInfo
import com.tencent.shadow.core.loader.managers.ComponentManager

/**
 * 「插件 Activity ↔ 宿主壳子」映射表。
 * 壳子 Activity FQN 必须和 :app/AndroidManifest.xml 中注册的一致。
 * 默认插件 Activity 走 PluginDefaultProxyActivity0（standard 模式）。
 * 指定 launchMode 的插件 Activity 按 className 分发到对应壳子。
 */
class IwatchmeComponentManager(private val context: Context) : ComponentManager() {

    override fun onBindContainerActivity(pluginActivity: ComponentName): ComponentName {
        return when (pluginActivity.className) {
            SINGLE_TASK_PROBE_ACTIVITY -> ComponentName(context, SINGLE_TASK_ACTIVITY)
            else -> ComponentName(context, DEFAULT_ACTIVITY)
        }
    }

    override fun onBindContainerContentProvider(pluginContentProvider: ComponentName): ContainerProviderInfo {
        return ContainerProviderInfo(
            "com.tencent.shadow.core.runtime.container.PluginContainerContentProvider",
            "${context.packageName}.shadow.provider.dynamic",
        )
    }

    companion object {
        // 与 :host-shadow/container/PluginDefaultProxyActivity.kt 中的 0..3 个壳子对应
        private const val DEFAULT_ACTIVITY = "com.iwatchme.host.shadow.container.PluginDefaultProxyActivity0"
        private const val SINGLE_TASK_ACTIVITY = "com.iwatchme.host.shadow.container.PluginSingleTaskProxyActivity0"
        private const val SINGLE_TASK_PROBE_ACTIVITY = "com.iwatchme.plugin.demo.SingleTaskProbeActivity"
        // 备用：SingleInstance 壳子 —— 见 :host-shadow/src/main/AndroidManifest.xml
        // private const val SINGLE_INSTANCE_ACTIVITY = "com.iwatchme.host.shadow.container.PluginSingleInstanceProxyActivity0"
    }
}
