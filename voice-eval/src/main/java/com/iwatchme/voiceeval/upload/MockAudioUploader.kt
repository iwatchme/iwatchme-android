package com.iwatchme.voiceeval.upload

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 模拟七牛或任意一种对象存储 CDN：
 * 把源文件复制到 App 私有 files 目录下独立的 `uploaded/` 子目录，
 * 然后返回指向复制副本的 `file://` URL。模拟出的延迟让演示页的进度 UI 看起来更真实。
 *
 * 为什么要拷贝而不是直接返回原路径？因为生产环境里上传完成后，
 * 本地录音目录会被定期清理；保留一份副本让演示在「引擎已释放」之后仍能稳定回放。
 *
 * Pretends to be QiNiu (or any other object-store CDN). Copies the source
 * file to an isolated `uploaded/` directory under the app's private files
 * dir and returns a `file://` URL pointing at the copy. The simulated
 * latency makes the demo's progress UI realistic.
 *
 * Why copy instead of returning the original path? Production uploads are
 * destructive in the sense that the local recording dir is GC'd later; the
 * copy gives the demo something stable to play back even after the engine
 * has been released.
 */
class MockAudioUploader(
    context: Context,
    private val networkLatencyMs: Long = 600,
) : AudioUploader {

    private val uploadedDir: File =
        File(context.filesDir, "voice-eval/uploaded").apply { mkdirs() }

    override suspend fun upload(file: File, key: String): String = withContext(Dispatchers.IO) {
        delay(networkLatencyMs)
        require(file.exists()) { "Source file does not exist: ${file.absolutePath}" }

        val safeKey = key.replace('/', '_')
        val target = File(uploadedDir, "$safeKey.${file.extension.ifEmpty { "bin" }}")
        file.copyTo(target, overwrite = true)
        target.toURI().toString()
    }
}
