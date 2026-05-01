package io.tts.sdk.core

import kotlinx.coroutines.flow.Flow

interface ITtsSdk {
    fun handles(source: Int): Boolean
    suspend fun initialize()
    suspend fun synthesize(params: TtsSdkParams): String
    fun synthesizeStreaming(params: TtsSdkParams): Flow<ByteArray>
    fun release()
}
