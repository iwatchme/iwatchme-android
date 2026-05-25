package com.iwatchme.plugin.loader

import android.content.Context
import com.tencent.shadow.core.loader.ShadowPluginLoader
import com.tencent.shadow.core.loader.managers.ComponentManager

/**
 * 业务侧 PluginLoader 实现。对照 sample-loader/SamplePluginLoader.java 的简化 Kotlin 移植。
 * 与 sample 相比，本期不依赖 LoadPluginCallback（host 侧未暴露这个回调），用 Logger 替代即可。
 */
class IwatchmePluginLoader(hostAppContext: Context) : ShadowPluginLoader(hostAppContext) {

    private val componentManager = IwatchmeComponentManager(hostAppContext)

    override fun getComponentManager(): ComponentManager = componentManager

    /** 必须与 :host-shadow 里 PluginContainerActivity 子类的 getDelegateProviderKey() 返回值一致 */
    override val delegateProviderKey: String = "iwatchme-plugin-default"
}
