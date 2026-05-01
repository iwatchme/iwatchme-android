package io.tts.sdk.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.security.MessageDigest

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

actual fun platformMd5(input: String): String {
    val digest = MessageDigest.getInstance("MD5")
    return digest.digest(input.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
