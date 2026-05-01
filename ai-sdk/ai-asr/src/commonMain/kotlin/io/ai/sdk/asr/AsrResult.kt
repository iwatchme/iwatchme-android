package io.ai.sdk.asr

data class AsrResult(
    val text: String = "",
    val wordCount: Int = 0,
    val vtt: String? = null,
    val error: Exception? = null,
) {
    val isSuccess: Boolean get() = error == null
}
