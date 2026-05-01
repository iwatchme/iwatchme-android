package io.ai.sdk.testing

import io.ai.sdk.asr.AsrParams
import io.ai.sdk.asr.AsrResult
import io.ai.sdk.internal.AiSdkException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeAsrSdkTest {

    private fun makeParams(audio: ByteArray = byteArrayOf(1, 2, 3)) =
        AsrParams(audioData = audio)

    @Test
    fun recordsAllRecognizeCalls() = runTest {
        val sdk = FakeAsrSdk()
        sdk.recognize(makeParams(byteArrayOf(1)))
        sdk.recognize(makeParams(byteArrayOf(2)))

        val calls = sdk.recognizeCalls()
        assertEquals(2, calls.size)
    }

    @Test
    fun returnsFakeResultOnSuccess() = runTest {
        val expected = AsrResult(text = "hello world", wordCount = 2)
        val sdk = FakeAsrSdk(fakeResult = expected)
        val result = sdk.recognize(makeParams())
        assertEquals("hello world", result.text)
        assertEquals(2, result.wordCount)
    }

    @Test
    fun throwsOnError() = runTest {
        val sdk = FakeAsrSdk(behavior = AiSdkScenario.Error)
        val result = runCatching { sdk.recognize(makeParams()) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AiSdkException)
    }

    @Test
    fun hangForeverCancelsCleanly() = runTest {
        val sdk = FakeAsrSdk(behavior = AiSdkScenario.HangForever)
        val job = launch { sdk.recognize(makeParams()) }
        delay(50)
        job.cancelAndJoin()
    }

    @Test
    fun releaseTracked() {
        val sdk = FakeAsrSdk()
        assertFalse(sdk.isReleased)
        sdk.release()
        assertTrue(sdk.isReleased)
    }
}
