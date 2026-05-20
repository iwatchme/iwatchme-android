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
import com.iwatchme.netopt.net.dns.HttpDnsResolver
import com.iwatchme.netopt.net.dns.SlowSystemDns
import com.iwatchme.netopt.net.monitor.TimingRecord
import com.iwatchme.netopt.ui.component.WaterfallChart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

private enum class DnsLane(val label: String, val color: Color) {
    SLOW("Slow System DNS", Color(0xFFD32F2F)),
    HTTPDNS("HttpDNS + SWR", Color(0xFF388E3C)),
}

private data class DnsRun(val lane: DnsLane, val attempt: Int, val timing: TimingRecord?)

private const val DEMO_HOST = "api.demo.local"
private const val DEMO_URL = "https://$DEMO_HOST:4443/api/opt/feed?limit=10"

@Composable
fun ExperimentE2Screen(onBack: () -> Unit) {
    val runs = remember { mutableStateListOf<DnsRun>() }
    var running by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val pendingTiming = remember { mutableStateOf<TimingRecord?>(null) }

    val httpDns = remember { HttpDnsResolver(ApiHost.baseUrl) }

    suspend fun runOne(lane: DnsLane, attempt: Int) {
        pendingTiming.value = null
        val dns = when (lane) {
            DnsLane.SLOW -> SlowSystemDns()
            DnsLane.HTTPDNS -> httpDns
        }
        val client = ClientFactory.withDns(dns) { rec -> pendingTiming.value = rec }
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(DEMO_URL).build()
            client.newCall(req).execute().use { it.body?.bytes() }
        }
        runs.add(0, DnsRun(lane, attempt, pendingTiming.value))
    }

    fun runLane(lane: DnsLane, times: Int) {
        if (running) return
        scope.launch {
            running = true; err = null
            try { repeat(times) { runOne(lane, it + 1) } }
            catch (e: Exception) { err = e.message ?: e.javaClass.simpleName }
            running = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("E2 · DNS 优化") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            }
        )
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Target: $DEMO_URL", style = MaterialTheme.typography.caption)
            Text(
                "Slow lane: 模拟运营商 DNS 每次 ~500ms，25% 长尾 1500ms\n" +
                        "HttpDNS lane: 三层架构 cache→HttpDNS→fallback，SWR 1min 强新鲜",
                style = MaterialTheme.typography.caption,
                modifier = Modifier.padding(top = 4.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(
                    enabled = !running,
                    onClick = { runLane(DnsLane.SLOW, 5) },
                    modifier = Modifier.weight(1f),
                ) { Text("Slow DNS ×5", fontSize = 12.sp) }
                Button(
                    enabled = !running,
                    onClick = { runLane(DnsLane.HTTPDNS, 5) },
                    modifier = Modifier.weight(1f),
                ) { Text("HttpDNS ×5", fontSize = 12.sp) }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(
                    enabled = !running,
                    onClick = {
                        httpDns.prefetch(DEMO_HOST)
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Prefetch HttpDNS", fontSize = 12.sp) }
                Button(
                    enabled = !running,
                    onClick = {
                        httpDns.clear()
                        runs.clear()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Clear cache+rows", fontSize = 12.sp) }
            }

            err?.let {
                Text(
                    "Error: $it",
                    color = MaterialTheme.colors.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            DnsLaneSummary(runs)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(runs) { r ->
                Card(elevation = 2.dp) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            "${r.lane.label} · #${r.attempt}",
                            style = MaterialTheme.typography.subtitle2,
                            color = r.lane.color,
                        )
                        r.timing?.let { WaterfallChart(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DnsLaneSummary(runs: List<DnsRun>) {
    val perLane = DnsLane.values().associateWith { lane ->
        runs.filter { it.lane == lane && it.timing != null }
    }
    val slow = perLane[DnsLane.SLOW].orEmpty()
    val fast = perLane[DnsLane.HTTPDNS].orEmpty()
    if (slow.isEmpty() && fast.isEmpty()) return

    Column(modifier = Modifier.padding(top = 8.dp)) {
        if (slow.isNotEmpty()) {
            val avgDns = slow.map { it.timing!!.dnsMs }.average().toLong()
            val maxDns = slow.maxOf { it.timing!!.dnsMs }
            Text(
                "Slow DNS: avg DNS ${avgDns}ms · max ${maxDns}ms (n=${slow.size})",
                fontSize = 12.sp,
                color = DnsLane.SLOW.color,
            )
        }
        if (fast.isNotEmpty()) {
            val avgDns = fast.map { it.timing!!.dnsMs }.average().toLong()
            val maxDns = fast.maxOf { it.timing!!.dnsMs }
            Text(
                "HttpDNS:  avg DNS ${avgDns}ms · max ${maxDns}ms (n=${fast.size})",
                fontSize = 12.sp,
                color = DnsLane.HTTPDNS.color,
            )
        }
    }
}
