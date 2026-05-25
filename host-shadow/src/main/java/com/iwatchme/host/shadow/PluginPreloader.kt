package com.iwatchme.host.shadow

import android.content.Context
import android.os.Looper
import android.os.MessageQueue
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * App 启动后空闲时预下载 manager.apk + plugin.zip。
 * 把"首次冷启 5–10 秒"压缩到主流程之外，用户进 demo 屏时直接走 cache hit。
 *
 * 两种用法：
 *
 *  ① 接到项目的 JetpackStartupManager idle hook（推荐，跟项目"首屏画完再跑 idle 任务"语义对齐）
 *     ```
 *     val preloader = PluginPreloader(context, service, listOf("plugin-manager", "demo"), deviceId)
 *     JetpackStartupManager.registerExternalIdleHook("shadow-preload") { preloader.preloadAll() }
 *     ```
 *
 *  ② 独立运行（不依赖 StartupManager 的场景，例如测试或纯 host-shadow 工程）
 *     ```
 *     preloader.scheduleAtIdle(MainScope())
 *     ```
 *     内部用 [MessageQueue.IdleHandler] gate，避免抢启动期 CPU/IO。
 */
class PluginPreloader(
    private val context: Context,
    private val service: PluginUpdateService,
    private val partKeys: List<String>,
    private val deviceId: String,
) {

    /** 直接顺序跑预下载。调用者负责调度时机（在合适的 IO 协程里 call）。*/
    suspend fun preloadAll() {
        for (key in partKeys) {
            val t0 = System.currentTimeMillis()
            val result = service.checkAndDownload(partKey = key, deviceId = deviceId)
            val cost = System.currentTimeMillis() - t0
            if (result.isSuccess) {
                val d = result.getOrNull()
                Log.i(
                    TAG,
                    "preload ok partKey=$key fromCache=${d?.fromCache} size=${d?.file?.length()} cost=${cost}ms",
                )
            } else {
                val err = result.exceptionOrNull()
                Log.w(TAG, "preload failed partKey=$key cost=${cost}ms err=${err?.javaClass?.simpleName}: ${err?.message}")
            }
        }
    }

    /** 用法 ②：主线程空闲时（IdleHandler）触发 preloadAll，避免抢启动期资源。*/
    fun scheduleAtIdle(scope: CoroutineScope) {
        Looper.myQueue().addIdleHandler(
            object : MessageQueue.IdleHandler {
                override fun queueIdle(): Boolean {
                    scope.launch(Dispatchers.IO) { preloadAll() }
                    return false // 只触发一次
                }
            },
        )
    }

    companion object {
        private const val TAG = "PluginPreloader"
    }
}
