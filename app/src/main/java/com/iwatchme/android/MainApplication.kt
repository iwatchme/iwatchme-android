package com.iwatchme.android

import android.app.Application
import com.iwatchme.host.shadow.HostShadowInitializer
import com.iwatchme.host.shadow.PluginPreloader
import com.iwatchme.host.shadow.PluginUpdateClient
import com.iwatchme.host.shadow.PluginUpdateService
import com.iwatchme.player.PlayerSdk
import com.iwatchme.startuplab.orchestration.JetpackStartupManager

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        JetpackStartupManager.start(this)
        PlayerSdk.init(this)
        HostShadowInitializer.init(this)

        if (HostShadowInitializer.isMainProcess) {
            // 把 Shadow 插件预下载挂到 JetpackStartupManager 的 idle 阶段：
            // 等 MainActivity.reportFullyDrawn() → markFullyDrawnReported() 触发 MessageQueue idle 后才跑，
            // 不抢首屏 UI 与关键启动任务的 IO/CPU。耗时会出现在 Startup Inspector 的 dashboard 笔记里。
            JetpackStartupManager.registerExternalIdleHook("shadow-preload") { app ->
                PluginPreloader(
                    context = app,
                    service = PluginUpdateService(app, PluginUpdateClient.create(SHADOW_BACKEND_URL)),
                    partKeys = listOf("plugin-manager", "demo"),
                    deviceId = "preload-${android.provider.Settings.Secure.getString(app.contentResolver, android.provider.Settings.Secure.ANDROID_ID).orEmpty()}",
                ).preloadAll()
            }
        }
    }

    companion object {
        private const val SHADOW_BACKEND_URL = "http://10.0.2.2:8081/"
    }
}
