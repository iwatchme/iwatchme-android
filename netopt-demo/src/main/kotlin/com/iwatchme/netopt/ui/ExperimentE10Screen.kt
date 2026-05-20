package com.iwatchme.netopt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iwatchme.netopt.net.ApiHost
import com.iwatchme.netopt.net.ClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private enum class Lane(val label: String, val color: Color) {
    NAIVE("Naive (no retry)", Color(0xFFD32F2F)),
    RESILIENT("Retry + Failover", Color(0xFF388E3C)),
}

private data class FlakyRun(
    val lane: Lane,
    val seq: Int,
    val httpCode: Int,
    val finalPath: String,
    val tookMs: Long,
    val errMsg: String? = null,
)

private const val PRIMARY = "/api/opt/flaky/primary"
private const val BACKUP = "/api/opt/flaky/backup"
private const val TOTAL_RUNS = 20

@Composable
fun ExperimentE10Screen(onBack: () -> Unit) {
    val runs = remember { mutableStateListOf<FlakyRun>() }
    var running by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val naiveClient: OkHttpClient = remember { ClientFactory.naive { } }
    val resilientClient: OkHttpClient = remember {
        ClientFactory.resilient(candidates = listOf(PRIMARY, BACKUP)) { }
    }

    suspend fun runBatch(lane: Lane, times: Int) {
        val client = if (lane == Lane.NAIVE) naiveClient else resilientClient
        for (i in 1..times) {
            val t0 = System.currentTimeMillis()
            val result: Triple<Int, String, String?> = withContext(Dispatchers.IO) {
                try {
                    val req = Request.Builder()
                        .url("${ApiHost.baseUrl}$PRIMARY")
                        .build()
                    client.newCall(req).execute().use { resp ->
                        resp.body?.bytes()
                        Triple(resp.code, resp.request.url.encodedPath, null as String?)
                    }
                } catch (e: Exception) {
                    Triple(-1, PRIMARY, "${e.javaClass.simpleName}: ${e.message}")
                }
            }
            val dt = System.currentTimeMillis() - t0
            runs.add(0, FlakyRun(lane, i, result.first, result.second, dt, result.third))
        }
    }

    fun trigger(lane: Lane) {
        if (running) return
        scope.launch {
            running = true; err = null
            try { runBatch(lane, TOTAL_RUNS) }
            catch (e: Exception) { err = e.message ?: e.javaClass.simpleName }
            running = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("E10 · 弱网 / 重试 / Failover") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            }
        )
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Primary endpoint = 50% 503\nBackup endpoint = 100% OK\n每个 lane 跑 $TOTAL_RUNS 次，看 final 成功率",
                style = MaterialTheme.typography.caption,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(
                    enabled = !running,
                    onClick = { trigger(Lane.NAIVE) },
                    modifier = Modifier.weight(1f),
                ) { Text("Naive ×$TOTAL_RUNS", fontSize = 12.sp) }
                Button(
                    enabled = !running,
                    onClick = { trigger(Lane.RESILIENT) },
                    modifier = Modifier.weight(1f),
                ) { Text("Resilient ×$TOTAL_RUNS", fontSize = 12.sp) }
            }
            Button(
                enabled = !running && runs.isNotEmpty(),
                onClick = { runs.clear() },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) { Text("Clear") }

            err?.let {
                Text(
                    "Error: $it",
                    color = MaterialTheme.colors.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            LaneSummary(runs)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(runs) { r ->
                Card(elevation = 1.dp) {
                    Column(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            val ok = r.httpCode in 200..299
                            Text(
                                "${r.lane.label.first()} #${r.seq} · ${if (ok) "OK" else "FAIL"} ${r.httpCode}",
                                color = if (ok) Color(0xFF388E3C) else Color(0xFFD32F2F),
                                fontSize = 12.sp,
                            )
                            Text(
                                "${r.finalPath.removePrefix("/api/opt/flaky/")} · ${r.tookMs}ms",
                                fontSize = 11.sp,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                            )
                        }
                        r.errMsg?.let {
                            Text(
                                it,
                                fontSize = 10.sp,
                                color = Color(0xFFD32F2F).copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LaneSummary(runs: List<FlakyRun>) {
    if (runs.isEmpty()) return
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Lane.values().forEach { lane ->
            val laneRuns = runs.filter { it.lane == lane }
            if (laneRuns.isEmpty()) return@forEach
            val ok = laneRuns.count { it.httpCode in 200..299 }
            val total = laneRuns.size
            val pct = (ok.toFloat() / total * 100f)
            val avgMs = laneRuns.map { it.tookMs }.average().toLong()
            Text(
                "${lane.label}: $ok/$total = ${"%.1f".format(pct)}% · avg ${avgMs}ms",
                fontSize = 13.sp,
                color = lane.color,
            )
        }
    }
}
