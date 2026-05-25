package com.iwatchme.host.shadow.container

import android.annotation.SuppressLint
import com.tencent.shadow.core.runtime.container.PluginContainerActivity

@SuppressLint("Registered")
open class PluginSingleInstanceProxyActivity : PluginContainerActivity() {
    override fun getDelegateProviderKey(): String = "iwatchme-plugin-default"
}

@SuppressLint("Registered")
class PluginSingleInstanceProxyActivity0 : PluginSingleInstanceProxyActivity()
