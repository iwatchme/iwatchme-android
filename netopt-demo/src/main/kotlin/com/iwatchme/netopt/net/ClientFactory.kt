package com.iwatchme.netopt.net

import com.iwatchme.netopt.net.dns.HttpDnsResolver
import com.iwatchme.netopt.net.dns.SlowSystemDns
import com.iwatchme.netopt.net.interceptor.EncodingInterceptor
import com.iwatchme.netopt.net.interceptor.EndpointFailover
import com.iwatchme.netopt.net.interceptor.RetryInterceptor
import com.iwatchme.netopt.net.monitor.NetMonitorListener
import com.iwatchme.netopt.net.monitor.TimingRecord
import java.io.File
import okhttp3.Cache
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.OkHttpClient

object ClientFactory {

    /**
     * Baseline client: out-of-the-box OkHttp config, only attaches a per-call
     * NetMonitorListener so every request emits a TimingRecord.
     */
    fun baseline(onTiming: (TimingRecord) -> Unit): OkHttpClient =
        OkHttpClient.Builder()
            .eventListenerFactory(EventListener.Factory { NetMonitorListener(onTiming) })
            .build()

    /**
     * E5: client wired to negotiate a specific encoding (JSON / Gzip / Brotli /
     * Protobuf). The EncodingInterceptor sets Accept + Accept-Encoding, which
     * also disables OkHttp's transparent gzip so reported byte counts match
     * the wire payload.
     */
    fun forEncoding(type: EncodingType, onTiming: (TimingRecord) -> Unit): OkHttpClient =
        OkHttpClient.Builder()
            .eventListenerFactory(EventListener.Factory { NetMonitorListener(onTiming) })
            .addInterceptor(EncodingInterceptor(type))
            .build()

    /**
     * E6: client with an on-disk HTTP cache. OkHttp honors Cache-Control / ETag
     * automatically; the experiment exposes whether each request was satisfied
     * fresh, via 304 revalidation, or fully from local cache.
     */
    fun cached(cacheDir: File, sizeBytes: Long = 10L * 1024 * 1024, onTiming: (TimingRecord) -> Unit): OkHttpClient =
        OkHttpClient.Builder()
            .cache(Cache(File(cacheDir, "http_cache_e6"), sizeBytes))
            .eventListenerFactory(EventListener.Factory { NetMonitorListener(onTiming) })
            .build()

    /**
     * E2: client with a swappable Dns implementation. Used to compare the slow
     * "ISP DNS" baseline against an HttpDNS + SWR cache stack.
     */
    fun withDns(dns: Dns, onTiming: (TimingRecord) -> Unit): OkHttpClient =
        OkHttpClient.Builder()
            .dns(dns)
            .eventListenerFactory(EventListener.Factory { NetMonitorListener(onTiming) })
            .build()

    /**
     * E10: naive client — no retry, no failover. Will inherit the chaos
     * endpoint's 50% failure rate one-to-one.
     */
    fun naive(onTiming: (TimingRecord) -> Unit): OkHttpClient =
        OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .eventListenerFactory(EventListener.Factory { NetMonitorListener(onTiming) })
            .build()

    /**
     * E10: resilient client — exponential-backoff retry + path-level failover.
     * Order matters: failover is the outer interceptor so retries on the
     * primary path exhaust before moving to backup.
     */
    /**
     * E3: client with an explicit Dispatcher cap. enqueue() respects
     * maxRequestsPerHost — when the cap is hit, additional calls are held in
     * the dispatcher's readyAsyncCalls queue, which is exactly the "browser
     * H1.1 6-per-host" queueing behaviour we want to visualise.
     */
    fun withDispatcherCap(maxPerHost: Int, onTiming: (TimingRecord) -> Unit): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequests = 256
            maxRequestsPerHost = maxPerHost
        }
        return OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .eventListenerFactory(EventListener.Factory { NetMonitorListener(onTiming) })
            .build()
    }

    fun resilient(
        candidates: List<String>,
        onTiming: (TimingRecord) -> Unit,
    ): OkHttpClient =
        OkHttpClient.Builder()
            // Let OkHttp transparently re-establish the TCP connection that
            // primary's "Connection: close" forces us to drop — our
            // RetryInterceptor handles the application-level 5xx retries.
            .retryOnConnectionFailure(true)
            .addInterceptor(EndpointFailover(candidates))
            .addInterceptor(RetryInterceptor(maxRetries = 3, baseMs = 150))
            .eventListenerFactory(EventListener.Factory { NetMonitorListener(onTiming) })
            .build()
}
