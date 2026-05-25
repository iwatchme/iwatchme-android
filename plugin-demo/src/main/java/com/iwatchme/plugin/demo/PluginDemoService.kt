package com.iwatchme.plugin.demo

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 插件 Service 示例。
 *
 * 验证：
 *  1. Shadow `PluginServiceManager` 能把插件 startService(intent) 路由到这里
 *  2. kotlinx.coroutines 在插件 classpath 内正常工作（不需要 host 注入）
 *  3. Service 跑在 :plugin 进程，崩了不连累 host
 *
 * Shadow Transform 会把这个 Service 改成继承 ShadowService。源码里仍写 android.app.Service。
 */
class PluginDemoService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate; coroutines start ticking inside :plugin process pid=${android.os.Process.myPid()}")
        scope.launch {
            for (i in 1..5) {
                delay(1000)
                Log.i(TAG, "coroutine tick $i / 5 thread=${Thread.currentThread().name}")
            }
            Log.i(TAG, "coroutine done; stopSelf()")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand startId=$startId")
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        Log.i(TAG, "onDestroy; coroutine scope cancelled")
        super.onDestroy()
    }

    companion object {
        const val TAG = "PluginDemoService"
    }
}
