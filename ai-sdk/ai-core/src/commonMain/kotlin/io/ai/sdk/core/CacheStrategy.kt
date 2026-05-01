package io.ai.sdk.core

fun interface CacheStrategy {
    fun selectFilesToEvict(
        files: List<CacheFileInfo>,
        totalSize: Long,
        maxSizeBytes: Long,
    ): List<CacheFileInfo>
}
