package io.tts.sdk.core

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TtsCacheTest {

    private lateinit var fs: FakeFileSystem
    private lateinit var cache: TtsCache
    private val cacheDirPath = "/cache"

    private val testParams = TtsSdkParams(
        text = "hello", source = 1, voiceType = "abc",
        volume = "50", rate = 10, pitch = 5,
        cacheDirPath = cacheDirPath,
    )
    private val testKey = TtsCacheKey.from(testParams)

    @BeforeTest
    fun setup() {
        fs = FakeFileSystem()
        fs.createDirectories(cacheDirPath.toPath())
        cache = TtsCache(cacheDirPath, fileSystem = fs)
    }

    @AfterTest
    fun tearDown() {
        fs.checkNoOpenFiles()
    }

    @Test
    fun getReturnsNullForMissingFile() {
        assertNull(cache.get(testKey))
    }

    @Test
    fun getReturnsNullForZeroLengthFile() {
        val path = cacheDirPath.toPath() / testKey.toFileName()
        fs.write(path) { /* empty */ }
        assertNull(cache.get(testKey))
    }

    @Test
    fun getReturnsValidFileAfterCommit() {
        val tempPath = cache.createTemp(testKey)
        fs.write(tempPath.toPath()) { writeUtf8("audio data") }
        val committedPath = cache.commit(testKey, tempPath)
        assertNotNull(cache.get(testKey))
        val content = fs.read(committedPath.toPath()) { readUtf8() }
        assertEquals("audio data", content)
    }

    @Test
    fun createTempAndCommitIsAtomicTempFileRenamed() {
        val tempPath = cache.createTemp(testKey)
        fs.write(tempPath.toPath()) { writeUtf8("data") }
        cache.commit(testKey, tempPath)
        assertTrue(!fs.exists(tempPath.toPath()))
    }

    @Test
    fun deleteRemovesCachedFile() {
        val tempPath = cache.createTemp(testKey)
        fs.write(tempPath.toPath()) { writeUtf8("data") }
        cache.commit(testKey, tempPath)
        assertNotNull(cache.get(testKey))
        cache.delete(testKey)
        assertNull(cache.get(testKey))
    }

    @Test
    fun clearRemovesAllFiles() {
        val key1 = testKey
        val key2 = TtsCacheKey.from(testParams.copy(text = "world"))

        val t1 = cache.createTemp(key1)
        fs.write(t1.toPath()) { writeUtf8("a") }
        cache.commit(key1, t1)

        val t2 = cache.createTemp(key2)
        fs.write(t2.toPath()) { writeUtf8("b") }
        cache.commit(key2, t2)

        assertEquals(2, fs.list(cacheDirPath.toPath()).size)
        cache.clear()
        assertEquals(0, fs.list(cacheDirPath.toPath()).size)
    }

    @Test
    fun trimIfNeededEvictsOldestFilesWhenOverLimit() {
        val smallCache = TtsCache(cacheDirPath, maxSizeBytes = 10, fileSystem = fs)

        for (i in 1..3) {
            val params = testParams.copy(text = "text$i")
            val key = TtsCacheKey.from(params)
            val tempPath = smallCache.createTemp(key)
            fs.write(tempPath.toPath()) { write(ByteArray(5)) }
            smallCache.commit(key, tempPath)
        }

        smallCache.trimIfNeeded()

        val remaining = fs.list(cacheDirPath.toPath()).filter { !it.name.endsWith(".tmp") }
        val totalSize = remaining.sumOf { fs.metadata(it).size ?: 0L }
        assertTrue(totalSize <= 10)
    }

    @Test
    fun customStrategyIsCalledOnCommit() {
        val evicted = mutableListOf<CacheFileInfo>()
        val customStrategy = TtsCacheStrategy { files, _, _ ->
            files.also { evicted.addAll(it) }
        }
        val customCache = TtsCache(cacheDirPath, maxSizeBytes = 10, strategy = customStrategy, fileSystem = fs)

        val tempPath = customCache.createTemp(testKey)
        fs.write(tempPath.toPath()) { write(ByteArray(5)) }
        customCache.commit(testKey, tempPath)

        assertTrue(evicted.isNotEmpty())
    }

    @Test
    fun customStrategyControlsWhichFilesAreEvicted() {
        val noEvictCache = TtsCache(
            cacheDirPath, maxSizeBytes = 1,
            strategy = { _, _, _ -> emptyList() },
            fileSystem = fs,
        )

        for (i in 1..3) {
            val params = testParams.copy(text = "text$i")
            val key = TtsCacheKey.from(params)
            val tempPath = noEvictCache.createTemp(key)
            fs.write(tempPath.toPath()) { write(ByteArray(10)) }
            noEvictCache.commit(key, tempPath)
        }

        val remaining = fs.list(cacheDirPath.toPath()).filter { !it.name.endsWith(".tmp") }
        assertEquals(3, remaining.size)
    }
}
