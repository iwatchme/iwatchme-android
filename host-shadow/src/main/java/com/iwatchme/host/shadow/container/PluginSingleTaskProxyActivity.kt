package com.iwatchme.host.shadow.container

import android.annotation.SuppressLint
import com.tencent.shadow.core.runtime.container.PluginContainerActivity

@SuppressLint("Registered")
open class PluginSingleTaskProxyActivity : PluginContainerActivity() {
    override fun getDelegateProviderKey(): String = "iwatchme-plugin-default"
}

@SuppressLint("Registered")
class PluginSingleTaskProxyActivity0 : PluginSingleTaskProxyActivity()

@SuppressLint("Registered")
class PluginSingleTaskProxyActivity1 : PluginSingleTaskProxyActivity()
