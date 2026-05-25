package com.iwatchme.host.shadow

import android.content.Context
import java.io.File

/**
 * 插件更新流程门面：拉取最新版本元信息 → 下载 → 校验 md5 → 返回 zip 路径。
 * 加载/解压/反射等交给 Shadow Manager（见 [PluginManagerBridge]，待接入）。
 */
class PluginUpdateService(
    context: Context,
    private val api: PluginUpdateApi,
    private val downloader: PluginDownloader = PluginDownloader(),
) {

    private val rootDir = File(context.filesDir, "shadow_plugins").apply { mkdirs() }

    suspend fun checkAndDownload(
        partKey: String,
        deviceId: String,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Result<DownloadedPlugin> {
        val release = runCatching { api.latest(partKey, deviceId) }
            .getOrElse { return Result.failure(it) }

        val target = File(rootDir, "${release.partKey}-${release.versionCode}.zip")
        if (target.exists() && target.length() == release.fileSize) {
            return Result.success(DownloadedPlugin(release, target, fromCache = true))
        }
        return downloader.download(release.downloadUrl, target, release.md5, onProgress)
            .map { DownloadedPlugin(release, it, fromCache = false) }
    }

    data class DownloadedPlugin(
        val release: PluginReleaseDto,
        val file: File,
        val fromCache: Boolean,
    )
}
