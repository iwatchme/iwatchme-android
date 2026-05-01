package io.tts.sdk.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LargestFirstCacheStrategyTest {

    private val strategy = LargestFirstCacheStrategy()

    private fun fileInfo(name: String, size: Long) =
        CacheFileInfo(path = "/cache/$name", sizeBytes = size, lastModifiedMillis = 0)

    @Test
    fun returnsEmptyWhenUnderLimit() {
        val files = listOf(fileInfo("a.mp3", 50), fileInfo("b.mp3", 50))
        val result = strategy.selectFilesToEvict(files, 100, 200)
        assertTrue(result.isEmpty())
    }

    @Test
    fun returnsEmptyWhenExactlyAtLimit() {
        val files = listOf(fileInfo("a.mp3", 100))
        val result = strategy.selectFilesToEvict(files, 100, 100)
        assertTrue(result.isEmpty())
    }

    @Test
    fun evictsLargestFileFirst() {
        val small = fileInfo("small.mp3", 10)
        val large = fileInfo("large.mp3", 100)
        val medium = fileInfo("medium.mp3", 50)
        val files = listOf(small, large, medium)

        val result = strategy.selectFilesToEvict(files, 160, 100)
        assertEquals(listOf(large), result)
    }

    @Test
    fun evictsMultipleFilesLargestFirstUntilUnderLimit() {
        val f1 = fileInfo("1.mp3", 10)
        val f2 = fileInfo("2.mp3", 80)
        val f3 = fileInfo("3.mp3", 50)
        val f4 = fileInfo("4.mp3", 30)
        val files = listOf(f1, f2, f3, f4)

        val result = strategy.selectFilesToEvict(files, 170, 50)
        assertEquals(listOf(f2, f3), result)
    }

    @Test
    fun evictsAllFilesIfAllNeeded() {
        val f1 = fileInfo("1.mp3", 100)
        val f2 = fileInfo("2.mp3", 50)
        val files = listOf(f1, f2)

        val result = strategy.selectFilesToEvict(files, 150, 0)
        assertEquals(listOf(f1, f2), result)
    }

    @Test
    fun handlesEmptyFileList() {
        val result = strategy.selectFilesToEvict(emptyList(), 0, 100)
        assertTrue(result.isEmpty())
    }
}
