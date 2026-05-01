package com.iwatchme.android.demo.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iwatchme.android.BuildConfig
import io.tts.sdk.TtsEngine
import io.tts.sdk.TtsItem
import io.tts.sdk.TtsVoiceParams
import io.tts.sdk.cloudflare.CloudflareTtsSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "TtsDemoScreen"

private const val DEFAULT_TEXT = "Hello, this is a text to speech demo. Welcome to try different voices."
private const val SAMPLE_RATE = 24000
private const val WRITE_CHUNK_SIZE = 4096

@Composable
fun TtsDemoScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf(DEFAULT_TEXT) }
    var playingSpeaker by remember { mutableStateOf<String?>(null) }
    var loadingSpeaker by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var audioTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var playJob by remember { mutableStateOf<Job?>(null) }
    var ttsEngine by remember { mutableStateOf<TtsEngine?>(null) }

    val cacheDirPath = remember { context.cacheDir.resolve("tts_demo").apply { mkdirs() }.absolutePath }

    fun getOrCreateEngine(): TtsEngine {
        return ttsEngine ?: TtsEngine.Builder()
            .sdk(CloudflareTtsSdk.SOURCE_CLOUDFLARE, factory = {
                CloudflareTtsSdk(
                    accountId = BuildConfig.CLOUDFLARE_ACCOUNT_ID,
                    apiToken = BuildConfig.CLOUDFLARE_API_TOKEN,
                ).also { it.initialize() }
            })
            .cacheDirPath(cacheDirPath)
            .build()
            .also { ttsEngine = it }
    }

    fun stopPlayback() {
        playJob?.cancel()
        playJob = null
        audioTrack?.run {
            stop()
            release()
        }
        audioTrack = null
        playingSpeaker = null
    }

    fun playSpeaker(speaker: String) {
        if (inputText.isBlank()) {
            errorMessage = "Please enter some text"
            return
        }

        if (playingSpeaker == speaker) {
            stopPlayback()
            return
        }

        stopPlayback()
        loadingSpeaker = speaker
        errorMessage = null

        playJob = scope.launch {
            try {
                val engine = getOrCreateEngine()
                val voice = TtsVoiceParams(
                    voiceType = speaker,
                    encodeType = TtsVoiceParams.ENCODE_TYPE_PCM,
                    sampleRate = SAMPLE_RATE,
                )
                val items = listOf(TtsItem(text = inputText, source = CloudflareTtsSdk.SOURCE_CLOUDFLARE))

                // Download full PCM file first
                val results = withContext(Dispatchers.IO) {
                    engine.generate(items, voice)
                }
                val result = results.firstOrNull()
                if (result == null || !result.isSuccess) {
                    errorMessage = result?.error?.message ?: "TTS generation failed"
                    loadingSpeaker = null
                    return@launch
                }

                val pcmFile = File(result.filePath!!)
                val pcmData = withContext(Dispatchers.IO) { pcmFile.readBytes() }
                Log.d(TAG, "PCM downloaded: ${pcmData.size} bytes, duration=${pcmData.size / 2.0 / SAMPLE_RATE}s")

                // Create AudioTrack and play the full buffer
                val minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
                val track = AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                    maxOf(minBuf, pcmData.size),
                    AudioTrack.MODE_STATIC,
                    0,
                )
                track.write(pcmData, 0, pcmData.size)
                track.setNotificationMarkerPosition(pcmData.size / 2) // frame count = bytes / 2
                track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(t: AudioTrack) {
                        playingSpeaker = null
                        t.stop()
                        t.release()
                        audioTrack = null
                    }
                    override fun onPeriodicNotification(t: AudioTrack) {}
                })

                audioTrack = track
                track.play()
                playingSpeaker = speaker
                loadingSpeaker = null
            } catch (e: Exception) {
                Log.e(TAG, "playSpeaker error", e)
                errorMessage = e.message ?: "Unknown error"
                loadingSpeaker = null
                playingSpeaker = null
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopPlayback()
            scope.launch {
                ttsEngine?.close()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Cloudflare Aura-2 TTS",
            style = MaterialTheme.typography.h6,
        )

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Text to speak") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colors.error,
                style = MaterialTheme.typography.caption,
            )
        }

        Text(
            text = "Voices (${CloudflareTtsSdk.SPEAKERS.size})",
            style = MaterialTheme.typography.subtitle1,
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(CloudflareTtsSdk.SPEAKERS, key = { it }) { speaker ->
                val isPlaying = playingSpeaker == speaker
                val isLoading = loadingSpeaker == speaker

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isLoading) { playSpeaker(speaker) },
                    elevation = if (isPlaying) 4.dp else 1.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = speaker.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.body1,
                            modifier = Modifier.weight(1f),
                        )

                        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            when {
                                isLoading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                isPlaying -> Icon(Icons.Default.Close, contentDescription = "Stop", tint = MaterialTheme.colors.primary)
                                else -> Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                            }
                        }
                    }
                }
            }
        }
    }
}
