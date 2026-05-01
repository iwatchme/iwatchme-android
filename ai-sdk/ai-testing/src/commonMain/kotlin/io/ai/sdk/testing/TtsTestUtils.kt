package io.ai.sdk.testing

import io.ai.sdk.tts.TtsItem
import io.ai.sdk.tts.TtsVoiceParams
import io.ai.sdk.tts.TtsSdkParams

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
