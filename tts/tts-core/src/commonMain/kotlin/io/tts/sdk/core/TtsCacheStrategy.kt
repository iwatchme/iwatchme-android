package io.tts.sdk.core

fun interface TtsCacheStrategy {
    fun selectFilesToEvict(
        files: List<CacheFileInfo>,
        totalSize: Long,
        maxSizeBytes: Long,
    ): List<CacheFileInfo>
}
