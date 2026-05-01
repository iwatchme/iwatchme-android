package io.tts.sdk

import io.tts.sdk.testing.FakeCacheStrategy
import io.tts.sdk.testing.FakeTtsSdk
import io.tts.sdk.testing.TtsSdkScenario
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TtsEngineTest {

    private val cacheDirPath = "/tmp/tts-engine-test"
    private lateinit var engine: TtsEngine

    @AfterTest
    fun tearDown() {
        if (::engine.isInitialized) runBlocking { engine.close() }
    }

    private fun buildEngine(sdk: FakeTtsSdk, source: Int = 1): TtsEngine {
        engine = TtsEngine.Builder()
            .sdk(source, sdk)
            .cacheDirPath(cacheDirPath)
            .build()
        return engine
    }

    @Test
    fun generateReturnsResultsWithFilePaths(): Unit = runBlocking {
        val sdk = FakeTtsSdk(fakeFilePath = "/out/test.mp3")
        val engine = buildEngine(sdk)
        val voice = TtsVoiceParams(voiceType = "default")
        val items = listOf(TtsItem("hello", 1), TtsItem("world", 1))

        val result = engine.generate(items, voice)

        assertEquals(2, result.size)
        result.forEach { assertEquals("/out/test.mp3", it.filePath) }
    }

    @Test
    fun generateReportsProgress(): Unit = runBlocking {
        val sdk = FakeTtsSdk()
        val engine = buildEngine(sdk)
        val voice = TtsVoiceParams(voiceType = "default")
        val items = (1..3).map { TtsItem("text$it", 1) }

        val mutex = Mutex()
        val progressUpdates = mutableListOf<Pair<Int, Int>>()
        engine.generate(items, voice) { completed, total ->
            runBlocking { mutex.withLock { progressUpdates.add(completed to total) } }
        }

        assertEquals(3, progressUpdates.size)
        assertTrue(progressUpdates.all { it.second == 3 })
    }

    @Test
    fun generateDeduplicatesByTextAndSource(): Unit = runBlocking {
        val sdk = FakeTtsSdk(fakeFilePath = "/out.mp3")
        val engine = buildEngine(sdk)
        val voice = TtsVoiceParams(voiceType = "default")
        val items = listOf(TtsItem("same", 1), TtsItem("same", 1), TtsItem("different", 1))

        val result = engine.generate(items, voice)

        assertEquals(2, sdk.synthesizeCalls().size)
        assertEquals(3, result.size)
        result.forEach { assertEquals("/out.mp3", it.filePath) }
    }

    @Test
    fun generateWithEmptyListReturnsImmediately(): Unit = runBlocking {
        val sdk = FakeTtsSdk()
        val engine = buildEngine(sdk)
        val result = engine.generate(emptyList(), TtsVoiceParams(voiceType = "default"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun closeReleasesSdk(): Unit = runBlocking {
        val sdk = FakeTtsSdk()
        val engine = buildEngine(sdk)
        engine.generate(listOf(TtsItem("hello", 1)), TtsVoiceParams(voiceType = "default"))
        engine.close()
        assertEquals(1, sdk.releaseCallCount)
    }

    @Test
    fun previewEmitsAudioChunkThenStreamEnd(): Unit = runBlocking {
        val chunks = listOf(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6))
        val sdk = FakeTtsSdk(streamChunks = chunks, fakeFilePath = "/preview.mp3")
        val engine = buildEngine(sdk)
        val voice = TtsVoiceParams(voiceType = "default")

        val events = mutableListOf<TtsEvent>()
        engine.preview("hello", 1, voice).collect { events.add(it) }

        val audioChunks = events.filterIsInstance<TtsEvent.AudioChunk>()
        assertEquals(2, audioChunks.size)
        assertEquals(TtsEvent.AudioChunk(byteArrayOf(1, 2, 3)), audioChunks[0])
        assertTrue(events.last() is TtsEvent.StreamEnd)
    }

    @Test
    fun previewEmitsErrorOnFailure(): Unit = runBlocking {
        val sdk = FakeTtsSdk(behavior = TtsSdkScenario.Error)
        val engine = buildEngine(sdk)
        val voice = TtsVoiceParams(voiceType = "default")

        val events = mutableListOf<TtsEvent>()
        engine.preview("hello", 1, voice).collect { events.add(it) }

        assertTrue(events.last() is TtsEvent.Error)
    }

    @Test
    fun builderAcceptsCustomCacheStrategy() {
        val fakeStrategy = FakeCacheStrategy()
        val sdk = FakeTtsSdk()
        engine = TtsEngine.Builder()
            .sdk(1, sdk)
            .cacheDirPath(cacheDirPath)
            .cacheStrategy(fakeStrategy)
            .build()
        assertNotNull(engine)
    }
}
