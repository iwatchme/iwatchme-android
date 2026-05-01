package io.tts.sdk.core

import kotlinx.coroutines.CoroutineDispatcher
import okio.FileSystem

expect val ioDispatcher: CoroutineDispatcher

expect fun platformMd5(input: String): String

expect val platformFileSystem: FileSystem
