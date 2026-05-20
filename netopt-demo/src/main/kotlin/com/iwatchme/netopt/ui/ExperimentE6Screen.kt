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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iwatchme.netopt.net.ApiHost
import com.iwatchme.netopt.net.ClientFactory
import com.iwatchme.netopt.net.monitor.TimingRecord
import com.iwatchme.netopt.ui.component.WaterfallChart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

enum class CacheMode(val label: String, val query: String) {
    NONE("No-Store", "none"),
    ETAG("ETag (304)", "etag"),
    STRONG("Strong (max-age)", "strong"),
}

enum class CacheHit(val label: String, val color: Color) {
    FRESH_NET("FRESH 200 (wire)", Color(0xFFD32F2F)),
    NEGOTIATED_304("304 revalidated", Color(0xFFFFA000)),
    LOCAL_CACHE("LOCAL cache hit", Color(0xFF388E3C)),
}

data class CacheResult(
    val mode: CacheMode,
    val attempt: Int,
    val hit: CacheHit,
    val timing: TimingRecord?,
    val statusCode: Int,
)

@Composable
fun ExperimentE6Screen(onBack: () -> Unit) {
    val context = LocalContext.current
    val results = remember { mutableStateListOf<CacheResult>() }
    var running by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val pendingTiming = remember { mutableStateOf<TimingRecord?>(null) }
    val client: OkHttpClient = remember {
        ClientFactory.cached(context.cacheDir) { rec ->
            pendingTiming.value = rec
        }
    }

    suspend fun runOnce(mode: CacheMode, attempt: Int) {
        pendingTiming.value = null
        val (hit, status) = withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("${ApiHost.baseUrl}/api/opt/config?cache=${mode.query}")
                .build()
            client.newCall(req).execute().use { resp ->
                val h = classify(resp)
                resp.body?.bytes()
                h to resp.code
            }
        }
        results.add(0, CacheResult(mode, attempt, hit, pendingTiming.value, status))
    }

    fun runThreeTimes(mode: CacheMode) {
        if (running) return
        scope.launch {
            running = true; err = null
            try {
                repeat(3) { i -> runOnce(mode, i + 1) }
            } catch (e: Exception) {
                err = e.message ?: e.javaClass.simpleName
            }
            running = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("E6 · HTTP 缓存") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            }
        )
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Target: ${ApiHost.baseUrl}/api/opt/config?cache=…",
                style = MaterialTheme.typography.caption
            )
            Text(
                "每个 mode 跑 3 次：第 1 次 FRESH 200，后续看 cache 行为",
                style = MaterialTheme.typography.caption,
                modifier = Modifier.padding(top = 4.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CacheMode.values().forEach { mode ->
                    Button(
                        enabled = !running,
                        onClick = { runThreeTimes(mode) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(mode.label, fontSize = 11.sp)
                    }
                }
            }
            Button(
                enabled = !running && results.isNotEmpty(),
                onClick = { results.clear() },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) { Text("Clear") }

            err?.let {
                Text(
                    "Error: $it",
                    color = MaterialTheme.colors.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(results) { r ->
                Card(elevation = 2.dp) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            "${r.mode.label} · #${r.attempt} · HTTP ${r.statusCode}",
                            style = MaterialTheme.typography.subtitle2,
                        )
                        Text(
                            r.hit.label,
                            color = r.hit.color,
                            style = MaterialTheme.typography.body2,
                        )
                        val tr = r.timing
                        if (tr != null) {
                            WaterfallChart(tr)
                        } else {
                            Text(
                                "no network IO · ~0ms",
                                fontSize = 11.sp,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun classify(resp: Response): CacheHit {
    val net = resp.networkResponse
    val cache = resp.cacheResponse
    return when {
        net == null && cache != null -> CacheHit.LOCAL_CACHE
        net != null && cache != null -> CacheHit.NEGOTIATED_304
        else -> CacheHit.FRESH_NET
    }
}
