package com.iwatchme.netopt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.iwatchme.netopt.net.ApiHost
import com.iwatchme.netopt.net.ClientFactory
import com.iwatchme.netopt.net.monitor.TimingRecord
import com.iwatchme.netopt.ui.component.EncodingBarChart
import com.iwatchme.netopt.net.EncodingType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

private enum class ImgVariant(val label: String, val query: String, val color: Color) {
    HI("JPEG q=92", "jpeg-hi", Color(0xFF7B1FA2)),
    MD("JPEG q=60", "jpeg-md", Color(0xFF1976D2)),
    LO("JPEG q=30", "jpeg-lo", Color(0xFFD32F2F)),
    BLUR("Blur 16×11", "blur",    Color(0xFFFF8F00)),
}

@Composable
fun ExperimentE8Screen(onBack: () -> Unit) {
    val context = LocalContext.current
    val timings = remember { mutableStateMapOf<ImgVariant, TimingRecord>() }
    var selected by remember { mutableStateOf<ImgVariant?>(null) }
    var running by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadOne(v: ImgVariant) {
        if (running) return
        scope.launch {
            running = true; err = null
            try {
                val client = ClientFactory.baseline { rec -> timings[v] = rec }
                withContext(Dispatchers.IO) {
                    val req = Request.Builder()
                        .url("${ApiHost.baseUrl}/api/opt/img?format=${v.query}")
                        .build()
                    client.newCall(req).execute().use { it.body?.bytes() }
                }
                selected = v
            } catch (e: Exception) {
                err = e.message ?: e.javaClass.simpleName
            }
            running = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("E8 · 图片优化") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            }
        )
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Target: ${ApiHost.baseUrl}/api/opt/img?format=…\n" +
                        "同一张 1080×720 图，对比 4 种 wire 大小。Blur 模拟 BlurHash 占位。",
                style = MaterialTheme.typography.caption,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ImgVariant.values().forEach { v ->
                    Button(
                        enabled = !running,
                        onClick = { loadOne(v) },
                        modifier = Modifier.weight(1f),
                    ) { Text(v.label, fontSize = 10.sp) }
                }
            }
            Button(
                enabled = !running,
                onClick = {
                    scope.launch {
                        running = true; err = null
                        try {
                            ImgVariant.values().forEach { v ->
                                val client = ClientFactory.baseline { rec -> timings[v] = rec }
                                withContext(Dispatchers.IO) {
                                    val req = Request.Builder()
                                        .url("${ApiHost.baseUrl}/api/opt/img?format=${v.query}")
                                        .build()
                                    client.newCall(req).execute().use { it.body?.bytes() }
                                }
                            }
                        } catch (e: Exception) { err = e.message ?: e.javaClass.simpleName }
                        running = false
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) { Text("Run all variants") }

            err?.let {
                Text("Error: $it", color = MaterialTheme.colors.error, modifier = Modifier.padding(top = 6.dp))
            }

            // Adapt EncodingBarChart by mapping into EncodingType-keyed map (re-use the widget).
            val mapForChart = mapOf(
                EncodingType.JSON     to (timings[ImgVariant.HI]?.respBytes ?: 0L),
                EncodingType.GZIP     to (timings[ImgVariant.MD]?.respBytes ?: 0L),
                EncodingType.BROTLI   to (timings[ImgVariant.LO]?.respBytes ?: 0L),
                EncodingType.PROTOBUF to (timings[ImgVariant.BLUR]?.respBytes ?: 0L),
            )
            EncodingBarChart(bytesByType = mapForChart)
            Text(
                "← labels (HI / MD / LO / BLUR) — reusing the E5 bar widget",
                fontSize = 9.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
            )

            ImgVariant.values().forEach { v ->
                timings[v]?.let { rec ->
                    val hi = timings[ImgVariant.HI]?.respBytes ?: 0L
                    val pct = if (hi > 0) " · ${"%.1f".format(rec.respBytes.toFloat() / hi * 100f)}% of HI" else ""
                    Text(
                        "${v.label}: ${rec.respBytes}B$pct · ${rec.totalMs}ms",
                        fontSize = 12.sp,
                        color = v.color,
                    )
                }
            }
        }

        // Preview area — show the selected variant rendered by Coil.
        selected?.let { v ->
            Card(
                elevation = 2.dp,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Render: ${v.label}", fontSize = 12.sp, color = v.color)
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("${ApiHost.baseUrl}/api/opt/img?format=${v.query}&t=${System.currentTimeMillis()}")
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1080f / 720f),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}
