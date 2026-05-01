package io.ai.sdk.translation

data class TranslationParams(
    val text: String,
    val sourceLang: String,
    val targetLang: String,
)
