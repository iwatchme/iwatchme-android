package io.tts.sdk.core

data class TtsSdkParams(
    val text: String,
    val source: Int,
    val voiceType: String,
    val engineName: String? = null,
    val volume: String? = null,
    val rate: Int = 0,
    val pitch: Int = 0,
    val effect: String? = null,
    val effectValue: String? = null,
    val sampleRate: Int = 24000,
    val encodeType: String = "mp3",
    val cacheDirPath: String,
) {
    companion object {
        fun from(
            item: io.tts.sdk.TtsItem,
            voice: io.tts.sdk.TtsVoiceParams,
            source: Int,
            cacheDirPath: String,
        ) = TtsSdkParams(
            text = item.text,
            source = source,
            voiceType = voice.voiceType,
            engineName = voice.engineName,
            volume = voice.volume,
            rate = voice.rate,
            pitch = voice.pitch,
            effect = voice.effect,
            effectValue = voice.effectValue,
            sampleRate = voice.sampleRate,
            encodeType = voice.encodeType,
            cacheDirPath = cacheDirPath,
        )
    }
}
