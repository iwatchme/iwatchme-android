package com.iwatchme.netopt.net.dns

import java.net.InetAddress
import kotlin.random.Random
import okhttp3.Dns

/**
 * Simulates the "ISP DNS / system DNS" experience for the baseline lane:
 *  - average resolution adds [avgMs] ± jitter
 *  - [tailRate] of resolutions hit a long tail of [tailMs]
 *  - the actual mapping result is hard-coded so the call still reaches the
 *    host loopback alias (10.0.2.2), letting us run the demo without real DNS.
 *
 * Reproduces what the article calls "运营商 LocalDNS 慢且不稳定".
 */
class SlowSystemDns(
    private val avgMs: Long = 500,
    private val tailMs: Long = 1500,
    private val tailRate: Float = 0.25f,
) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        val delay = if (Random.nextFloat() < tailRate) {
            tailMs + Random.nextLong(-200, 200)
        } else {
            avgMs + Random.nextLong(-100, 100)
        }.coerceAtLeast(0)
        try {
            Thread.sleep(delay)
        } catch (_: InterruptedException) {
        }
        // Pretend every hostname resolves to the emulator-to-host alias.
        // In real life this would be Dns.SYSTEM.lookup(hostname).
        return listOf(InetAddress.getByName("10.0.2.2"))
    }
}
