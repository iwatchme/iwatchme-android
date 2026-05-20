package com.iwatchme.netopt.net.dns

import java.net.InetAddress
import okhttp3.Dns

/**
 * Maps a fixed set of hostnames to one IP. Lets the demo run a connection
 * coalescing experiment without touching the emulator's /etc/hosts — every
 * "api.demo.local" / "cdn.demo.local" / etc. request lands on 10.0.2.2.
 *
 * Combined with mkcert's wildcard SAN cert, OkHttp will detect that the
 * three SAN matches all live behind the same socket and merge subsequent
 * H2 calls onto a single connection (Connection Coalescing).
 */
class MapHostDns(
    private val mappings: Map<String, String>,
    private val fallback: Dns = Dns.SYSTEM,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        mappings[hostname]?.let { ip ->
            return listOf(InetAddress.getByName(ip))
        }
        return fallback.lookup(hostname)
    }
}
