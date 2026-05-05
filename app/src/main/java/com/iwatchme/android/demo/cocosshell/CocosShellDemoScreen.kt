package com.iwatchme.android.demo.cocosshell

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iwatchme.cocosshell.download.DownloadCache

/**
 * Compose-side entry for the cocos-shell demo. This Composable runs in
 * the **main** process; tapping the Launch button starts
 * [CocosGameActivity], which lives in the `:cocos_game` process and
 * binds back to `GameMessageService` (also in main).
 *
 * Cache invalidation runs here (main process) by calling [DownloadCache]
 * directly — the cache lives at main-process `filesDir/rootfiles/...`
 * which is the same UID-shared dir the game process reads via the
 * Service-mediated download path.
 */
@Composable
fun CocosShellDemoScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var transport by remember { mutableStateOf(Transport.Asset) }
    var customUrl by remember { mutableStateOf("http://10.0.2.2:8000/cocos-game.zip") }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Cocos Bridge & Downloader", style = MaterialTheme.typography.h6)
        Text(
            text = "Faithful reproduction of the Jiliguala Cocos integration: " +
                ":cocos_game process + Messenger IPC + URL-keyed package cache + " +
                "WebView-hosted JS↔Native bridge calling into voice-eval.",
            style = MaterialTheme.typography.caption,
        )

        ArchitectureCard()

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            elevation = 2.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Downloader Strategy", style = MaterialTheme.typography.subtitle2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = transport == Transport.Asset,
                        onClick = { transport = Transport.Asset },
                    )
                    Text("Asset (offline, bundled cocos-game.zip)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = transport == Transport.OkHttp,
                        onClick = { transport = Transport.OkHttp },
                    )
                    Text("OkHttp (real network)")
                }
                if (transport == Transport.OkHttp) {
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        label = { Text("Package URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Text(
                        text = "Tip: run python3 -m http.server in cocos-shell/src/main/assets/ " +
                            "and use 10.0.2.2 from the emulator, or your LAN IP from a real device.",
                        style = MaterialTheme.typography.caption,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    val url = when (transport) {
                        Transport.Asset -> CocosGameActivity.DEFAULT_URL
                        Transport.OkHttp -> customUrl
                    }
                    context.startActivity(
                        Intent(context, CocosGameActivity::class.java)
                            .putExtra(CocosGameActivity.EXTRA_URL, url),
                    )
                    statusMsg = "Launched :cocos_game with URL = $url"
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Launch Cocos Game")
            }
            Button(
                onClick = {
                    DownloadCache(context.applicationContext).invalidate()
                    statusMsg = "Cache invalidated. Next launch will re-download."
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.error,
                ),
                modifier = Modifier.weight(1f),
            ) {
                Text("Clear Cache")
            }
        }

        statusMsg?.let { Text(text = it, style = MaterialTheme.typography.caption) }

        Spacer(Modifier.height(12.dp))
        VerificationCard()
    }
}

private enum class Transport { Asset, OkHttp }

@Composable
private fun ArchitectureCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Architecture", style = MaterialTheme.typography.subtitle2)
            Text(
                text = "main process: GameMessageService + CocosBaseDownloadMgr (URL-keyed " +
                    "cache + md5 sentinel)\n" +
                    ":cocos_game: CocosGameActivity → bind service → Messenger IPC → " +
                    "WebView + JsbBridge → JS calls Native.startRecording → " +
                    "VoiceEvalEngine (real LAME, mock scorer) → onRecordResult → DOM update",
                style = MaterialTheme.typography.caption,
            )
        }
    }
}

@Composable
private fun VerificationCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Verification", style = MaterialTheme.typography.subtitle2)
            Text(
                text = "1. Tap Launch. While the loading bar runs:\n" +
                    "   adb shell ps -A | grep iwatchme  →  expect 2 PIDs (main + :cocos_game)\n" +
                    "2. After WebView mounts, tap Start Recording inside the page, speak.\n" +
                    "3. adb shell run-as com.iwatchme.android cat files/rootfiles/cocosgame/success.txt\n" +
                    "   →  prints md5(url). Cache integrity sentinel.",
                style = MaterialTheme.typography.caption,
            )
        }
    }
}
