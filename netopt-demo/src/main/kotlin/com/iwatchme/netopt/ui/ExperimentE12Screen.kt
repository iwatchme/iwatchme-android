package com.iwatchme.netopt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

private sealed class ResolveResult(val tier: String, val body: String, val color: Color) {
    class Fresh(body: String) : ResolveResult("L1 network · FRESH", body, Color(0xFF388E3C))
    class Stale(body: String) : ResolveResult("L2 OkHttp cache · STALE", body, Color(0xFFFFA000))
    class Fallback(body: String) : ResolveResult("L3 APK asset · FALLBACK", body, Color(0xFFD32F2F))
    class Empty(reason: String) : ResolveResult("none · $reason", "", Color(0xFF616161))
}

// All three scenarios use the SAME cacheable URL — otherwise OkHttp's cache
// key would not match and a failed lookup would skip L2 and go straight to L3.
// Strong cache mode keeps a 60s body in OkHttp's disk cache — the dependency
// for L2 to fire when L1 is skipped. ETag-only mode would force revalidation
// and OkHttp would refuse to serve from cache without network.
private const val URL_CONFIG = "/api/opt/config?cache=strong"

@Composable
fun ExperimentE12Screen(onBack: () -> Unit) {
    val context = LocalContext.current
    var result by remember { mutableStateOf<ResolveResult?>(null) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val cachedClient = remember { ClientFactory.cached(context.cacheDir) { } }
    val plainClient = remember { ClientFactory.baseline { } }

    suspend fun readNetwork(path: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("${ApiHost.baseUrl}$path").build()
            cachedClient.newCall(req).execute().use { resp ->
                if (resp.code in 200..299) resp.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun readCacheOnly(path: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("${ApiHost.baseUrl}$path")
                .header("Cache-Control", "only-if-cached, max-stale=604800")
                .build()
            // Use a copy that won't try network — OkHttp resolves from cache or 504.
            cachedClient.newCall(req).execute().use { resp ->
                if (resp.code in 200..299) resp.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun readAsset(): String? = try {
        context.assets.open("fallback_config.json").bufferedReader().use { it.readText() }
    } catch (_: Exception) { null }

    suspend fun resolveFresh(): ResolveResult {
        readNetwork(URL_CONFIG)?.let { return ResolveResult.Fresh(it) }
        readCacheOnly(URL_CONFIG)?.let { return ResolveResult.Stale(it) }
        readAsset()?.let { return ResolveResult.Fallback(it) }
        return ResolveResult.Empty("nothing left")
    }

    /** Skip L1 to simulate "no network" — go cache → asset. */
    suspend fun resolveOffline(): ResolveResult {
        readCacheOnly(URL_CONFIG)?.let { return ResolveResult.Stale(it) }
        readAsset()?.let { return ResolveResult.Fallback(it) }
        return ResolveResult.Empty("nothing left")
    }

    fun runHealthy()  { if (!running) scope.launch { running = true; result = resolveFresh(); running = false } }
    fun runBroken()   { if (!running) scope.launch { running = true; result = resolveOffline(); running = false } }
    fun runWipeBroken() {
        if (running) return
        try { java.io.File(context.cacheDir, "http_cache_e6").deleteRecursively() } catch (_: Exception) {}
        scope.launch { running = true; result = resolveOffline(); running = false }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("E12 · 三级容灾") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            }
        )
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Resolve 顺序: 网络 → OkHttp 缓存 → APK assets/fallback_config.json\n" +
                        "1) 先点 Healthy 让缓存暖起来\n" +
                        "2) Broken 模拟网络全挂 → 看是否仍能返回 cache\n" +
                        "3) 点 Wipe cache + Broken → 看是否走到 asset 兜底",
                style = MaterialTheme.typography.caption,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(
                    enabled = !running,
                    onClick = { runHealthy() },
                    modifier = Modifier.weight(1f),
                ) { Text("Healthy", fontSize = 11.sp) }
                Button(
                    enabled = !running,
                    onClick = { runBroken() },
                    modifier = Modifier.weight(1f),
                ) { Text("Offline", fontSize = 11.sp) }
                Button(
                    enabled = !running,
                    onClick = { runWipeBroken() },
                    modifier = Modifier.weight(1f),
                ) { Text("Wipe+Offline", fontSize = 11.sp) }
            }

            result?.let { r ->
                Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(r.tier, color = r.color, fontSize = 13.sp)
                        Text(
                            r.body.take(600),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        }
    }
}
