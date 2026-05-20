package com.iwatchme.netopt.net.interceptor

import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Walks through a list of candidate endpoints (path-segments) on every
 * request. Returns the first non-5xx response. Adapted from Article #5
 * L397-447, but switches paths instead of hosts so a single-Caddy demo can
 * still demonstrate the pattern.
 */
class EndpointFailover(
    private val candidates: List<String>,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        var lastErr: IOException? = null
        var lastResp: Response? = null

        for (path in candidates) {
            val newUrl: HttpUrl = original.url.newBuilder()
                .encodedPath(path)
                .build()
            val attempt = original.newBuilder().url(newUrl).build()
            try {
                val resp = chain.proceed(attempt)
                if (resp.code < 500) {
                    lastResp?.close()
                    return resp
                }
                // Drain + rebuild so the underlying connection is released
                // before the next chain.proceed() call — otherwise OkHttp
                // throws "previous response is still open".
                val drained = (resp.body?.bytes() ?: ByteArray(0))
                    .toResponseBody(resp.body?.contentType())
                resp.close()
                lastResp?.close()
                lastResp = resp.newBuilder().body(drained).build()
            } catch (e: IOException) {
                lastErr = e
                // Try the next candidate.
            }
        }
        // Return any cached 5xx response if we have one, otherwise rethrow.
        lastResp?.let { return it }
        throw (lastErr ?: IOException("All endpoints failed"))
    }
}
