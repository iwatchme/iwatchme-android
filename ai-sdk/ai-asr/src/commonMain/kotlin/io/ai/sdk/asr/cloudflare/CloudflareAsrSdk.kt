package io.ai.sdk.asr.cloudflare

import io.ai.sdk.asr.AsrParams
import io.ai.sdk.asr.AsrResult
import io.ai.sdk.asr.IAsrSdk
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class CloudflareAsrSdk(
    private val accountId: String,
    private val apiToken: String,
    private val model: String = MODEL_WHISPER,
) : IAsrSdk {

    private val client = HttpClient()

    override suspend fun recognize(params: AsrParams): AsrResult {
        val url = buildString {
            append("https://api.cloudflare.com/client/v4/accounts/$accountId/ai/run/${model.replace("@", "%40")}")
            if (params.language != null) {
                append("?language=${params.language}")
            }
        }

        val response = client.post(url) {
            header("Authorization", "Bearer $apiToken")
            contentType(ContentType.parse(params.contentType))
            setBody(params.audioData)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw RuntimeException("ASR failed: ${response.status}, body=$errorBody")
        }

        val body = response.bodyAsText()
        return parseAsrResponse(body)
    }

    override fun release() {
        client.close()
    }

    companion object {
        const val MODEL_WHISPER = "@cf/openai/whisper"
        const val MODEL_WHISPER_LARGE = "@cf/openai/whisper-large-v3-turbo"
        const val MODEL_WHISPER_TINY_EN = "@cf/openai/whisper-tiny-en"
        const val MODEL_NOVA_3 = "@cf/deepgram/nova-3"
    }
}

private fun parseAsrResponse(json: String): AsrResult {
    // Parse {"result":{"text":"...","word_count":N,"vtt":"..."}}
    val text = extractJsonString(json, "text") ?: ""
    val wordCount = extractJsonInt(json, "word_count") ?: 0
    val vtt = extractJsonString(json, "vtt")
    return AsrResult(text = text, wordCount = wordCount, vtt = vtt)
}

private fun extractJsonString(json: String, key: String): String? {
    val pattern = "\"$key\":"
    val idx = json.indexOf(pattern)
    if (idx == -1) return null
    val afterKey = json.substring(idx + pattern.length).trimStart()
    if (afterKey.startsWith("null")) return null
    if (!afterKey.startsWith("\"")) return null
    val sb = StringBuilder()
    var i = 1 // skip opening quote
    while (i < afterKey.length) {
        val c = afterKey[i]
        if (c == '\\' && i + 1 < afterKey.length) {
            when (afterKey[i + 1]) {
                '"' -> { sb.append('"'); i += 2 }
                '\\' -> { sb.append('\\'); i += 2 }
                'n' -> { sb.append('\n'); i += 2 }
                'r' -> { sb.append('\r'); i += 2 }
                't' -> { sb.append('\t'); i += 2 }
                else -> { sb.append(c); i++ }
            }
        } else if (c == '"') {
            break
        } else {
            sb.append(c)
            i++
        }
    }
    return sb.toString()
}

private fun extractJsonInt(json: String, key: String): Int? {
    val pattern = "\"$key\":"
    val idx = json.indexOf(pattern)
    if (idx == -1) return null
    val afterKey = json.substring(idx + pattern.length).trimStart()
    val numStr = afterKey.takeWhile { it.isDigit() || it == '-' }
    return numStr.toIntOrNull()
}
