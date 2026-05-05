package com.iwatchme.android.demo.cocosshell

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.iwatchme.cocosshell.bridge.JsbBridge
import com.iwatchme.cocosshell.bridge.JsbHost
import com.iwatchme.cocosshell.bridge.handlers.VoiceEvalJsb
import com.iwatchme.cocosshell.download.DownloadProgress
import com.iwatchme.cocosshell.service.GameMessageService
import com.iwatchme.cocosshell.service.GameMessages
import com.iwatchme.voiceeval.VoiceEvalEngine
import com.iwatchme.voiceeval.encoder.Mp3Encoder
import com.iwatchme.voiceeval.scoring.MockVoiceScorer
import com.iwatchme.voiceeval.upload.MockAudioUploader
import java.io.File

/**
 * Game-process container Activity. Mirrors the original Jiliguala
 * `BaseGameActivity` role:
 *
 *  1. `onCreate`: bind to [GameMessageService] (which lives in the main
 *     process) so the package download is coordinated by the main
 *     process — same as the original (network stack, cookies, account
 *     headers all live in main, this process just consumes the result).
 *  2. As progress messages stream in, animate the loading bar (download
 *     0..30%, unzip 30..40% — same banding as the original via
 *     [DownloadProgress]).
 *  3. On `MSG_RESULT` success, swap the loading UI for a [WebView] wired
 *     up with [JsbBridge] + [VoiceEvalJsb] (the analog of
 *     `Cocos2dxActivity.startRenderGame()` + `setResPath(unzipFilePath)`),
 *     and load `file://${gameDir}/index.html`.
 *
 * Declared with `android:process=":cocos_game"` in the app manifest, so
 * `adb shell ps -A | grep iwatchme` shows two processes when this is
 * mounted — the literal "container isolation" demonstration the resume
 * claim points at.
 */
class CocosGameActivity : ComponentActivity() {

    private var serviceMessenger: Messenger? = null
    private val replyMessenger = Messenger(Handler(Looper.getMainLooper(), ::onIncoming))

    private val state = mutableStateOf<GameStage>(GameStage.Connecting)
    private val webViewSlot = mutableStateOf<WebView?>(null)
    private var bridge: JsbBridge? = null

    private val engine: VoiceEvalEngine by lazy {
        VoiceEvalEngine.Builder(this)
            .encoder(::Mp3Encoder)
            .scorer(MockVoiceScorer(seed = 42L, networkLatencyMs = 500))
            .uploader(MockAudioUploader(this))
            .silenceAutoStop(thresholdDb = 55, quietForMs = 2_500, triggerAfterMs = 5_000)
            .scoringTimeoutMs(5_000)
            .build()
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
            val svc = Messenger(binder)
            serviceMessenger = svc
            svc.send(Message.obtain(null, GameMessages.MSG_REGISTER_CLIENT).apply {
                replyTo = replyMessenger
            })
            val url = intent.getStringExtra(EXTRA_URL) ?: DEFAULT_URL
            svc.send(Message.obtain(null, GameMessages.MSG_CHECK_DOWNLOAD).apply {
                replyTo = replyMessenger
                data = Bundle().apply { putString(GameMessages.KEY_URL, url) }
            })
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceMessenger = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val current by state
            val webView by webViewSlot
            if (webView != null) {
                AndroidView(modifier = Modifier.fillMaxSize(), factory = { webView!! })
            } else {
                LoadingPanel(current)
            }
        }
        bindService(
            Intent(this, GameMessageService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
    }

    private fun onIncoming(msg: Message): Boolean {
        when (msg.what) {
            GameMessages.MSG_PROGRESS -> {
                val phase = msg.data?.getString(GameMessages.KEY_PHASE)
                val percent = msg.data?.getInt(GameMessages.KEY_PERCENT) ?: 0
                state.value = when (phase) {
                    GameMessages.PHASE_DOWNLOADING -> GameStage.Downloading(percent)
                    GameMessages.PHASE_UNZIPPING -> GameStage.Unzipping(percent)
                    else -> state.value
                }
            }
            GameMessages.MSG_RESULT -> {
                val ok = msg.data?.getBoolean(GameMessages.KEY_SUCCESS) == true
                if (ok) {
                    val gameDirPath = msg.data?.getString(GameMessages.KEY_GAME_DIR)
                    val cached = msg.data?.getBoolean(GameMessages.KEY_CACHED) == true
                    if (gameDirPath != null) {
                        state.value = GameStage.Ready(cached)
                        mountWebView(File(gameDirPath))
                    } else {
                        state.value = GameStage.Failed("missing gameDir")
                    }
                } else {
                    state.value = GameStage.Failed(
                        msg.data?.getString(GameMessages.KEY_ERROR) ?: "unknown",
                    )
                }
            }
        }
        return true
    }

    private fun mountWebView(gameDir: File) {
        val wv = WebView(this)
        // Local JsbHost — we can't reference `bridge` before assigning it,
        // so we wire eval directly to the same WebView we'll hand to
        // JsbBridge. After build() the bridge is the canonical owner;
        // this host is only used by [VoiceEvalJsb] callbacks.
        val host = object : JsbHost {
            override fun evalJs(js: String) {
                wv.post { wv.evaluateJavascript(js, null) }
            }
        }
        bridge = JsbBridge.Builder(wv)
            .register(VoiceEvalJsb(engine, host = host, scope = lifecycleScope))
            .build()
        bridge!!.load("file://${gameDir.absolutePath}/index.html")
        webViewSlot.value = wv
    }

    override fun onDestroy() {
        bridge?.detach()
        bridge = null
        engine.stop()
        runCatching {
            serviceMessenger?.send(Message.obtain(null, GameMessages.MSG_DETACH).apply {
                replyTo = replyMessenger
            })
        }
        runCatching { unbindService(connection) }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
        const val DEFAULT_URL = "asset://cocos-game.zip"
    }
}

private sealed class GameStage {
    abstract val label: String
    abstract val progress: Float

    object Connecting : GameStage() {
        override val label = "Binding to GameMessageService…"
        override val progress = 0f
    }
    data class Downloading(val percent: Int) : GameStage() {
        override val label = "Downloading… $percent%"
        override val progress = DownloadProgress.globalPercent(percent / 100f, 0f) / 100f
    }
    data class Unzipping(val percent: Int) : GameStage() {
        override val label = "Unzipping… $percent%"
        override val progress = DownloadProgress.globalPercent(1f, percent / 100f) / 100f
    }
    data class Ready(val cached: Boolean) : GameStage() {
        override val label = if (cached) "Cache hit. Mounting WebView…" else "Mounting WebView…"
        override val progress = 0.4f
    }
    data class Failed(val reason: String) : GameStage() {
        override val label = "Failed: $reason"
        override val progress = 0f
    }
}

@Composable
private fun LoadingPanel(stage: GameStage) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "Cocos Game Container", style = MaterialTheme.typography.h6)
            Text(
                text = "Running in :cocos_game process · adb shell ps -A | grep iwatchme to verify",
                style = MaterialTheme.typography.caption,
            )
            LinearProgressIndicator(
                progress = stage.progress,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Text(text = stage.label, style = MaterialTheme.typography.body2)
        }
    }
}
