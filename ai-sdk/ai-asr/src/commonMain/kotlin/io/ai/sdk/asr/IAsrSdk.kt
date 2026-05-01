package io.ai.sdk.asr

interface IAsrSdk {
    suspend fun recognize(params: AsrParams): AsrResult
    fun release()
}
