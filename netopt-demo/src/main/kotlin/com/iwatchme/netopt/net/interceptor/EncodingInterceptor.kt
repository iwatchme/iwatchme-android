package com.iwatchme.netopt.net.interceptor

import com.iwatchme.netopt.net.EncodingType
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Forces the request to use a specific Accept / Accept-Encoding combination
 * for the E5 experiment.
 *
 * Setting Accept-Encoding explicitly also disables OkHttp's transparent gzip,
 * so the bytes reported by NetMonitorListener.responseBodyEnd reflect the
 * actual wire payload — exactly what we want to compare.
 */
class EncodingInterceptor(private val type: EncodingType) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request().newBuilder()
            .header("Accept", type.accept)
            .header("Accept-Encoding", type.acceptEncoding)
            .build()
        return chain.proceed(req)
    }
}
