package io.ai.sdk.testing

import io.ai.sdk.internal.AiSdkException
import io.ai.sdk.translation.TranslationParams
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeTranslationSdkTest {

    private fun makeParams(
        text: String = "hello",
        sourceLang: String = "english",
        targetLang: String = "chinese",
    ) = TranslationParams(text = text, sourceLang = sourceLang, targetLang = targetLang)

    @Test
    fun recordsAllTranslateCalls() = runTest {
        val sdk = FakeTranslationSdk()
        sdk.translate(makeParams("a"))
        sdk.translate(makeParams("b"))

        val calls = sdk.translateCalls()
        assertEquals(2, calls.size)
        assertEquals("a", calls[0].text)
        assertEquals("b", calls[1].text)
    }

    @Test
    fun returnsFakeTranslationOnSuccess() = runTest {
        val sdk = FakeTranslationSdk(fakeTranslation = "你好")
        val result = sdk.translate(makeParams())
        assertEquals("你好", result)
    }

    @Test
    fun throwsOnError() = runTest {
        val sdk = FakeTranslationSdk(behavior = AiSdkScenario.Error)
        val result = runCatching { sdk.translate(makeParams()) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AiSdkException)
    }

    @Test
    fun hangForeverCancelsCleanly() = runTest {
        val sdk = FakeTranslationSdk(behavior = AiSdkScenario.HangForever)
        val job = launch { sdk.translate(makeParams()) }
        delay(50)
        job.cancelAndJoin()
    }

    @Test
    fun releaseTracked() {
        val sdk = FakeTranslationSdk()
        assertFalse(sdk.isReleased)
        sdk.release()
        assertTrue(sdk.isReleased)
    }
}
