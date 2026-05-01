package io.tts.sdk.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TtsCacheKeyTest {

    @Test
    fun sameParamsProduceIdenticalFilename() {
        val params1 = TtsSdkParams(
            text = "hello", source = 1, voiceType = "abc",
            volume = "50", rate = 10, pitch = 5,
            effect = "reverb", effectValue = "0.5",
            cacheDirPath = "/tmp",
        )
        val params2 = params1.copy()
        assertEquals(
            TtsCacheKey.from(params1).toFileName(),
            TtsCacheKey.from(params2).toFileName(),
        )
    }

    @Test
    fun differentParamsProduceDifferentFilename() {
        val base = TtsSdkParams(
            text = "hello", source = 1, voiceType = "abc",
            volume = "50", rate = 10, pitch = 5,
            cacheDirPath = "/tmp",
        )
        val different = base.copy(text = "world")
        assertNotEquals(
            TtsCacheKey.from(base).toFileName(),
            TtsCacheKey.from(different).toFileName(),
        )
    }

    @Test
    fun noSeparatorCollisionBetweenAdjacentFields() {
        val params1 = TtsSdkParams(
            text = "t", source = 1, voiceType = "abc", volume = "1",
            cacheDirPath = "/tmp",
        )
        val params2 = TtsSdkParams(
            text = "t", source = 1, voiceType = "", volume = "1abc",
            cacheDirPath = "/tmp",
        )
        assertNotEquals(
            TtsCacheKey.from(params1).toFileName(),
            TtsCacheKey.from(params2).toFileName(),
        )
    }

    @Test
    fun differentSourceProducesDifferentFilename() {
        val base = TtsSdkParams(
            text = "hello", source = 1, voiceType = "abc",
            cacheDirPath = "/tmp",
        )
        val differentSource = base.copy(source = 2)
        assertNotEquals(
            TtsCacheKey.from(base).toFileName(),
            TtsCacheKey.from(differentSource).toFileName(),
        )
    }
}
