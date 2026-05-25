package com.iwatchme.host.shadow

import android.os.Build
import com.tencent.shadow.dynamic.host.PluginManagerUpdater
import java.io.File
import java.util.concurrent.Future

/**
 * 已下载到本地的 manager.apk 直接喂给 Shadow，不再做后台更新轮询。
 * 升级逻辑放在外部（[PluginUpdateService]）：等业务调 checkAndDownload(partKey="plugin-manager")
 * 拿到新文件后，重建一个 [FixedFilePluginManagerUpdater] 再交给 [HostShadowInitializer.loadPluginManager]。
 *
 * 仿 vendor/Shadow sample-host 的 FixedPathPmUpdater，但适配我们后端驱动的下发流程。
 */
class FixedFilePluginManagerUpdater(private val managerApk: File) : PluginManagerUpdater {

    init {
        // API 33+ 要求 .apk 必须不可写入才能被 ClassLoader 加载
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU) {
            @Suppress("ResultOfMethodCallIgnored")
            managerApk.setWritable(false)
        }
    }

    override fun wasUpdating(): Boolean = false
    override fun update(): Future<File>? = null
    override fun getLatest(): File? = if (managerApk.exists()) managerApk else null
    override fun isAvailable(file: File): Future<Boolean>? = null
}
