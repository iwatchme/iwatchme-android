package io.tts.sdk.cloudflare

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.tts.sdk.core.ITtsSdk
import io.tts.sdk.core.TtsSdkParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import io.tts.sdk.core.platformFileSystem
import okio.Path.Companion.toPath
import okio.buffer

/**
 * Cloudflare Workers AI TTS implementation using Deepgram Aura-2.
 *
 * @param accountId Cloudflare Account ID
 * @param apiToken  Cloudflare Workers AI API Token
 * @param model     Model ID, defaults to Aura-2 English
 * @param speaker   Default speaker voice (40 voices available)
 */
class CloudflareTtsSdk(
    private val accountId: String,
    private val apiToken: String,
    private val model: String = MODEL_AURA_2_EN,
    private val speaker: String = "luna",
    private val sourceId: Int = SOURCE_CLOUDFLARE,
) : ITtsSdk {

    private var client: HttpClient? = null

    override fun handles(source: Int): Boolean = source == sourceId

    override suspend fun initialize() {
        client = HttpClient()
    }

    override suspend fun synthesize(params: TtsSdkParams): String {
        val httpClient = requireClient()
        val response = httpClient.post(buildUrl()) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiToken")
            setBody(buildRequestBody(params))
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw RuntimeException("Cloudflare TTS failed: ${response.status}, body=$errorBody")
        }

        val audioBytes = response.readRawBytes()
        val ext = if (params.encodeType == "pcm") "pcm" else "mp3"
        val filePath = "${params.cacheDirPath}/cf_${params.text.hashCode()}.$ext"
        val path = filePath.toPath()

        val sink = platformFileSystem.sink(path).buffer()
        try {
            sink.write(audioBytes)
            sink.flush()
        } finally {
            sink.close()
        }

        return filePath
    }

    override fun synthesizeStreaming(params: TtsSdkParams): Flow<ByteArray> = flow {
        val httpClient = requireClient()

        httpClient.preparePost(buildUrl()) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiToken")
            setBody(buildRequestBody(params))
        }.execute { response ->
            if (!response.status.isSuccess()) {
                throw RuntimeException("Cloudflare TTS streaming failed: ${response.status}")
            }

            val channel = response.bodyAsChannel()
            val buffer = ByteArray(STREAM_CHUNK_SIZE)

            while (!channel.isClosedForRead) {
                val bytesRead = channel.readAvailable(buffer)
                if (bytesRead > 0) {
                    emit(buffer.copyOf(bytesRead))
                }
            }
        }
    }

    override fun release() {
        client?.close()
        client = null
    }

    private fun requireClient(): HttpClient =
        client ?: throw IllegalStateException("CloudflareTtsSdk not initialized. Call initialize() first.")

    private fun buildUrl(): String =
        "https://api.cloudflare.com/client/v4/accounts/$accountId/ai/run/${model.replace("@", "%40")}"

    private fun buildRequestBody(params: TtsSdkParams): String {
        val voiceName = params.voiceType.ifEmpty { speaker }
        val encoding = if (params.encodeType == "pcm") "linear16" else "mp3"

        val sb = StringBuilder()
        sb.append("""{"text":${params.text.escapeJson()},"speaker":"$voiceName","encoding":"$encoding"""")
        if (encoding == "linear16") {
            sb.append(""","sample_rate":${params.sampleRate},"container":"none"""")
        }
        sb.append("}")
        return sb.toString()
    }

    companion object {
        const val SOURCE_CLOUDFLARE = 100
        const val MODEL_AURA_2_EN = "@cf/deepgram/aura-2-en"
        const val MODEL_AURA_2_ES = "@cf/deepgram/aura-2-es"
        const val MODEL_MELOTTS = "@cf/myshell-ai/melotts"

        private const val STREAM_CHUNK_SIZE = 8192

        /** Available Aura-2 speakers (40 voices) */
        val SPEAKERS = listOf(
            "amalthea", "andromeda", "apollo", "arcas", "aries",
            "asteria", "athena", "atlas", "aurora", "callista",
            "cora", "cordelia", "delia", "draco", "electra",
            "harmonia", "helena", "hera", "hermes", "hyperion",
            "iris", "janus", "juno", "jupiter", "luna",
            "mars", "minerva", "neptune", "odysseus", "ophelia",
            "orion", "orpheus", "pandora", "phoebe", "pluto",
            "saturn", "thalia", "theia", "vesta", "zeus",
        )
    }
}

private fun String.escapeJson(): String {
    val sb = StringBuilder("\"")
    for (c in this) {
        when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> sb.append(c)
        }
    }
    sb.append("\"")
    return sb.toString()
}
