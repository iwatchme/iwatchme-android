package com.iwatchme.netopt.net.dns

import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * E2 — three-layer DNS resolver: SWR cache → HttpDNS → SlowSystemDns fallback.
 *
 * Caching follows the stale-while-revalidate idiom from the article:
 *  - {@code now < expireAt}: hit, ~0ms.
 *  - {@code expireAt < now < staleAt}: hit with the stale value, kick off
 *    background refresh.
 *  - {@code now > staleAt}: synchronous refresh via HttpDNS, fall back to
 *    SlowSystemDns if that fails.
 *
 * The internal HTTP client used to talk to the HttpDNS endpoint connects to
 * the host loopback alias by IP (no recursive DNS needed).
 */
class HttpDnsResolver(
    private val httpDnsBaseUrl: String,
    private val fallback: Dns = SlowSystemDns(),
    private val freshMs: Long = 60_000L,
    private val staleMs: Long = 5 * 60_000L,
) : Dns {

    data class Entry(
        val addresses: List<InetAddress>,
        val expireAt: Long,
        val staleAt: Long,
    )

    private val cache = ConcurrentHashMap<String, Entry>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val internal: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(2, TimeUnit.SECONDS)
        .build()

    override fun lookup(hostname: String): List<InetAddress> {
        val now = System.currentTimeMillis()
        cache[hostname]?.let { e ->
            return when {
                now < e.expireAt -> e.addresses
                now < e.staleAt -> {
                    scope.launch { resolveAndCache(hostname) }
                    e.addresses
                }
                else -> {
                    resolveAndCache(hostname)?.let { return it }
                    fallback.lookup(hostname)
                }
            }
        }
        return resolveAndCache(hostname) ?: fallback.lookup(hostname)
    }

    /** Pre-resolve before the first business request — kick off from UI. */
    fun prefetch(hostname: String) {
        scope.launch { resolveAndCache(hostname) }
    }

    /** Drop everything so subsequent requests pay the full HttpDNS RTT again. */
    fun clear() {
        cache.clear()
    }

    private fun resolveAndCache(hostname: String): List<InetAddress>? {
        val ips = fetchOnce(hostname) ?: return null
        val now = System.currentTimeMillis()
        cache[hostname] = Entry(ips, now + freshMs, now + staleMs)
        return ips
    }

    private fun fetchOnce(hostname: String): List<InetAddress>? = runCatching {
        val req = Request.Builder()
            .url("$httpDnsBaseUrl/api/opt/d?host=$hostname")
            .build()
        internal.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching null
            val body = resp.body?.string() ?: return@runCatching null
            val json = JSONObject(body)
            val arr = json.getJSONArray("ips")
            (0 until arr.length()).mapNotNull { idx ->
                runCatching { InetAddress.getByName(arr.getString(idx)) }.getOrNull()
            }.takeIf { it.isNotEmpty() }
        }
    }.getOrNull()
}
