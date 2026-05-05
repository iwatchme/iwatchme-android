package com.iwatchme.cocosshell.download

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Default downloader for the demo. Copies a bundled APK asset (`cocos-game.zip`)
 * into [destZip] while emitting smooth progress, so the entire end-to-end
 * pipeline (download → unzip → mount) runs without a network connection.
 *
 * The [url] argument is ignored — kept in the signature so the caller can
 * still flip downloaders behind the same interface.
 */
class AssetPackageDownloader(
    private val context: Context,
    private val assetZipPath: String = "cocos-game.zip",
) : PackageDownloader {

    override suspend fun download(
        url: String,
        destZip: File,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        destZip.parentFile?.mkdirs()
        if (destZip.exists()) destZip.delete()

        // We'd like a real progress signal — for that we need the total
        // size, which AssetManager.openFd() gives us cheaply when the
        // asset is uncompressed. For compressed assets the FD trick fails;
        // falling back to a single-shot copy with synthetic progress.
        val total = runCatching {
            context.assets.openFd(assetZipPath).use { it.length }
        }.getOrNull()

        if (total != null && total > 0) {
            context.assets.open(assetZipPath).use { input ->
                BufferedOutputStream(FileOutputStream(destZip)).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var copied = 0L
                    var n = input.read(buf)
                    while (n != -1) {
                        out.write(buf, 0, n)
                        copied += n
                        onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                        n = input.read(buf)
                        // Slight cadence so the bar visibly animates even
                        // for tiny payloads — this is a demo affordance.
                        if (n != -1) delay(20)
                    }
                }
            }
        } else {
            // Compressed-asset fallback: copy in one shot, fake progress.
            for (p in 1..10) { onProgress(p / 10f); delay(40) }
            context.assets.open(assetZipPath).use { input ->
                BufferedOutputStream(FileOutputStream(destZip)).use { out ->
                    input.copyTo(out)
                }
            }
        }
        onProgress(1f)
    }
}
