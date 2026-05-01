package io.tts.sdk.core

import kotlinx.coroutines.CoroutineDispatcher

expect val ioDispatcher: CoroutineDispatcher

expect fun platformMd5(input: String): String
