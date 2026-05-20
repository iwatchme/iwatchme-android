package com.iwatchme.netopt.net.monitor

import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol

class NetMonitorListener(
    private val onComplete: (TimingRecord) -> Unit,
) : EventListener() {

    private var callStartMs = 0L
    private var dnsStartMs = 0L
    private var dnsEndMs = 0L
    private var connectStartMs = 0L
    private var connectEndMs = 0L
    private var tlsStartMs = 0L
    private var tlsEndMs = 0L
    private var requestStartMs = 0L
    private var responseStartMs = 0L
    private var responseEndMs = 0L

    private var url: String = ""
    private var host: String = ""
    private var protocol: String? = null
    private var hadConnectStart = false
    private var reused = false
    private var respBytes: Long = 0L
    private var success = false
    private var errorType: String? = null

    override fun callStart(call: Call) {
        callStartMs = System.currentTimeMillis()
        url = call.request().url.toString()
        host = call.request().url.host
    }

    override fun dnsStart(call: Call, domainName: String) {
        dnsStartMs = System.currentTimeMillis()
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        dnsEndMs = System.currentTimeMillis()
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        hadConnectStart = true
        connectStartMs = System.currentTimeMillis()
    }

    override fun secureConnectStart(call: Call) {
        tlsStartMs = System.currentTimeMillis()
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        tlsEndMs = System.currentTimeMillis()
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
    ) {
        connectEndMs = System.currentTimeMillis()
        this.protocol = protocol?.toString()
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        reused = !hadConnectStart
        if (protocol == null) protocol = connection.protocol().toString()
    }

    override fun requestHeadersStart(call: Call) {
        requestStartMs = System.currentTimeMillis()
    }

    override fun responseHeadersStart(call: Call) {
        responseStartMs = System.currentTimeMillis()
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        responseEndMs = System.currentTimeMillis()
        respBytes = byteCount
    }

    override fun callEnd(call: Call) {
        success = true
        finish()
    }

    override fun callFailed(call: Call, ioe: IOException) {
        success = false
        errorType = classifyError(ioe)
        finish()
    }

    private fun finish() {
        val end = if (responseEndMs > 0) responseEndMs else System.currentTimeMillis()
        val tls = nonNegative(tlsEndMs - tlsStartMs, tlsStartMs > 0)
        val rawConnect = nonNegative(connectEndMs - connectStartMs, connectStartMs > 0)
        val tcpOnly = (rawConnect - tls).coerceAtLeast(0)
        // For E3 we want "when did this call actually start executing", not
        // "when was it enqueued". requestHeadersStart fires only after the
        // dispatcher releases the call and a connection is ready, so it
        // reflects real dispatcher queueing.
        val executeStartMs = if (requestStartMs > 0) requestStartMs else callStartMs
        onComplete(
            TimingRecord(
                wallStartMs = executeStartMs,
                url = url,
                host = host,
                protocol = protocol,
                reused = reused,
                dnsMs = nonNegative(dnsEndMs - dnsStartMs, dnsStartMs > 0),
                connectMs = tcpOnly,
                tlsMs = tls,
                ttfbMs = nonNegative(responseStartMs - requestStartMs, requestStartMs > 0),
                recvMs = nonNegative(responseEndMs - responseStartMs, responseStartMs > 0),
                totalMs = end - callStartMs,
                respBytes = respBytes,
                success = success,
                errorType = errorType,
                dnsStartOffset = offset(dnsStartMs),
                connectStartOffset = offset(connectStartMs),
                tlsStartOffset = offset(tlsStartMs),
                requestStartOffset = offset(requestStartMs),
                responseStartOffset = offset(responseStartMs),
                responseEndOffset = offset(responseEndMs),
            )
        )
    }

    private fun offset(absMs: Long): Long =
        if (absMs > 0) (absMs - callStartMs).coerceAtLeast(0) else 0L

    private fun nonNegative(delta: Long, valid: Boolean): Long =
        if (valid) delta.coerceAtLeast(0) else 0L

    private fun classifyError(e: IOException): String = when (e) {
        is UnknownHostException -> "dns_fail"
        is ConnectException -> "connect_fail"
        is SSLException -> "tls_fail"
        is SocketTimeoutException -> "timeout"
        else -> "unknown_${e.javaClass.simpleName}"
    }
}
