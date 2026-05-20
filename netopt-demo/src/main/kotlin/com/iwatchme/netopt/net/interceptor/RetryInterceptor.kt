package com.iwatchme.netopt.net.interceptor

import java.io.IOException
import kotlin.random.Random
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Exponential backoff with jitter — see Article #3 L547-579.
 *
 *  - Retries on IOException and the 5xx codes listed in [retryableCodes].
 *  - Backoff = baseMs · 2^attempt, capped at maxMs.
 *  - 0..50% jitter added so a fleet of clients does not stampede on recovery.
 */
class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val baseMs: Long = 200,
    private val maxMs: Long = 4_000,
    private val retryableCodes: Set<Int> = setOf(502, 503, 504),
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var lastErr: IOException? = null

        for (attempt in 0..maxRetries) {
            try {
                val resp = chain.proceed(request)
                if (resp.code in retryableCodes && attempt < maxRetries) {
                    resp.close()
                    Thread.sleep(delay(attempt))
                    continue
                }
                if (resp.code in retryableCodes) {
                    // Last attempt is still 5xx — drain so upstream interceptors
                    // (e.g. EndpointFailover) can safely proceed on the chain.
                    val drained = (resp.body?.bytes() ?: ByteArray(0))
                        .toResponseBody(resp.body?.contentType())
                    resp.close()
                    return resp.newBuilder().body(drained).build()
                }
                return resp
            } catch (e: IOException) {
                lastErr = e
                if (attempt < maxRetries) Thread.sleep(delay(attempt))
            }
        }
        throw lastErr ?: IOException("Retry budget exhausted")
    }

    private fun delay(attempt: Int): Long {
        val exp = minOf(baseMs * (1L shl attempt), maxMs)
        return exp + (exp * Random.nextFloat() * 0.5f).toLong()
    }
}
