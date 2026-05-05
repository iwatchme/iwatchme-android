package com.iwatchme.cocosshell.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Network-backed downloader using OkHttp's streaming `byteStream()`.
 * Mirrors the original `DownloadUtil` (Retrofit + OkHttp + `@Streaming`):
 *  - blocking I/O on a background dispatcher
 *  - server `Content-Length` drives progress when present
 *  - falls back to indeterminate (just emits 0 → 1) when the server omits
 *    the length header
 */
class OkHttpPackageDownloader(
    private val client: OkHttpClient = OkHttpClient(),
) : PackageDownloader {

    override suspend fun download(
        url: String,
        destZip: File,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        destZip.parentFile?.mkdirs()
        if (destZip.exists()) destZip.delete()

        val response = client.newCall(Request.Builder().url(url).build()).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code} for $url")
        }
        val body = response.body ?: throw IOException("empty body for $url")
        val total = body.contentLength()

        body.byteStream().use { input ->
            BufferedOutputStream(FileOutputStream(destZip)).use { out ->
                val buf = ByteArray(64 * 1024)
                var copied = 0L
                onProgress(0f)
                var n = input.read(buf)
                while (n != -1) {
                    out.write(buf, 0, n)
                    copied += n
                    if (total > 0) {
                        onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                    }
                    n = input.read(buf)
                }
            }
        }
        onProgress(1f)
        response.close()
    }
}
