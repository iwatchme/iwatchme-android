package com.iwatchme.netopt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iwatchme.netopt.data.FeedDecoder
import com.iwatchme.netopt.data.FeedItemView
import com.iwatchme.netopt.net.ApiHost
import com.iwatchme.netopt.net.ClientFactory
import com.iwatchme.netopt.net.EncodingType
import com.iwatchme.netopt.net.monitor.TimingRecord
import com.iwatchme.netopt.ui.component.EncodingBarChart
import com.iwatchme.netopt.ui.component.WaterfallChart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

@Composable
fun ExperimentE5Screen(onBack: () -> Unit) {
    val latestTiming = remember { mutableStateMapOf<EncodingType, TimingRecord>() }
    val latestItems = remember { mutableStateMapOf<EncodingType, List<FeedItemView>>() }
    val decodeError = remember { mutableStateMapOf<EncodingType, String>() }
    var running by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun fetchOne(type: EncodingType) {
        val client = ClientFactory.forEncoding(type) { rec -> latestTiming[type] = rec }
        val raw: ByteArray = withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("${ApiHost.baseUrl}/api/opt/feed?limit=100")
                .build()
            client.newCall(req).execute().use { it.body!!.bytes() }
        }
        try {
            latestItems[type] = FeedDecoder.decode(type, raw)
            decodeError.remove(type)
        } catch (e: Throwable) {
            decodeError[type] = e.message ?: e.javaClass.simpleName
        }
    }

    fun runOne(type: EncodingType) {
        if (running) return
        scope.launch {
            running = true; err = null
            try { fetchOne(type) } catch (e: Exception) { err = e.message ?: e.javaClass.simpleName }
            running = false
        }
    }

    fun runAll() {
        if (running) return
        scope.launch {
            running = true; err = null
            try { EncodingType.values().forEach { fetchOne(it) } }
            catch (e: Exception) { err = e.message ?: e.javaClass.simpleName }
            running = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("E5 · 编码对比") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Target: ${ApiHost.baseUrl}/api/opt/feed?limit=100", style = MaterialTheme.typography.caption)

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EncodingType.values().forEach { type ->
                    Button(
                        enabled = !running,
                        onClick = { runOne(type) },
                        modifier = Modifier.weight(1f),
                    ) { Text(type.label) }
                }
            }

            Button(
                enabled = !running,
                onClick = { runAll() },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                Text(if (running) "Running..." else "Run all 4")
            }

            err?.let {
                Text(
                    "Error: $it",
                    color = MaterialTheme.colors.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            EncodingBarChart(
                bytesByType = EncodingType.values()
                    .associateWith { (latestTiming[it]?.respBytes ?: 0L) }
            )
        }

        val rows = EncodingType.values().mapNotNull { type ->
            latestTiming[type]?.let { type to it }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rows) { (type, rec) ->
                Card(elevation = 2.dp) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        val items = latestItems[type]
                        val decErr = decodeError[type]
                        val ratioVsJson = latestTiming[EncodingType.JSON]?.respBytes?.let { json ->
                            if (json > 0 && rec.respBytes > 0) {
                                val pct = (rec.respBytes.toFloat() / json.toFloat()) * 100f
                                " · ${"%.1f".format(pct)}% of JSON"
                            } else ""
                        } ?: ""

                        Text(
                            text = "${type.label} → ${rec.respBytes}B$ratioVsJson",
                            style = MaterialTheme.typography.subtitle2
                        )
                        WaterfallChart(rec)

                        Spacer(Modifier.height(4.dp))
                        when {
                            decErr != null -> Text(
                                "decode error: $decErr",
                                color = MaterialTheme.colors.error,
                                fontSize = 11.sp,
                            )
                            items == null -> Text("decoding...", fontSize = 11.sp)
                            else -> DecodedFeedPreview(items)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DecodedFeedPreview(items: List<FeedItemView>) {
    Text(
        "Decoded ${items.size} items · showing first 3",
        fontSize = 11.sp,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
    )
    items.take(3).forEach { it ->
        val snippet = it.content.take(58).let { s ->
            if (it.content.length > s.length) "$s…" else s
        }
        Text(
            text = "  #${it.id} v${it.version} · ${it.title}",
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = "  $snippet",
            fontSize = 10.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
        )
    }
}
