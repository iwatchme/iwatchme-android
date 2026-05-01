package io.ai.sdk.tts

data class TtsGenerateResult(
    val text: String,
    val source: Int,
    val filePath: String? = null,
    val error: Exception? = null,
) {
    val isSuccess: Boolean get() = filePath != null && error == null
}
