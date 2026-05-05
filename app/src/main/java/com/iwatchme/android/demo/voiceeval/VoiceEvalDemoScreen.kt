@file:OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)

package com.iwatchme.android.demo.voiceeval

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.rememberPermissionState
import com.iwatchme.voiceeval.VoiceEvalEngine
import com.iwatchme.voiceeval.api.EvalRequest
import com.iwatchme.voiceeval.api.EvalResult
import com.iwatchme.voiceeval.api.EvalState
import com.iwatchme.voiceeval.api.ResultSource
import com.iwatchme.voiceeval.api.SilenceLevel
import com.iwatchme.voiceeval.api.WordScore
import com.iwatchme.voiceeval.encoder.AudioEncoder
import com.iwatchme.voiceeval.encoder.Mp3Encoder
import com.iwatchme.voiceeval.encoder.WavEncoder
import com.iwatchme.voiceeval.scoring.MockVoiceScorer
import com.iwatchme.voiceeval.upload.MockAudioUploader
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Demo screen wiring up [VoiceEvalEngine] with the mock scorer + mock uploader.
 *
 * The screen intentionally instantiates the engine right here in the
 * Composable rather than hiding it behind a ViewModel — this is a one-stop
 * tour for an interviewer reading the code:
 *
 *  - Builder-based configuration (Strategy injection)
 *  - Cold Flow<EvalState> consumption with state-driven UI
 *  - Mic permission gating (Accompanist)
 *  - Resource cleanup on disposal
 */
@Composable
fun VoiceEvalDemoScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val micPermission = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)

    var useMp3 by remember { mutableStateOf(true) }

    // Engine is rebuilt whenever the encoder choice flips so the next round
    // picks up the new strategy. The Mock scorer/uploader stay pinned.
    val engine = remember(useMp3) {
        val encoderFactory: () -> AudioEncoder = if (useMp3) ::Mp3Encoder else ::WavEncoder
        VoiceEvalEngine.Builder(context)
            .encoder(encoderFactory)
            .scorer(MockVoiceScorer(seed = 42L, networkLatencyMs = 500))
            .uploader(MockAudioUploader(context))
            .silenceAutoStop(thresholdDb = 55, quietForMs = 2_500, triggerAfterMs = 5_000)
            .scoringTimeoutMs(5_000)
            .build()
    }

    var refText by remember { mutableStateOf("Hello, how are you today?") }
    var stateLabel by remember { mutableStateOf("Idle") }
    var currentDb by remember { mutableStateOf(0) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var silenceHint by remember { mutableStateOf<SilenceLevel?>(null) }
    var result by remember { mutableStateOf<EvalResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var inFlightJob by remember { mutableStateOf<Job?>(null) }
    val running = inFlightJob != null

    DisposableEffect(Unit) {
        onDispose {
            inFlightJob?.cancel()
            engine.stop()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Voice Eval (Mock SOE + Mock QiNiu)", style = MaterialTheme.typography.h6)
        Text(
            text = "AudioRecord → Encoder → 4KB ChunkSlicer → Scorer (mock) → Uploader (mock).",
            style = MaterialTheme.typography.caption,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Encoder:", style = MaterialTheme.typography.body2)
            Button(
                onClick = { useMp3 = false },
                enabled = !running && useMp3,
            ) { Text("WAV") }
            Button(
                onClick = { useMp3 = true },
                enabled = !running && !useMp3,
            ) { Text("MP3 (LAME)") }
        }

        OutlinedTextField(
            value = refText,
            onValueChange = { refText = it },
            label = { Text("Reference text") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !running,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    if (!micPermission.hasPermission) {
                        micPermission.launchPermissionRequest()
                        return@Button
                    }
                    error = null
                    result = null
                    silenceHint = null
                    val request = EvalRequest(
                        id = "demo-${System.currentTimeMillis()}",
                        refText = refText.trim(),
                    )
                    inFlightJob = scope.launch {
                        engine.evaluate(request)
                            .onEach { state ->
                                when (state) {
                                    EvalState.Idle -> stateLabel = "Idle"
                                    EvalState.Preparing -> stateLabel = "Preparing"
                                    is EvalState.Recording -> {
                                        stateLabel = "Recording"
                                        currentDb = state.currentDb
                                        elapsedMs = state.elapsedMs
                                    }
                                    is EvalState.SilenceHint -> silenceHint = state.level
                                    EvalState.Scoring -> {
                                        stateLabel = "Scoring"
                                        currentDb = 0
                                    }
                                    is EvalState.Completed -> {
                                        stateLabel = "Done"
                                        result = state.result
                                    }
                                    is EvalState.Failed -> {
                                        stateLabel = "Failed"
                                        error = state.error.message
                                    }
                                }
                            }
                            .catch { t -> error = t.message }
                            .collect()
                        inFlightJob = null
                    }
                },
                enabled = !running,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (micPermission.hasPermission) "Start" else "Grant Mic")
            }

            Button(
                onClick = { engine.stop() },
                enabled = running,
                modifier = Modifier.weight(1f),
            ) {
                Text("Stop")
            }
        }

        StatusCard(
            stateLabel = stateLabel,
            currentDb = currentDb,
            elapsedMs = elapsedMs,
            silenceHint = silenceHint,
        )

        if (error != null) {
            Text(text = "Error: $error", color = MaterialTheme.colors.error)
        }

        result?.let { ResultCard(it) }
    }
}

@Composable
private fun StatusCard(
    stateLabel: String,
    currentDb: Int,
    elapsedMs: Long,
    silenceHint: SilenceLevel?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "State: $stateLabel", style = MaterialTheme.typography.subtitle2)
                if (stateLabel == "Recording") {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(modifier = Modifier.height(14.dp).width(14.dp), strokeWidth = 2.dp)
                }
            }
            Text(text = "Elapsed: ${elapsedMs}ms", style = MaterialTheme.typography.caption)
            Text(text = "Current dB: $currentDb", style = MaterialTheme.typography.caption)
            // VU meter — quick & dirty, normalize 30..90 dB into 0..1.
            val normalized = ((currentDb - 30).coerceAtLeast(0) / 60f).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = normalized,
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
            silenceHint?.let {
                Text(
                    text = when (it) {
                        SilenceLevel.WARNING -> "💬 Speak louder!"
                        SilenceLevel.AUTO_STOP -> "🛑 Auto-stopped: too quiet for too long."
                    },
                    color = MaterialTheme.colors.secondary,
                    style = MaterialTheme.typography.body2,
                )
            }
        }
    }
}

@Composable
private fun ResultCard(result: EvalResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Score: ", style = MaterialTheme.typography.subtitle1)
                Text(
                    text = "${result.overallScore}",
                    style = MaterialTheme.typography.h5,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor(result.overallScore),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = sourceLabel(result.source),
                    style = MaterialTheme.typography.caption,
                    color = if (result.source == ResultSource.SCORER) {
                        MaterialTheme.colors.primary
                    } else {
                        MaterialTheme.colors.error
                    },
                )
            }
            Text("Duration: ${result.durationMs}ms", style = MaterialTheme.typography.caption)
            Divider()
            Text("Words", style = MaterialTheme.typography.subtitle2)
            result.words.forEach { WordRow(it) }
            Divider()
            Text("Local: ${result.localPath}", style = MaterialTheme.typography.caption)
            result.uploadedUrl?.let {
                Text("Uploaded: $it", style = MaterialTheme.typography.caption)
            }
        }
    }
}

@Composable
private fun WordRow(word: WordScore) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(word.word, style = MaterialTheme.typography.body2)
        Text(
            text = "${word.score}",
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.Bold,
            color = scoreColor(word.score),
        )
    }
}

private fun scoreColor(score: Int): Color = when {
    score >= 85 -> Color(0xFF2E7D32)
    score >= 70 -> Color(0xFF1565C0)
    score >= 60 -> Color(0xFFEF6C00)
    else -> Color(0xFFC62828)
}

private fun sourceLabel(source: ResultSource): String = when (source) {
    ResultSource.SCORER -> "(real)"
    ResultSource.TIMEOUT_FALLBACK -> "(timeout fallback)"
    ResultSource.DEFAULT_FALLBACK -> "(error fallback)"
}

