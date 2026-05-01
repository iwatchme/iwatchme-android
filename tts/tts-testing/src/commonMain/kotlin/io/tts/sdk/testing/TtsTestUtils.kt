package io.tts.sdk.testing

import io.tts.sdk.TtsItem
import io.tts.sdk.TtsVoiceParams
import io.tts.sdk.core.TtsSdkParams

object TtsTestUtils {
    fun makeParams(
        text: String = "hello",
        source: Int = 1,
        voiceType: String = "default",
        cacheDirPath: String = "/tmp/tts-test-cache",
    ) = TtsSdkParams(
        text = text,
        source = source,
        voiceType = voiceType,
        cacheDirPath = cacheDirPath,
    )

    fun makeItem(text: String = "hello", source: Int = 1) = TtsItem(text = text, source = source)

    fun makeVoice(voiceType: String = "default") = TtsVoiceParams(voiceType = voiceType)
}
