package io.tts.sdk.testing

import io.tts.sdk.core.TtsSdkParams
import io.tts.sdk.internal.TtsSynthesisException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeTtsSdkTest {

    private fun makeParams(text: String = "hello") = TtsSdkParams(
        text = text, source = 1, voiceType = "default",
        cacheDirPath = "/tmp",
    )

    @Test
    fun recordsAllSynthesizeCalls() = runTest {
        val sdk = FakeTtsSdk()
        sdk.initialize()
        sdk.synthesize(makeParams("a"))
        sdk.synthesize(makeParams("b"))

        val calls = sdk.synthesizeCalls()
        assertEquals(2, calls.size)
        assertEquals("a", calls[0].text)
        assertEquals("b", calls[1].text)
    }

    @Test
    fun returnsFakeFilePathOnSuccess() = runTest {
        val sdk = FakeTtsSdk(fakeFilePath = "/my/path.mp3")
        sdk.initialize()
        val result = sdk.synthesize(makeParams())
        assertEquals("/my/path.mp3", result)
    }

    @Test
    fun throwsOnError() = runTest {
        val sdk = FakeTtsSdk(behavior = TtsSdkScenario.Error)
        sdk.initialize()
        val result = runCatching { sdk.synthesize(makeParams()) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is TtsSynthesisException)
    }

    @Test
    fun hangForeverCancelsCleanly() = runTest {
        val sdk = FakeTtsSdk(behavior = TtsSdkScenario.HangForever)
        sdk.initialize()

        val job = launch { sdk.synthesize(makeParams()) }
        delay(50)
        job.cancelAndJoin()
    }

    @Test
    fun streamingEmitsAllConfiguredChunks() = runTest {
        val chunks = listOf(byteArrayOf(1, 2), byteArrayOf(3, 4), byteArrayOf(5))
        val sdk = FakeTtsSdk(streamChunks = chunks)
        sdk.initialize()

        val emitted = sdk.synthesizeStreaming(makeParams()).toList()
        assertEquals(3, emitted.size)
        assertContentEquals(byteArrayOf(1, 2), emitted[0])
        assertContentEquals(byteArrayOf(3, 4), emitted[1])
        assertContentEquals(byteArrayOf(5), emitted[2])
    }

    @Test
    fun initializeAndReleaseCountsAreTracked() = runTest {
        val sdk = FakeTtsSdk()
        assertEquals(0, sdk.initializeCallCount)
        sdk.initialize()
        assertEquals(1, sdk.initializeCallCount)
        sdk.release()
        assertEquals(1, sdk.releaseCallCount)
    }

    @Test
    fun handlesReturnsTrueForConfiguredSources() {
        val sdk = FakeTtsSdk(handledSources = setOf(1, 3))
        assertTrue(sdk.handles(1))
        assertFalse(sdk.handles(2))
        assertTrue(sdk.handles(3))
    }
}
