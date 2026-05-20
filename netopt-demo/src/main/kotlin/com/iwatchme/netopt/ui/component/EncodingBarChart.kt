package com.iwatchme.netopt.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iwatchme.netopt.net.EncodingType

private val EncodingColors = mapOf(
    EncodingType.JSON to Color(0xFF7B1FA2),
    EncodingType.GZIP to Color(0xFF1976D2),
    EncodingType.BROTLI to Color(0xFFD32F2F),
    EncodingType.PROTOBUF to Color(0xFFFF8F00),
)

@Composable
fun EncodingBarChart(
    bytesByType: Map<EncodingType, Long>,
    modifier: Modifier = Modifier,
) {
    val maxBytes = (bytesByType.values.maxOrNull() ?: 1L).coerceAtLeast(1L)
    val ordered = EncodingType.values().toList()

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Wire bytes (smaller is better)", fontSize = 12.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            ordered.forEach { type ->
                val bytes = bytesByType[type] ?: 0L
                val ratio = if (bytes > 0) bytes.toFloat() / maxBytes.toFloat() else 0f
                Column(
                    modifier = Modifier.width(64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (bytes > 0) "${bytes}B" else "—",
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height((140.dp.value * ratio).coerceAtLeast(2f).dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    ) {
                        Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                            drawRect(EncodingColors[type] ?: Color.Gray)
                        }
                    }
                    Text(type.label, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
