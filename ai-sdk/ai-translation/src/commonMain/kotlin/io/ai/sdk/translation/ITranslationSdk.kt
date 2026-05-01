package io.ai.sdk.translation

interface ITranslationSdk {
    suspend fun translate(params: TranslationParams): String
    fun release()
}
