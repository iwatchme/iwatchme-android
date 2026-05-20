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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iwatchme.netopt.net.monitor.TimingRecord

private val WaitColor = Color(0xFFCFD8DC) // grey — queued in dispatcher
private val ExecColor = Color(0xFF4CAF50) // green — actually executing
private val ExecH1Color = Color(0xFFE57373) // red-ish — execution under H1 lane

/**
 * Renders one row per request, horizontally aligned to a shared batch start.
 *
 *  - The grey segment is `wallStart - batchStart` — time spent waiting in
 *    OkHttp's dispatcher queue (only > 0 when maxRequestsPerHost throttles).
 *  - The colored segment is the actual execute time (totalMs).
 *
 * Lets you see HTTP/1.1 "queue then run" patterns vs HTTP/2 "all at once".
 */
@Composable
fun BatchTimelineChart(
    batchStartMs: Long,
    batchTotalMs: Long,
    records: List<TimingRecord>,
    laneColor: Color = ExecColor,
    modifier: Modifier = Modifier,
) {
    val total = batchTotalMs.coerceAtLeast(1L).toFloat()
    val sorted = records.sortedBy { it.wallStartMs }

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            "Batch timeline (gray=queued · color=executing)",
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(4.dp))
        sorted.forEachIndexed { idx, rec ->
            val waitMs = (rec.wallStartMs - batchStartMs).coerceAtLeast(0L).toFloat()
            // execMs = callEnd - requestHeadersStart (the actual writing+reading slice).
            val execMs = (rec.totalMs - rec.requestStartOffset).coerceAtLeast(1L).toFloat()
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                Text("#${idx + 1}", fontSize = 9.sp, modifier = Modifier.width(20.dp))
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                ) {
                    val w = size.width
                    val h = size.height
                    val waitW = (waitMs / total) * w
                    val execW = (execMs / total) * w
                    // Wait segment (queued in dispatcher)
                    if (waitW > 0.5f) {
                        drawRect(
                            color = WaitColor,
                            topLeft = Offset(0f, 0f),
                            size = Size(waitW, h),
                        )
                    }
                    // Execute segment
                    drawRect(
                        color = laneColor,
                        topLeft = Offset(waitW, 0f),
                        size = Size(execW.coerceAtLeast(2f), h),
                    )
                }
            }
        }
    }
}

@Suppress("unused")
val TimelineExecColor = ExecColor
@Suppress("unused")
val TimelineExecH1Color = ExecH1Color
