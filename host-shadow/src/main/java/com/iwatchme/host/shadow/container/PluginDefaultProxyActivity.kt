package com.iwatchme.host.shadow.container

import android.annotation.SuppressLint
import com.tencent.shadow.core.runtime.container.PluginContainerActivity

/**
 * standard launchMode 壳子。注册在宿主 :app 的 AndroidManifest 里。
 * Shadow 通过 [getDelegateProviderKey] 找到 runtime 注册的 delegate 工厂，
 * 由 delegate 把壳子的生命周期转发给真插件 Activity。
 *
 * 壳子按需多注册几个：standard 模式下一个壳子只能承载一个插件 Activity 实例，
 * 同时栈里能有几个就要预声明几个。
 */
@SuppressLint("Registered")
open class PluginDefaultProxyActivity : PluginContainerActivity() {
    override fun getDelegateProviderKey(): String = DELEGATE_KEY

    companion object {
        const val DELEGATE_KEY: String = "iwatchme-plugin-default"
    }
}

@SuppressLint("Registered")
class PluginDefaultProxyActivity0 : PluginDefaultProxyActivity()

@SuppressLint("Registered")
class PluginDefaultProxyActivity1 : PluginDefaultProxyActivity()

@SuppressLint("Registered")
class PluginDefaultProxyActivity2 : PluginDefaultProxyActivity()

@SuppressLint("Registered")
class PluginDefaultProxyActivity3 : PluginDefaultProxyActivity()
