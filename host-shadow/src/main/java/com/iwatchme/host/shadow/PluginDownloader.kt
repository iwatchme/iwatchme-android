package com.iwatchme.host.shadow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * 流式下载插件 zip 并做 md5 校验。
 * 复用 cocos-shell 的 [com.iwatchme.cocosshell.download.OkHttpPackageDownloader] 思路，
 * 但这里不依赖 :cocos-shell，避免循环依赖；写法保持一致。
 */
class PluginDownloader(
    private val client: OkHttpClient = OkHttpClient(),
    private val bufferSize: Int = 64 * 1024,
) {

    suspend fun download(
        url: String,
        target: File,
        expectedMd5: String,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            target.parentFile?.mkdirs()
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} from $url")
                }
                val body = response.body ?: throw IOException("empty body from $url")
                val total = body.contentLength().coerceAtLeast(0L)
                val digest = MessageDigest.getInstance("MD5")
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buf = ByteArray(bufferSize)
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            output.write(buf, 0, read)
                            digest.update(buf, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(expectedMd5, ignoreCase = true)) {
                    target.delete()
                    throw IOException("md5 mismatch: expected=$expectedMd5 actual=$actual")
                }
            }
            target
        }
    }
}
