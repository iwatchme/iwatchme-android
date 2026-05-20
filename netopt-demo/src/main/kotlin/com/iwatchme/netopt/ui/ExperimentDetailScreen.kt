package com.iwatchme.netopt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iwatchme.netopt.net.ApiHost
import com.iwatchme.netopt.net.ClientFactory
import com.iwatchme.netopt.net.monitor.TimingRecord
import com.iwatchme.netopt.ui.component.WaterfallChart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@Composable
fun ExperimentDetailScreen(
    experimentId: String,
    title: String,
    onBack: () -> Unit,
) {
    val records = remember { mutableStateListOf<TimingRecord>() }
    var running by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val client: OkHttpClient = remember {
        ClientFactory.baseline { rec ->
            // EventListener fires on the network thread; LiveData/State updates
            // need to be marshalled but mutableStateListOf is safe to write off
            // the main thread for Compose snapshots in this simple demo.
            records.add(0, rec)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("$experimentId · $title") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(modifier = Modifier.padding(12.dp)) {
            Text("Target: ${ApiHost.baseUrl}/api/opt/feed", style = MaterialTheme.typography.caption)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    enabled = !running,
                    onClick = {
                        scope.launch {
                            running = true
                            lastError = null
                            try {
                                runRequest(client)
                            } catch (e: Exception) {
                                lastError = e.message ?: e.javaClass.simpleName
                            }
                            running = false
                        }
                    }
                ) { Text(if (running) "Running..." else "Run once") }

                Button(
                    enabled = !running,
                    onClick = {
                        scope.launch {
                            running = true
                            lastError = null
                            try {
                                repeat(5) { runRequest(client) }
                            } catch (e: Exception) {
                                lastError = e.message ?: e.javaClass.simpleName
                            }
                            running = false
                        }
                    }
                ) { Text("Run x5") }

                Button(enabled = records.isNotEmpty(), onClick = { records.clear() }) {
                    Text("Clear")
                }
            }

            lastError?.let { err ->
                Text(
                    "Error: $err",
                    color = MaterialTheme.colors.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(records) { idx, rec ->
                Card(elevation = 2.dp) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "#${records.size - idx} → ${if (rec.success) "OK" else "FAIL(${rec.errorType})"}",
                            style = MaterialTheme.typography.subtitle2
                        )
                        WaterfallChart(rec)
                    }
                }
            }
        }
    }
}

private suspend fun runRequest(client: OkHttpClient) = withContext(Dispatchers.IO) {
    val req = Request.Builder()
        .url("${ApiHost.baseUrl}/api/opt/feed?limit=50")
        .build()
    client.newCall(req).execute().use { resp ->
        // Drain the body so responseBodyEnd fires with the correct byte count.
        resp.body?.bytes()
    }
}
