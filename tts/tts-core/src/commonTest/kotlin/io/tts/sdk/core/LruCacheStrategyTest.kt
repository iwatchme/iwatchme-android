package io.tts.sdk.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LruCacheStrategyTest {

    private val strategy = LruCacheStrategy()

    private fun fileInfo(name: String, size: Long, lastModified: Long) =
        CacheFileInfo(path = "/cache/$name", sizeBytes = size, lastModifiedMillis = lastModified)

    @Test
    fun returnsEmptyWhenUnderLimit() {
        val files = listOf(fileInfo("a.mp3", 100, 1000), fileInfo("b.mp3", 100, 2000))
        val result = strategy.selectFilesToEvict(files, 200, 500)
        assertTrue(result.isEmpty())
    }

    @Test
    fun returnsEmptyWhenExactlyAtLimit() {
        val files = listOf(fileInfo("a.mp3", 500, 1000))
        val result = strategy.selectFilesToEvict(files, 500, 500)
        assertTrue(result.isEmpty())
    }

    @Test
    fun evictsOldestFilesFirst() {
        val oldest = fileInfo("old.mp3", 100, 1000)
        val middle = fileInfo("mid.mp3", 100, 2000)
        val newest = fileInfo("new.mp3", 100, 3000)
        val files = listOf(oldest, middle, newest)

        val result = strategy.selectFilesToEvict(files, 300, 250)
        assertEquals(listOf(oldest), result)
    }

    @Test
    fun evictsMultipleFilesUntilUnderLimit() {
        val f1 = fileInfo("1.mp3", 100, 1000)
        val f2 = fileInfo("2.mp3", 100, 2000)
        val f3 = fileInfo("3.mp3", 100, 3000)
        val files = listOf(f1, f2, f3)

        val result = strategy.selectFilesToEvict(files, 300, 100)
        assertEquals(listOf(f1, f2), result)
    }

    @Test
    fun evictsAllFilesIfAllNeeded() {
        val f1 = fileInfo("1.mp3", 100, 1000)
        val f2 = fileInfo("2.mp3", 100, 2000)
        val files = listOf(f1, f2)

        val result = strategy.selectFilesToEvict(files, 200, 0)
        assertEquals(listOf(f1, f2), result)
    }

    @Test
    fun handlesEmptyFileList() {
        val result = strategy.selectFilesToEvict(emptyList(), 0, 100)
        assertTrue(result.isEmpty())
    }
}
