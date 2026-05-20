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
import com.iwatchme.netopt.net.ClientFactory
import com.iwatchme.netopt.net.monitor.TimingRecord
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.iwatchme.netopt.ui.component.BatchTimelineChart
import com.iwatchme.netopt.ui.component.WaterfallChart
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private enum class ProtoLane(
    val label: String,
    val baseUrl: String,
    val color: Color,
    val dispatcherCap: Int,
) {
    H1_REAL("H1 cap=6 (real)",  "https://10.0.2.2:4444", Color(0xFFD32F2F), 6),
    H1_FAIR("H1 cap=12 (fair)", "https://10.0.2.2:4444", Color(0xFFEF6C00), 12),
    H2_FAIR("H2 cap=12 (fair)", "https://10.0.2.2:4443", Color(0xFF1976D2), 12),
    H2_REAL("H2 cap=64 (real)", "https://10.0.2.2:4443", Color(0xFF388E3C), 64),
}

private data class ParallelRun(
    val lane: ProtoLane,
    val totalMs: Long,
    val batchStartMs: Long,
    val perRequest: List<TimingRecord>,
)

private const val PARALLEL = 12
private const val RTT_MS = 300

@Composable
fun ExperimentE3Screen(onBack: () -> Unit) {
    val results = remember { mutableStateListOf<ParallelRun>() }
    var running by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val sinks: Map<ProtoLane, SnapshotStateList<TimingRecord>> = remember {
        ProtoLane.values().associateWith { mutableStateListOf<TimingRecord>() }
    }
    val clients: Map<ProtoLane, OkHttpClient> = remember {
        ProtoLane.values().associateWith { lane ->
            ClientFactory.withDispatcherCap(lane.dispatcherCap) { rec ->
                sinks[lane]!!.add(rec)
            }
        }
    }

    suspend fun runParallel(lane: ProtoLane) {
        val sink = sinks[lane]!!
        val client = clients[lane]!!

        // Warm up to ensure ALPN is negotiated and (for H2) a multiplexable
        // connection sits in the pool. Otherwise the burst would race to
        // open multiple conns and hide H2's stream-multiplexing advantage.
        sink.clear()
        try {
            withContext(Dispatchers.IO) {
                val warm = Request.Builder()
                    .url("${lane.baseUrl}/api/opt/ping")
                    .build()
                client.newCall(warm).execute().use { it.body?.bytes() }
            }
        } catch (_: Exception) { /* ignore warmup failures */ }
        sink.clear()

        val errors = mutableListOf<String>()
        val batchStart = System.currentTimeMillis()
        // enqueue() — not execute() — so the Dispatcher's per-host cap actually queues calls.
        val deferreds = (1..PARALLEL).map { idx ->
            val d = CompletableDeferred<Unit>()
            val req = Request.Builder()
                .url("${lane.baseUrl}/api/opt/feed?limit=10&rtt=$RTT_MS&i=$idx")
                .build()
            client.newCall(req).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    response.use { it.body?.bytes() }
                    d.complete(Unit)
                }
                override fun onFailure(call: Call, e: IOException) {
                    synchronized(errors) {
                        errors.add("#$idx ${e.javaClass.simpleName}: ${e.message}")
                    }
                    d.complete(Unit)
                }
            })
            d
        }
        deferreds.awaitAll()
        val dt = System.currentTimeMillis() - batchStart
        if (errors.isNotEmpty()) err = errors.joinToString("\n").take(300)
        results.add(0, ParallelRun(lane, dt, batchStart, sink.toList()))
    }

    fun trigger(lane: ProtoLane) {
        if (running) return
        scope.launch {
            running = true; err = null
            try { runParallel(lane) }
            catch (e: Exception) { err = e.message ?: e.javaClass.simpleName }
            running = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("E3 · H1.1 vs H2 多路复用") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            }
        )
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "并发 $PARALLEL 个请求，服务端注入 ${RTT_MS}ms RTT\n" +
                        "两两对比解开 cap 和 multiplexing 两个变量：\n" +
                        "  · H1(6)  vs H1(12): dispatcher cap 单变量\n" +
                        "  · H1(12) vs H2(12): 公平 cap 下纯协议差异\n" +
                        "  · H1(6)  vs H2(64): 浏览器现实总差异（cap+multiplex 叠加）",
                style = MaterialTheme.typography.caption,
            )

            // 两行 4 按钮
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(enabled = !running, onClick = { trigger(ProtoLane.H1_REAL) }, modifier = Modifier.weight(1f)) {
                    Text("H1(6)", fontSize = 11.sp)
                }
                Button(enabled = !running, onClick = { trigger(ProtoLane.H1_FAIR) }, modifier = Modifier.weight(1f)) {
                    Text("H1(12)", fontSize = 11.sp)
                }
                Button(enabled = !running, onClick = { trigger(ProtoLane.H2_FAIR) }, modifier = Modifier.weight(1f)) {
                    Text("H2(12)", fontSize = 11.sp)
                }
                Button(enabled = !running, onClick = { trigger(ProtoLane.H2_REAL) }, modifier = Modifier.weight(1f)) {
                    Text("H2(64)", fontSize = 11.sp)
                }
            }
            Button(
                enabled = !running,
                onClick = {
                    scope.launch {
                        running = true; err = null
                        try { ProtoLane.values().forEach { runParallel(it) } }
                        catch (e: Exception) { err = e.message ?: e.javaClass.simpleName }
                        running = false
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) { Text(if (running) "Running..." else "Run all 4 lanes") }
            Button(
                enabled = !running && results.isNotEmpty(),
                onClick = { results.clear() },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) { Text("Clear") }

            err?.let {
                Text("Error: $it", color = MaterialTheme.colors.error, modifier = Modifier.padding(top = 6.dp))
            }

            ComparisonSummary(results)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(results) { r ->
                Card(elevation = 2.dp) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            "${r.lane.label} · wall ${r.totalMs}ms",
                            style = MaterialTheme.typography.subtitle2,
                            color = r.lane.color,
                        )
                        val protocols = r.perRequest.groupingBy { it.protocol ?: "?" }.eachCount()
                        val newConn = r.perRequest.count { !it.reused }
                        val reuse = r.perRequest.count { it.reused }
                        val queued = r.perRequest.count { (it.wallStartMs - r.batchStartMs) > 100 }
                        val maxQueueMs = r.perRequest.maxOfOrNull { (it.wallStartMs - r.batchStartMs) } ?: 0L
                        Text(
                            "protocols=$protocols · new=$newConn · reused=$reuse · queued(>100ms)=$queued · maxWait=${maxQueueMs}ms",
                            fontSize = 11.sp,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                        )
                        BatchTimelineChart(
                            batchStartMs = r.batchStartMs,
                            batchTotalMs = r.totalMs,
                            records = r.perRequest,
                            laneColor = r.lane.color,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonSummary(results: List<ParallelRun>) {
    val latest = ProtoLane.values().associateWith { lane -> results.firstOrNull { it.lane == lane } }
    if (latest.values.all { it == null }) return
    Column(modifier = Modifier.padding(top = 8.dp)) {
        ProtoLane.values().forEach { lane ->
            latest[lane]?.let { r ->
                Text(
                    "${lane.label.padEnd(20)} → wall ${r.totalMs}ms",
                    fontSize = 12.sp,
                    color = lane.color,
                )
            }
        }
        val h1real = latest[ProtoLane.H1_REAL]
        val h1fair = latest[ProtoLane.H1_FAIR]
        val h2fair = latest[ProtoLane.H2_FAIR]
        if (h1real != null && h1fair != null) {
            val capDelta = 100f - h1fair.totalMs.toFloat() / h1real.totalMs.toFloat() * 100f
            Text(
                "Δ cap (H1 6→12) = -${"%.1f".format(capDelta)}%",
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (h1fair != null && h2fair != null) {
            val muxDelta = 100f - h2fair.totalMs.toFloat() / h1fair.totalMs.toFloat() * 100f
            Text(
                "Δ multiplex (H1→H2 @ cap=12) = -${"%.1f".format(muxDelta)}%",
                fontSize = 12.sp,
                style = MaterialTheme.typography.subtitle2,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
