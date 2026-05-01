package io.ai.sdk.core

import io.ai.sdk.internal.AiSdkException
import io.ai.sdk.tts.ITtsSdk
import io.ai.sdk.tts.TtsCacheKey
import io.ai.sdk.tts.TtsEngineCore
import io.ai.sdk.tts.TtsSdkParams
import io.ai.sdk.testing.FakeTtsSdk
import io.ai.sdk.testing.AiSdkScenario
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class TtsEngineCoreTest {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val fs = FakeFileSystem()
    private val cacheDirPath = "/cache"

    init {
        fs.createDirectories(cacheDirPath.toPath())
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private fun makeParams(text: String = "hello", source: Int = 1) = TtsSdkParams(
        text = text, source = source, voiceType = "default",
        cacheDirPath = cacheDirPath,
    )

    @Test
    fun synthesizeOneReturnsPathOnSuccess(): Unit = runBlocking {
        val sdk = FakeTtsSdk(fakeFilePath = "/output/test.mp3")
        val pool = SdkPool<ITtsSdk>(factory = { sdk }, maxSize = 1)
        val core = TtsEngineCore(mapOf(1 to pool), scope)

        val result = core.synthesizeOne(makeParams())
        assertEquals("/output/test.mp3", result)
    }

    @Test
    fun synthesizeOneThrowsOnError(): Unit = runBlocking {
        val sdk = FakeTtsSdk(behavior = AiSdkScenario.Error)
        val pool = SdkPool<ITtsSdk>(factory = { sdk }, maxSize = 1)
        val core = TtsEngineCore(mapOf(1 to pool), scope)

        val result = runCatching { core.synthesizeOne(makeParams()) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AiSdkException)
    }

    @Test
    fun concurrencyIsBoundedByPoolMaxSize(): Unit = runBlocking {
        val mutex = Mutex()
        var concurrent = 0
        var maxConcurrent = 0

        val sdk = FakeTtsSdk(delay = 100.milliseconds, fakeFilePath = "/out.mp3")
        val pool = SdkPool<ITtsSdk>(
            factory = {
                object : ITtsSdk by sdk {
                    override suspend fun synthesize(params: TtsSdkParams): String {
                        val c = mutex.withLock { ++concurrent }
                        mutex.withLock { if (c > maxConcurrent) maxConcurrent = c }
                        try {
                            return sdk.synthesize(params)
                        } finally {
                            mutex.withLock { concurrent-- }
                        }
                    }
                }
            },
            maxSize = 2,
        )
        val core = TtsEngineCore(mapOf(1 to pool), scope)

        val params = (1..5).map { makeParams("text$it") }
        core.generateBatch(params, null)

        assertTrue(maxConcurrent <= 12)
    }

    @Test
    fun generateBatchPreservesOrder(): Unit = runBlocking {
        val sdk = FakeTtsSdk(delay = 10.milliseconds)
        val pool = SdkPool<ITtsSdk>(factory = { sdk }, maxSize = 3)
        val core = TtsEngineCore(mapOf(1 to pool), scope)

        val params = (1..5).map { makeParams("text$it") }
        val results = core.generateBatch(params, null)

        assertEquals(5, results.size)
        results.forEach { assertTrue(it.isSuccess) }
    }

    @Test
    fun generateBatchReportsProgress(): Unit = runBlocking {
        val sdk = FakeTtsSdk()
        val pool = SdkPool<ITtsSdk>(factory = { sdk }, maxSize = 2)
        val core = TtsEngineCore(mapOf(1 to pool), scope)

        val mutex = Mutex()
        val progressUpdates = mutableListOf<Pair<Int, Int>>()
        val params = (1..3).map { makeParams("text$it") }
        core.generateBatch(params) { completed, total ->
            runBlocking { mutex.withLock { progressUpdates.add(completed to total) } }
        }

        assertEquals(3, progressUpdates.size)
        assertTrue(progressUpdates.all { it.second == 3 })
        assertEquals(setOf(1, 2, 3), progressUpdates.map { it.first }.toSet())
    }

    @Test
    fun errorInOneItemDoesNotCancelOthers(): Unit = runBlocking {
        val pool = SdkPool<ITtsSdk>(
            factory = {
                object : ITtsSdk {
                    override fun handles(source: Int) = true
                    override suspend fun initialize() {}
                    override fun release() {}
                    override suspend fun synthesize(params: TtsSdkParams): String {
                        if (params.text == "fail") throw AiSdkException(-1)
                        return "/ok.mp3"
                    }
                    override fun synthesizeStreaming(params: TtsSdkParams) = emptyFlow<ByteArray>()
                }
            },
            maxSize = 3,
        )
        val core = TtsEngineCore(mapOf(1 to pool), scope)

        val params = listOf(makeParams("ok1"), makeParams("fail"), makeParams("ok2"))
        val results = core.generateBatch(params, null)

        assertTrue(results[0].isSuccess)
        assertTrue(results[1].isFailure)
        assertTrue(results[2].isSuccess)
    }

    @Test
    fun cancelAllCancelsChildrenButScopeRemainsReusable(): Unit = runBlocking {
        val sdk = FakeTtsSdk(behavior = AiSdkScenario.HangForever)
        val pool = SdkPool<ITtsSdk>(factory = { sdk }, maxSize = 2)
        val core = TtsEngineCore(mapOf(1 to pool), scope)

        val job = scope.launch {
            core.generateBatch(listOf(makeParams()), null)
        }
        delay(100)
        core.cancelAll()
        job.join()

        assertTrue(scope.isActive)

        val sdk2 = FakeTtsSdk(fakeFilePath = "/reuse.mp3")
        val pool2 = SdkPool<ITtsSdk>(factory = { sdk2 }, maxSize = 1)
        val core2 = TtsEngineCore(mapOf(1 to pool2), scope)
        val result = core2.synthesizeOne(makeParams())
        assertEquals("/reuse.mp3", result)
    }

    @Test
    fun noSdkForSourceThrows(): Unit = runBlocking {
        val core = TtsEngineCore(emptyMap(), scope)
        val result = runCatching { core.synthesizeOne(makeParams(source = 99)) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AiSdkException)
    }

    @Test
    fun cacheHitSkipsSdkCall(): Unit = runBlocking {
        val cache = AiCache(cacheDirPath, fileSystem = fs)
        val params = makeParams("cached-text")
        val key = TtsCacheKey.from(params)

        val tempPath = cache.createTemp(key)
        fs.write(tempPath.toPath()) { writeUtf8("cached audio data") }
        cache.commit(key, tempPath)

        val sdk = FakeTtsSdk(fakeFilePath = "/should-not-be-called.mp3")
        val pool = SdkPool<ITtsSdk>(factory = { sdk }, maxSize = 1)
        val core = TtsEngineCore(mapOf(1 to pool), scope, cache)

        val result = core.synthesizeOne(params)

        assertTrue(result.endsWith(key.toFileName()))
        assertEquals(0, sdk.synthesizeCalls().size)
    }

    @Test
    fun cacheMissCallsSdkThenCachesResult(): Unit = runBlocking {
        val cache = AiCache(cacheDirPath, fileSystem = fs)

        val outputPath = "$cacheDirPath/sdk_output.mp3"
        fs.write(outputPath.toPath()) { writeUtf8("synthesized audio") }

        val sdk = FakeTtsSdk(fakeFilePath = outputPath)
        val pool = SdkPool<ITtsSdk>(factory = { sdk }, maxSize = 1)
        val core = TtsEngineCore(mapOf(1 to pool), scope, cache)

        val params = makeParams("new-text")
        val key = TtsCacheKey.from(params)

        val result = core.synthesizeOne(params)
        assertEquals(1, sdk.synthesizeCalls().size)
        assertEquals(outputPath, result)

        assertNotNull(cache.get(key))
    }

    @Test
    fun secondCallWithSameParamsHitsCache(): Unit = runBlocking {
        val cache = AiCache(cacheDirPath, fileSystem = fs)

        val outputPath = "$cacheDirPath/sdk_output.mp3"
        fs.write(outputPath.toPath()) { writeUtf8("synthesized audio") }

        val sdk = FakeTtsSdk(fakeFilePath = outputPath)
        val pool = SdkPool<ITtsSdk>(factory = { sdk }, maxSize = 1)
        val core = TtsEngineCore(mapOf(1 to pool), scope, cache)

        val params = makeParams("repeat-text")

        core.synthesizeOne(params)
        assertEquals(1, sdk.synthesizeCalls().size)

        val result2 = core.synthesizeOne(params)
        assertEquals(1, sdk.synthesizeCalls().size)
        val content = fs.read(result2.toPath()) { readUtf8() }
        assertEquals("synthesized audio", content)
    }
}
