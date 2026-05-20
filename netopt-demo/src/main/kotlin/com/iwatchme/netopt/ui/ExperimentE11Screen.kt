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
import androidx.compose.material.OutlinedTextField
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
import com.iwatchme.netopt.data.LocalNote
import com.iwatchme.netopt.data.NoteStore
import com.iwatchme.netopt.net.ApiHost
import com.iwatchme.netopt.net.ClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@Composable
fun ExperimentE11Screen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { NoteStore(context) }
    val notes = remember { mutableStateListOf<LocalNote>().apply { addAll(store.all()) } }
    var draft by remember { mutableStateOf("") }
    var syncing by remember { mutableStateOf(false) }
    var simulateOffline by remember { mutableStateOf(false) }
    var lastSaveMs by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        notes.clear(); notes.addAll(store.all().sortedByDescending { it.updatedAt })
    }

    fun save() {
        if (draft.isBlank()) return
        val t0 = System.currentTimeMillis()
        store.add(draft.trim())
        val dt = System.currentTimeMillis() - t0
        lastSaveMs = dt
        draft = ""
        refresh()
    }

    suspend fun pushOne(note: LocalNote): Long? {
        if (simulateOffline) return null
        return withContext(Dispatchers.IO) {
            try {
                val client = ClientFactory.baseline { }
                val body = JSONObject()
                    .put("localId", note.localId)
                    .put("text", note.text)
                    .toString()
                    .toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url("${ApiHost.baseUrl}/api/opt/notes")
                    .post(body)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val text = resp.body!!.string()
                    JSONObject(text).optLong("serverId", -1L).takeIf { it > 0 }
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    fun syncPending() {
        if (syncing) return
        scope.launch {
            syncing = true
            val pending = store.all().filter { it.status == "PENDING" }
            pending.forEach { note ->
                val serverId = pushOne(note)
                if (serverId != null) store.markSynced(note.localId, serverId)
                refresh()
            }
            syncing = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("E11 · 离线优先") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            }
        )
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "1) 输入 → 保存 → 本地立刻可见（写入耗时显示在下方）\n" +
                        "2) 点 Sync 把 PENDING 提交到 ${ApiHost.baseUrl}/api/opt/notes\n" +
                        "3) Simulate offline 开关：模拟无网络时保存仍工作",
                style = MaterialTheme.typography.caption,
            )

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("note text") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(onClick = { save() }, modifier = Modifier.weight(1f)) { Text("Save (local)") }
                Button(onClick = { syncPending() }, enabled = !syncing, modifier = Modifier.weight(1f)) {
                    Text(if (syncing) "Syncing..." else "Sync PENDING")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(
                    onClick = { simulateOffline = !simulateOffline },
                    modifier = Modifier.weight(1f),
                ) { Text(if (simulateOffline) "Offline ON" else "Offline OFF") }
                Button(
                    onClick = { store.clear(); refresh() },
                    modifier = Modifier.weight(1f),
                ) { Text("Clear all") }
            }

            lastSaveMs?.let {
                Text(
                    "last local save: ${it}ms",
                    fontSize = 11.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(notes) { note ->
                Card(elevation = 1.dp) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                note.status,
                                color = if (note.status == "SYNCED")
                                    Color(0xFF388E3C) else Color(0xFFE65100),
                                fontSize = 11.sp,
                            )
                            note.serverId?.let {
                                Text("server#$it", fontSize = 11.sp,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                            }
                        }
                        Text(note.text, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
