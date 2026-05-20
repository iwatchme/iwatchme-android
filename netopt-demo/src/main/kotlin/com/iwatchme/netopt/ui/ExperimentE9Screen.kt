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
import com.iwatchme.netopt.net.dns.MapHostDns
import com.iwatchme.netopt.net.monitor.TimingRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private enum class CoalesceLane(val label: String, val color: Color) {
    DISJOINT("Disjoint (one client per host)", Color(0xFFD32F2F)),
    COALESCED("Coalesced (shared client + wildcard cert)", Color(0xFF388E3C)),
}

private data class HostRun(
    val lane: CoalesceLane,
    val host: String,
    val timing: TimingRecord?,
)

// All four resolve to 10.0.2.2 (host loopback). Caddy's :4443 site has a
// wildcard mkcert cert that already covers all four SANs — see tools/caddy/*.
private val DEMO_HOSTS = listOf(
    "api.demo.local",
    "cdn.demo.local",
    "img.demo.local",
    "h2.demo.local",
)

@Composable
fun ExperimentE9Screen(onBack: () -> Unit) {
    val runs = remember { mutableStateListOf<HostRun>() }
    var running by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val pendingTiming = remember { mutableStateOf<TimingRecord?>(null) }

    val hostMap = remember { DEMO_HOSTS.associateWith { "10.0.2.2" } }
    val sharedDns = remember { MapHostDns(hostMap) }

    // Coalesced lane: ONE client → connection pool can share across hosts.
    val coalescedClient: OkHttpClient = remember {
        ClientFactory.withDns(sharedDns) { rec -> pendingTiming.value = rec }
    }
    // Disjoint lane: a separate client per host = a separate connection pool,
    // so the wildcard cert + same IP don't help. Each call must hand-shake.
    val disjointClients: Map<String, OkHttpClient> = remember {
        DEMO_HOSTS.associateWith {
            ClientFactory.withDns(sharedDns) { rec -> pendingTiming.value = rec }
        }
    }

    suspend fun sendOnce(lane: CoalesceLane, host: String) {
        pendingTiming.value = null
        val client = when (lane) {
            CoalesceLane.COALESCED -> coalescedClient
            CoalesceLane.DISJOINT  -> disjointClients[host]!!
        }
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("https://$host:4443/api/opt/ping")
                .build()
            client.newCall(req).execute().use { it.body?.bytes() }
        }
        runs.add(0, HostRun(lane, host, pendingTiming.value))
    }

    fun trigger(lane: CoalesceLane) {
        if (running) return
        scope.launch {
            running = true; err = null
            try { DEMO_HOSTS.forEach { sendOnce(lane, it) } }
            catch (e: Exception) { err = e.message ?: e.javaClass.simpleName }
            running = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("E9 · 域名收敛 / 连接合并") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            }
        )
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "4 hosts: ${DEMO_HOSTS.joinToString()}\n" +
                        "全部 MapHostDns → 10.0.2.2，共享 mkcert 通配符证书\n" +
                        "Disjoint: 每个 host 独立 client = 独立 connection pool → 每次都新建\n" +
                        "Coalesced: 一个 client → OkHttp 检测 SAN+同 IP → H2 stream 复用",
                style = MaterialTheme.typography.caption,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(
                    enabled = !running,
                    onClick = { trigger(CoalesceLane.DISJOINT) },
                    modifier = Modifier.weight(1f),
                ) { Text("Disjoint", fontSize = 11.sp) }
                Button(
                    enabled = !running,
                    onClick = { trigger(CoalesceLane.COALESCED) },
                    modifier = Modifier.weight(1f),
                ) { Text("Coalesced", fontSize = 11.sp) }
            }
            Button(
                enabled = !running && runs.isNotEmpty(),
                onClick = { runs.clear() },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) { Text("Clear") }

            err?.let {
                Text("Error: $it", color = MaterialTheme.colors.error, modifier = Modifier.padding(top = 6.dp))
            }

            ReuseSummary(runs)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(runs) { r ->
                Card(elevation = 1.dp) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "${r.host}",
                                fontSize = 12.sp,
                                color = r.lane.color,
                            )
                            r.timing?.let { tr ->
                                val tag = if (tr.reused) "REUSED" else "NEW"
                                Text(
                                    "$tag · ${tr.protocol ?: "?"} · ${tr.totalMs}ms · TLS ${tr.tlsMs}ms",
                                    fontSize = 11.sp,
                                    color = if (tr.reused) Color(0xFF388E3C) else Color(0xFFE65100),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReuseSummary(runs: List<HostRun>) {
    if (runs.isEmpty()) return
    Column(modifier = Modifier.padding(top = 8.dp)) {
        CoalesceLane.values().forEach { lane ->
            val laneRuns = runs.filter { it.lane == lane }
            if (laneRuns.isEmpty()) return@forEach
            val total = laneRuns.size
            val reused = laneRuns.count { it.timing?.reused == true }
            val newConns = total - reused
            val totalTls = laneRuns.sumOf { it.timing?.tlsMs ?: 0 }
            Text(
                "${lane.label}: reused $reused/$total · new conns $newConns · 累计 TLS ${totalTls}ms",
                fontSize = 12.sp,
                color = lane.color,
            )
        }
    }
}
