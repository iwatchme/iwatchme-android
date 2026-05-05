package com.iwatchme.cocosshell.service

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import com.iwatchme.cocosshell.download.AssetPackageDownloader
import com.iwatchme.cocosshell.download.CocosBaseDownloadMgr
import com.iwatchme.cocosshell.download.DownloadState
import com.iwatchme.cocosshell.download.OkHttpPackageDownloader
import com.iwatchme.cocosshell.download.PackageDownloader
import java.io.File
import com.iwatchme.cocosshell.service.GameMessages.KEY_CACHED
import com.iwatchme.cocosshell.service.GameMessages.KEY_ERROR
import com.iwatchme.cocosshell.service.GameMessages.KEY_GAME_DIR
import com.iwatchme.cocosshell.service.GameMessages.KEY_PERCENT
import com.iwatchme.cocosshell.service.GameMessages.KEY_PHASE
import com.iwatchme.cocosshell.service.GameMessages.KEY_SUCCESS
import com.iwatchme.cocosshell.service.GameMessages.KEY_URL
import com.iwatchme.cocosshell.service.GameMessages.MSG_CHECK_DOWNLOAD
import com.iwatchme.cocosshell.service.GameMessages.MSG_DETACH
import com.iwatchme.cocosshell.service.GameMessages.MSG_INVALIDATE
import com.iwatchme.cocosshell.service.GameMessages.MSG_PROGRESS
import com.iwatchme.cocosshell.service.GameMessages.MSG_REGISTER_CLIENT
import com.iwatchme.cocosshell.service.GameMessages.MSG_RESULT
import com.iwatchme.cocosshell.service.GameMessages.PHASE_DOWNLOADING
import com.iwatchme.cocosshell.service.GameMessages.PHASE_UNZIPPING
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Bound Messenger service that lives in the **main** process and serves
 * the **`:cocos_game`** process. Same role as the original Jiliguala
 * `GameMessageService`:
 *
 *  - keeps the `CocosBaseDownloadMgr` instance singleton in the main
 *    process (so cookies, network stack, account headers and the
 *    on-disk cache are not duplicated across processes)
 *  - coordinates download-progress + result messages back to the game
 *    process via Messenger reply channel
 *
 * The download cache lives at `mainProcess.filesDir/rootfiles/...` —
 * filesDir is shared across processes of the same app (same UID/data
 * dir), so the game-process Activity can read the extracted gameDir
 * after the service writes it. This is exactly how the original handled
 * it.
 */
class GameMessageService : Service() {

    /**
     * Scheme-aware downloader: `asset://...` routes to the bundled-asset
     * impl (default for offline demos), `http(s)://...` routes to OkHttp.
     * Lets us flip transports just by changing the URL — Strategy pattern
     * pushed to the URL boundary, no extra IPC needed.
     */
    private val schemeDispatcher = object : PackageDownloader {
        private val asset by lazy { AssetPackageDownloader(applicationContext) }
        private val http by lazy { OkHttpPackageDownloader() }
        override suspend fun download(
            url: String,
            destZip: File,
            onProgress: (Float) -> Unit,
        ) {
            val impl = if (url.startsWith("http://", true) || url.startsWith("https://", true)) http else asset
            impl.download(url, destZip, onProgress)
        }
    }

    private val downloadMgr by lazy {
        CocosBaseDownloadMgr(applicationContext, downloader = schemeDispatcher)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val incoming = Messenger(IncomingHandler(Looper.getMainLooper()))

    @Volatile
    private var client: Messenger? = null
    private var downloadJob: Job? = null

    override fun onBind(intent: Intent): IBinder = incoming.binder

    override fun onDestroy() {
        downloadJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private inner class IncomingHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_REGISTER_CLIENT -> client = msg.replyTo

                MSG_CHECK_DOWNLOAD -> {
                    val replyTo = msg.replyTo ?: client ?: return
                    client = replyTo
                    val url = msg.data?.getString(KEY_URL).orEmpty()
                    startDownload(replyTo, url)
                }

                MSG_INVALIDATE -> {
                    // Same DownloadCache the manager uses — wipes prefs + gameDir.
                    com.iwatchme.cocosshell.download.DownloadCache(applicationContext)
                        .invalidate()
                }

                MSG_DETACH -> {
                    if (msg.replyTo == client) client = null
                    downloadJob?.cancel()
                }

                else -> super.handleMessage(msg)
            }
        }
    }

    private fun startDownload(replyTo: Messenger, url: String) {
        downloadJob?.cancel()
        downloadJob = scope.launch {
            downloadMgr.ensureGamePackage(url, name = "base") { state ->
                replyTo.safeSend(state.toMessage())
            }
        }
    }

    private fun DownloadState.toMessage(): Message = when (this) {
        is DownloadState.Downloading -> Message.obtain().apply {
            what = MSG_PROGRESS
            data = Bundle().apply {
                putString(KEY_PHASE, PHASE_DOWNLOADING)
                putInt(KEY_PERCENT, percent)
            }
        }
        is DownloadState.Unzipping -> Message.obtain().apply {
            what = MSG_PROGRESS
            data = Bundle().apply {
                putString(KEY_PHASE, PHASE_UNZIPPING)
                putInt(KEY_PERCENT, percent)
            }
        }
        is DownloadState.Done -> Message.obtain().apply {
            what = MSG_RESULT
            data = Bundle().apply {
                putBoolean(KEY_SUCCESS, true)
                putString(KEY_GAME_DIR, gameDir.absolutePath)
                putBoolean(KEY_CACHED, cached)
            }
        }
        is DownloadState.Failed -> Message.obtain().apply {
            what = MSG_RESULT
            data = Bundle().apply {
                putBoolean(KEY_SUCCESS, false)
                putString(KEY_ERROR, error.message ?: error.javaClass.simpleName)
            }
        }
        DownloadState.Idle -> Message.obtain().apply { what = MSG_PROGRESS }  // never sent in practice
    }

    private fun Messenger.safeSend(msg: Message) {
        try {
            send(msg)
        } catch (e: RemoteException) {
            // The game process likely went away (back-pressed or killed).
            // Drop the message — the next bind will re-establish the channel.
            Log.w(TAG, "remote game process gone: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "GameMessageService"
    }
}
