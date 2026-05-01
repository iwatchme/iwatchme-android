package io.ai.sdk.translation

data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val sourceLang: String,
    val targetLang: String,
    val error: Exception? = null,
) {
    val isSuccess: Boolean get() = error == null
}
