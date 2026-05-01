package io.tts.sdk.core

data class TtsCacheKey(
    val textMd5: String,
    val source: Int,
    val voiceType: String,
    val volume: String,
    val rate: Int,
    val pitch: Int,
    val effect: String,
    val effectValue: String,
    val encodeType: String,
) {
    fun toFileName(): String =
        "${textMd5}${SEP}${source}${SEP}${voiceType}${SEP}${volume}${SEP}${rate}${SEP}${pitch}${SEP}${effect}${SEP}${effectValue}.${encodeType}"

    companion object {
        private const val SEP = "\u001F"

        fun from(params: TtsSdkParams): TtsCacheKey = TtsCacheKey(
            textMd5 = platformMd5(params.text),
            source = params.source,
            voiceType = params.voiceType,
            volume = params.volume ?: "",
            rate = params.rate,
            pitch = params.pitch,
            effect = params.effect ?: "",
            effectValue = params.effectValue ?: "",
            encodeType = params.encodeType,
        )
    }
}
