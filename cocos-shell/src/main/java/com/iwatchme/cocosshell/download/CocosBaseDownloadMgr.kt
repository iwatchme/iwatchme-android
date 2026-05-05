package com.iwatchme.cocosshell.download

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Public facade for the package download pipeline. Same name as the
 * original Jiliguala class so the resume story can point at it directly.
 *
 * Pipeline per call to [ensureGamePackage]:
 *
 *  ```
 *  cache hit?  ─► return Done(cached=true) immediately
 *      │
 *  miss
 *      ▼
 *  PackageDownloader.download   (state: Downloading 0..100)
 *      │
 *      ▼
 *  ZipExtractor.extract         (state: Unzipping 0..100)
 *      │
 *      ▼
 *  DownloadCache.markSuccess    (writes prefs + success.txt(md5(url)))
 *      │
 *      ▼
 *  Done(cached=false)
 *  ```
 *
 * On any failure the in-progress `gameDir` is wiped so the next call
 * cannot mistake a half-extracted dir for a valid cache.
 *
 * The listener is invoked on the calling coroutine context — IPC adapters
 * (`GameMessageService`) marshal the callback to the main thread before
 * forwarding via Messenger.
 */
class CocosBaseDownloadMgr(
    private val context: Context,
    private val downloader: PackageDownloader = AssetPackageDownloader(context),
    private val extractor: ZipExtractor = ZipExtractor(),
    private val cache: DownloadCache = DownloadCache(context),
) {

    suspend fun ensureGamePackage(
        url: String,
        name: String,
        listener: DownloadListener,
    ): DownloadResult = withContext(Dispatchers.IO) {
        cache.hit(url)?.let { gameDir ->
            listener.onState(DownloadState.Done(gameDir, cached = true))
            return@withContext DownloadResult.Success(gameDir, cached = true)
        }

        val zipFile = StorageLayout.zipFile(context, name)
        val gameDir = StorageLayout.gameDir(context)
        // Stale gameDir from a previous failed run, or from a cache miss
        // because the URL changed. Wipe before we fill it again.
        gameDir.deleteRecursively()

        try {
            listener.onState(DownloadState.Downloading(0))
            downloader.download(url, zipFile) { fraction ->
                listener.onState(DownloadState.Downloading((fraction * 100).toInt()))
            }

            listener.onState(DownloadState.Unzipping(0))
            extractor.extract(zipFile, gameDir) { fraction ->
                listener.onState(DownloadState.Unzipping((fraction * 100).toInt()))
            }

            cache.markSuccess(url)
            // Clean the temp zip — we have the extracted form now.
            zipFile.delete()

            listener.onState(DownloadState.Done(gameDir, cached = false))
            DownloadResult.Success(gameDir, cached = false)
        } catch (t: Throwable) {
            // Don't leave a poisoned cache.
            gameDir.deleteRecursively()
            zipFile.delete()
            listener.onState(DownloadState.Failed(t))
            DownloadResult.Failure(t.message ?: t.javaClass.simpleName)
        }
    }
}
