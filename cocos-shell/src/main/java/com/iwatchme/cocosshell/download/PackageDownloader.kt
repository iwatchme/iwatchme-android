package com.iwatchme.cocosshell.download

import java.io.File

/**
 * Strategy for getting bytes from [url] into [destZip]. The two impls
 * shipped here are [AssetPackageDownloader] (default — copies a bundled
 * asset, lets the demo run offline) and [OkHttpPackageDownloader] (real
 * network). The original Jiliguala project only had one impl
 * (`DownloadUtil` over Retrofit/OkHttp); the Strategy split here exists
 * specifically so the demo can run on a flaky-WiFi laptop without any
 * network setup.
 */
interface PackageDownloader {
    /**
     * Stream [url] into [destZip], emitting normalized 0..1 progress.
     * Implementations are responsible for switching to a background
     * thread internally — callers may invoke this from any context.
     */
    suspend fun download(url: String, destZip: File, onProgress: (Float) -> Unit)
}
