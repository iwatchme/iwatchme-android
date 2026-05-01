package io.tts.sdk.core

import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random

class TtsCache(
    private val cacheDirPath: String,
    private val maxSizeBytes: Long = 500L * 1024 * 1024,
    private val strategy: TtsCacheStrategy = LruCacheStrategy(),
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) {
    private val cacheDir get() = cacheDirPath.toPath()

    fun get(key: TtsCacheKey): String? {
        val file = cacheDir / key.toFileName()
        if (!fileSystem.exists(file)) return null
        val size = fileSystem.metadata(file).size ?: 0L
        return if (size > 0) file.toString() else null
    }

    fun createTemp(key: TtsCacheKey): String {
        fileSystem.createDirectories(cacheDir)
        val name = "tts_${Random.nextLong().toULong()}.tmp"
        val tempPath = cacheDir / name
        fileSystem.write(tempPath) { /* create empty file */ }
        return tempPath.toString()
    }

    fun commit(key: TtsCacheKey, tempFilePath: String): String {
        val target = cacheDir / key.toFileName()
        fileSystem.atomicMove(tempFilePath.toPath(), target)
        trimIfNeeded()
        return target.toString()
    }

    fun put(key: TtsCacheKey, sourceFilePath: String): String? {
        val sourcePath = sourceFilePath.toPath()
        if (!fileSystem.exists(sourcePath)) return null
        val size = fileSystem.metadata(sourcePath).size ?: 0L
        if (size == 0L) return null
        val tempPath = createTemp(key)
        fileSystem.copy(sourcePath, tempPath.toPath())
        return commit(key, tempPath)
    }

    internal fun trimIfNeeded() {
        if (!fileSystem.exists(cacheDir)) return
        val files = fileSystem.list(cacheDir)
            .filter { !it.name.endsWith(".tmp") }
            .mapNotNull { path ->
                val meta = fileSystem.metadata(path)
                val size = meta.size ?: return@mapNotNull null
                CacheFileInfo(
                    path = path.toString(),
                    sizeBytes = size,
                    lastModifiedMillis = meta.lastModifiedAtMillis ?: 0L,
                )
            }
        val totalSize = files.sumOf { it.sizeBytes }
        val toEvict = strategy.selectFilesToEvict(files, totalSize, maxSizeBytes)
        toEvict.forEach { fileSystem.delete(it.path.toPath()) }
    }

    fun delete(key: TtsCacheKey) {
        fileSystem.delete(cacheDir / key.toFileName())
    }

    fun clear() {
        if (!fileSystem.exists(cacheDir)) return
        fileSystem.list(cacheDir).forEach { fileSystem.delete(it) }
    }
}
