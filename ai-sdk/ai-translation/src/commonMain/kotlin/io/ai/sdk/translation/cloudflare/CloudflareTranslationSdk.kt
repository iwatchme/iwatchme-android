package io.ai.sdk.translation.cloudflare

import io.ai.sdk.translation.ITranslationSdk
import io.ai.sdk.translation.TranslationParams
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class CloudflareTranslationSdk(
    private val accountId: String,
    private val apiToken: String,
    private val model: String = MODEL_M2M100,
) : ITranslationSdk {

    private val client = HttpClient()

    override suspend fun translate(params: TranslationParams): String {
        val url = "https://api.cloudflare.com/client/v4/accounts/$accountId/ai/run/${model.replace("@", "%40")}"
        val body = """{"text":${params.text.escapeJson()},"source_lang":"${params.sourceLang}","target_lang":"${params.targetLang}"}"""

        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiToken")
            setBody(body)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw RuntimeException("Translation failed: ${response.status}, body=$errorBody")
        }

        val responseBody = response.bodyAsText()
        return parseTranslatedText(responseBody)
    }

    override fun release() {
        client.close()
    }

    companion object {
        const val MODEL_M2M100 = "@cf/meta/m2m100-1.2b"

        val SUPPORTED_LANGUAGES = listOf(
            "english", "chinese", "french", "spanish", "arabic", "russian",
            "german", "japanese", "portuguese", "hindi", "italian", "dutch",
            "korean", "turkish", "polish", "swedish", "danish", "finnish",
            "greek", "czech", "romanian", "hungarian", "indonesian", "thai",
            "vietnamese", "hebrew", "ukrainian", "persian",
        )
    }
}

// Minimal JSON helpers (no dependency on serialization library)
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

private fun parseTranslatedText(json: String): String {
    // Parse {"result":{"translated_text":"..."}}
    val key = "\"translated_text\":"
    val idx = json.indexOf(key)
    if (idx == -1) throw RuntimeException("Unexpected response format: $json")
    val start = json.indexOf('"', idx + key.length) + 1
    val end = json.indexOf('"', start)
    return json.substring(start, end)
        .replace("\\n", "\n")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
}
