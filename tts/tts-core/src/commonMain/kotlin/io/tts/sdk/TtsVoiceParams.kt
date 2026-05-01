package io.tts.sdk

data class TtsVoiceParams(
    val voiceType: String,
    val engineName: String? = null,
    val volume: String? = null,
    val rate: Int = 0,
    val pitch: Int = 0,
    val effect: String? = null,
    val effectValue: String? = null,
    val sampleRate: Int = 24000,
    val encodeType: String = ENCODE_TYPE_MP3,
) {
    companion object {
        const val ENCODE_TYPE_MP3 = "mp3"
        const val ENCODE_TYPE_PCM = "pcm"
    }
}

const val ENCODE_TYPE_MP3 = TtsVoiceParams.ENCODE_TYPE_MP3
const val ENCODE_TYPE_PCM = TtsVoiceParams.ENCODE_TYPE_PCM
