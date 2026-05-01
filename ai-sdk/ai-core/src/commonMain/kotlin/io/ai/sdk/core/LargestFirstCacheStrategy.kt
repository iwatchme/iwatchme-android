package io.ai.sdk.core

class LargestFirstCacheStrategy : CacheStrategy {
    override fun selectFilesToEvict(
        files: List<CacheFileInfo>,
        totalSize: Long,
        maxSizeBytes: Long,
    ): List<CacheFileInfo> {
        if (totalSize <= maxSizeBytes) return emptyList()
        val toFree = totalSize - maxSizeBytes
        var freed = 0L
        return files.sortedByDescending { it.sizeBytes }.takeWhile { file ->
            if (freed >= toFree) return@takeWhile false
            freed += file.sizeBytes
            true
        }
    }
}
