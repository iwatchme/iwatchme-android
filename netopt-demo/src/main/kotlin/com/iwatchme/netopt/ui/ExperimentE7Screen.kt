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
import com.iwatchme.netopt.net.monitor.TimingRecord
import com.iwatchme.netopt.ui.component.WaterfallChart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

enum class SyncMode(val label: String, val color: Color) {
    FULL("Full sync (since=0)", Color(0xFFD32F2F)),
    INCREMENTAL("Incremental (since=98)", Color(0xFF388E3C)),
}

data class SyncRun(
    val mode: SyncMode,
    val sinceVersion: Long,
    val timing: TimingRecord?,
    val returnedItems: Int,
)

@Composable
fun ExperimentE7Screen(onBack: () -> Unit) {
    val runs = remember { mutableStateListOf<SyncRun>() }
    var running by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val pendingTiming = remember { mutableStateOf<TimingRecord?>(null) }

    suspend fun runOne(mode: SyncMode, since: Long) {
        pendingTiming.value = null
        val client = ClientFactory.baseline { rec -> pendingTiming.value = rec }
        val items = withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("${ApiHost.baseUrl}/api/opt/feed/incremental?since=$since")
                .build()
            client.newCall(req).execute().use { resp ->
                // Drain body so EventListener fires responseBodyEnd with wire byte count.
                resp.body?.bytes()
                resp.header("X-Items-Returned")?.toIntOrNull() ?: 0
            }
        }
        runs.add(0, SyncRun(mode, since, pendingTiming.value, items))
    }

    fun runScenario() {
        if (running) return
        scope.launch {
            running = true; err = null
            try {
                runOne(SyncMode.FULL, 0L)
                runOne(SyncMode.INCREMENTAL, 98L)
            } catch (e: Exception) {
                err = e.message ?: e.javaClass.simpleName
            }
            running = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("E7 · 增量同步") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            }
        )
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Target: ${ApiHost.baseUrl}/api/opt/feed/incremental?since=…",
                style = MaterialTheme.typography.caption,
            )
            Text(
                "服务端共 100 条 (version 1..100)。模拟「已同步到 v=98 后再拉取」场景。",
                style = MaterialTheme.typography.caption,
                modifier = Modifier.padding(top = 4.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(
                    enabled = !running,
                    onClick = {
                        scope.launch {
                            running = true; err = null
                            try { runOne(SyncMode.FULL, 0L) } catch (e: Exception) { err = e.message }
                            running = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Full") }
                Button(
                    enabled = !running,
                    onClick = {
                        scope.launch {
                            running = true; err = null
                            try { runOne(SyncMode.INCREMENTAL, 98L) } catch (e: Exception) { err = e.message }
                            running = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Incremental") }
            }
            Button(
                enabled = !running,
                onClick = { runScenario() },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) { Text(if (running) "Running..." else "Run Full + Incremental in sequence") }

            err?.let {
                Text("Error: $it", color = MaterialTheme.colors.error, modifier = Modifier.padding(top = 6.dp))
            }

            ComparisonSummary(runs)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(runs) { r ->
                Card(elevation = 2.dp) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            "${r.mode.label} · returned ${r.returnedItems} items",
                            style = MaterialTheme.typography.subtitle2,
                            color = r.mode.color,
                        )
                        r.timing?.let { WaterfallChart(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonSummary(runs: List<SyncRun>) {
    val latestFull = runs.firstOrNull { it.mode == SyncMode.FULL }
    val latestInc = runs.firstOrNull { it.mode == SyncMode.INCREMENTAL }
    if (latestFull == null && latestInc == null) return

    Column(modifier = Modifier.padding(top = 8.dp)) {
        latestFull?.let {
            Text(
                "Full: ${it.timing?.respBytes ?: 0}B · ${it.timing?.totalMs ?: 0}ms",
                fontSize = 12.sp,
                color = SyncMode.FULL.color,
            )
        }
        latestInc?.let {
            Text(
                "Incremental: ${it.timing?.respBytes ?: 0}B · ${it.timing?.totalMs ?: 0}ms",
                fontSize = 12.sp,
                color = SyncMode.INCREMENTAL.color,
            )
        }
        if (latestFull != null && latestInc != null) {
            val full = latestFull.timing?.respBytes ?: 0L
            val inc = latestInc.timing?.respBytes ?: 0L
            if (full > 0 && inc > 0) {
                val pct = (inc.toFloat() / full.toFloat()) * 100f
                val savings = 100f - pct
                Text(
                    "Incremental = ${"%.1f".format(pct)}% of full · saved ${"%.1f".format(savings)}%",
                    fontSize = 13.sp,
                    style = MaterialTheme.typography.subtitle2,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
