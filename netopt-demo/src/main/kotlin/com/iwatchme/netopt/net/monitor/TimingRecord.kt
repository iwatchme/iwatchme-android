package com.iwatchme.netopt.net.monitor

data class TimingRecord(
    val url: String,
    val host: String,
    val protocol: String?,
    val reused: Boolean,
    val dnsMs: Long,
    val connectMs: Long,
    val tlsMs: Long,
    val ttfbMs: Long,
    val recvMs: Long,
    val totalMs: Long,
    val respBytes: Long,
    val success: Boolean,
    val errorType: String?,
    val dnsStartOffset: Long,
    val connectStartOffset: Long,
    val tlsStartOffset: Long,
    val requestStartOffset: Long,
    val responseStartOffset: Long,
    val responseEndOffset: Long,
    /** Absolute wall-clock timestamp at callStart — lets E3 visualise dispatcher queueing. */
    val wallStartMs: Long = 0L,
)
