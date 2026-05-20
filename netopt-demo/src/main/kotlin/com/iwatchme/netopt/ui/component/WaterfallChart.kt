package com.iwatchme.netopt.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iwatchme.netopt.net.monitor.TimingRecord

private val DnsColor = Color(0xFF42A5F5)
private val TcpColor = Color(0xFF66BB6A)
private val TlsColor = Color(0xFFFFB74D)
private val WaitColor = Color(0xFFAB47BC)
private val RecvColor = Color(0xFF26A69A)
private val GapColor = Color(0xFFE0E0E0)

private data class Segment(val label: String, val color: Color, val startMs: Long, val durationMs: Long)

@Composable
fun WaterfallChart(record: TimingRecord, modifier: Modifier = Modifier) {
    val total = record.totalMs.coerceAtLeast(1L)
    val segments = buildList {
        if (record.dnsMs > 0) add(Segment("DNS", DnsColor, record.dnsStartOffset, record.dnsMs))
        if (record.connectMs > 0) add(Segment("TCP", TcpColor, record.connectStartOffset, record.connectMs))
        if (record.tlsMs > 0) add(Segment("TLS", TlsColor, record.tlsStartOffset, record.tlsMs))
        val waitDur = record.ttfbMs
        if (waitDur > 0) add(Segment("Wait", WaitColor, record.requestStartOffset, waitDur))
        if (record.recvMs > 0) add(Segment("Recv", RecvColor, record.responseStartOffset, record.recvMs))
    }

    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            val w = size.width
            val h = size.height
            // Background gap track
            drawRect(GapColor, size = androidx.compose.ui.geometry.Size(w, h))
            segments.forEach { seg ->
                val x = (seg.startMs.toFloat() / total.toFloat()) * w
                val segW = (seg.durationMs.toFloat() / total.toFloat()) * w
                drawRect(
                    color = seg.color,
                    topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                    size = androidx.compose.ui.geometry.Size(segW.coerceAtLeast(1f), h)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            LegendItem("DNS ${record.dnsMs}ms", DnsColor)
            LegendItem("TCP ${record.connectMs}ms", TcpColor)
            LegendItem("TLS ${record.tlsMs}ms", TlsColor)
            LegendItem("Wait(TTFB) ${record.ttfbMs}ms", WaitColor)
            LegendItem("Recv ${record.recvMs}ms", RecvColor)
        }
        Text(
            text = "total ${record.totalMs}ms · ${record.respBytes}B · ${record.protocol ?: "?"}${if (record.reused) " · REUSED" else ""}",
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(modifier = Modifier.padding(end = 8.dp)) {
        Spacer(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
        )
        Canvas(modifier = Modifier.size(10.dp)) { drawRect(color) }
        Spacer(Modifier.width(3.dp))
        Text(label, fontSize = 10.sp)
    }
}
