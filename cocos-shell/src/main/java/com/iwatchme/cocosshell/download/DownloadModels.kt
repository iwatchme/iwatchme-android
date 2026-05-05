package com.iwatchme.cocosshell.download

import java.io.File

/**
 * Sealed UI-facing state stream.
 *
 * Progress weighting matches the original `CocosBaseDownloadMgr`:
 *  - download spans 0..30% of the global bar
 *  - unzip spans 30..40% of the global bar
 *
 * The percent fields here are local to each phase (0..100) — the screen
 * decides how to compose them into a global bar (the constants live in
 * [DownloadProgress.DOWNLOAD_BAND] / [DownloadProgress.UNZIP_BAND]).
 */
sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val percent: Int) : DownloadState()
    data class Unzipping(val percent: Int) : DownloadState()
    data class Done(val gameDir: File, val cached: Boolean) : DownloadState()
    data class Failed(val error: Throwable) : DownloadState()
}

sealed class DownloadResult {
    data class Success(val gameDir: File, val cached: Boolean) : DownloadResult()
    data class Failure(val message: String) : DownloadResult()
}

fun interface DownloadListener {
    fun onState(state: DownloadState)
}

object DownloadProgress {
    const val DOWNLOAD_BAND_FRACTION = 0.30f   // 0.00..0.30 of global progress
    const val UNZIP_BAND_FRACTION    = 0.10f   // 0.30..0.40 of global progress

    /**
     * Compose the local-phase fractions into a 0..100 global integer.
     * The screen prefers calling this so the math lives in one place.
     */
    fun globalPercent(downloadFraction: Float, unzipFraction: Float): Int {
        val global = downloadFraction.coerceIn(0f, 1f) * DOWNLOAD_BAND_FRACTION +
            unzipFraction.coerceIn(0f, 1f) * UNZIP_BAND_FRACTION
        return (global * 100f).toInt().coerceIn(0, 100)
    }
}
