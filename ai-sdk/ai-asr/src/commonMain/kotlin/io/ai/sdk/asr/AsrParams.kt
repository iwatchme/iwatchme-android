package io.ai.sdk.asr

data class AsrParams(
    val audioData: ByteArray,
    val language: String? = null,
    val contentType: String = "audio/mpeg",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AsrParams) return false
        return audioData.contentEquals(other.audioData) && language == other.language && contentType == other.contentType
    }
    override fun hashCode(): Int = audioData.contentHashCode() * 31 + (language?.hashCode() ?: 0)
}
