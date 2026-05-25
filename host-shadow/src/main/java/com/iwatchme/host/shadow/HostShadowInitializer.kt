package com.iwatchme.host.shadow

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.StrictMode
import android.webkit.WebView
import com.iwatchme.host.shadow.safety.PluginCrashGuard
import com.iwatchme.host.shadow.safety.PluginDegradeManager
import com.iwatchme.host.shadow.safety.PluginVersionRegistry
import com.tencent.shadow.core.common.LoggerFactory
import com.tencent.shadow.dynamic.host.DynamicPluginManager
import com.tencent.shadow.dynamic.host.DynamicRuntime
import com.tencent.shadow.dynamic.host.PluginManager
import com.tencent.shadow.dynamic.host.PluginManagerUpdater
import kotlin.concurrent.thread

/**
 * 宿主进程内 Shadow 初始化入口。模仿 vendor/Shadow 的 sample-host/HostApplication.java 范式：
 * 1. 主进程做 Logger / Detect API 等常规初始化；
 * 2. ":plugin" 进程要调 [DynamicRuntime.recoveryRuntime] 兜底崩溃后系统恢复；
 * 3. 真正的 Manager 加载延迟到 [loadPluginManager] 调用时（拿到下载好的 manager.apk 后）。
 */
object HostShadowInitializer {

    @Volatile private var pluginManager: PluginManager? = null
    @Volatile private var currentManagerPath: String? = null

    @Volatile var isMainProcess: Boolean = false
        private set

    private lateinit var crashGuardInternal: PluginCrashGuard
    private lateinit var versionRegistryInternal: PluginVersionRegistry
    private lateinit var degradeManagerInternal: PluginDegradeManager

    val crashGuard get() = crashGuardInternal
    val versionRegistry get() = versionRegistryInternal
    val degradeManager get() = degradeManagerInternal

    fun init(application: Application) {
        val isPlugin = isPluginProcess(application)
        isMainProcess = !isPlugin
        // Shadow Logger 在两个进程都要设：DynamicRuntime / PluginProcessService 等 <clinit> 会调 getLogger
        try {
            LoggerFactory.setILoggerFactory(AndroidLogLoggerFactory)
        } catch (t: Throwable) {
            android.util.Log.w("HostShadowInitializer", "setILoggerFactory failed", t)
        }
        if (isPlugin) {
            // :plugin 进程：尽量做最少事情。recoveryRuntime 用 plain try/catch 包，避免 Kotlin lambda 触发
            // CallableReference <clinit>（API 37+ 多 dex + 进程隔离场景下会引发 IllegalAccessError）。
            try {
                DynamicRuntime.recoveryRuntime(application)
            } catch (t: Throwable) {
                android.util.Log.w("HostShadowInitializer", "recoveryRuntime failed in :plugin; will fall back to fresh bind", t)
            }
            return
        }
        // 主进程的常规初始化
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WebView.setDataDirectorySuffix(Application.getProcessName())
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder().detectNonSdkApiUsage().build(),
            )
        }
        crashGuardInternal = PluginCrashGuard()
        versionRegistryInternal = PluginVersionRegistry(application)
        degradeManagerInternal = PluginDegradeManager(crashGuardInternal)
    }

    /**
     * 当 [PluginUpdateService] 把 manager.apk 下载好后，由业务调用本方法初始化 PluginManager。
     *
     * 复用策略：当传入 updater 指向的 manager.apk 路径与上次相同（即同一份 manager），
     * 直接复用已有 DynamicPluginManager —— 这样省掉一次 [DynamicPluginManager] 反射构造 +
     * `md5File()` IO（首次实测 ~100ms+）。即使期间 :plugin 崩过，DynamicPluginManager 内
     * 的 mPpsController 会被 onServiceDisconnected 置空，下一次 enter() 会自动 rebind，
     * 不会拿到陈旧 binder。
     *
     * 当 manager.apk 升级（如 partKey=plugin-manager 上传了新 versionCode），新路径与
     * cached 不同，释放旧实例并重建。
     */
    @Synchronized
    fun loadPluginManager(updater: PluginManagerUpdater): PluginManager {
        val newPath = updater.getLatest()?.absolutePath
            ?: throw IllegalArgumentException("PluginManagerUpdater.getLatest() returned null")
        val cached = pluginManager
        if (cached != null && newPath == currentManagerPath) {
            return cached
        }
        if (cached is DynamicPluginManager) {
            try {
                cached.release()
            } catch (t: Throwable) {
                android.util.Log.w("HostShadowInitializer", "release old DynamicPluginManager failed", t)
            }
        }
        currentManagerPath = newPath
        return DynamicPluginManager(updater).also { pluginManager = it }
    }

    private fun isPluginProcess(context: Context): Boolean {
        val pid = Process.myPid()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val name = am.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName.orEmpty()
        return name.endsWith(":plugin")
    }
}
