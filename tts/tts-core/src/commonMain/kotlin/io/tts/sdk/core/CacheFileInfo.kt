package io.tts.sdk.core

data class CacheFileInfo(
    val path: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
)
