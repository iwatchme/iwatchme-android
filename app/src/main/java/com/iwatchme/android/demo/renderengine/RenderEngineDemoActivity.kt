package com.iwatchme.android.demo.renderengine

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.iwatchme.renderengine.RenderEngineView
import com.iwatchme.renderengine.VideoClip
import com.iwatchme.renderengine.VideoTrack
import kotlinx.coroutines.delay
import java.io.File

class RenderEngineDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                RenderEngineDemoScreen()
            }
        }
    }
}

@Composable
fun RenderEngineDemoScreen() {
    var engineView by remember { mutableStateOf<RenderEngineView?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var timelineStarted by remember { mutableStateOf(false) }
    var versionText by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("") }
    val context = LocalContext.current

    // 主轨道片段列表（文件路径）
    val clips = remember { mutableStateListOf<String>() }
    // 叠加轨道片段列表
    val overlayClips = remember { mutableStateListOf<String>() }
    var overlayAlpha by remember { mutableStateOf(0.5f) }

    // File picker: copy selected video to cache dir, add to clip list
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        statusText = "Copying video..."
        try {
            val index = clips.size
            val cacheFile = File(context.cacheDir, "clip_$index.mp4")
            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            clips.add(cacheFile.absolutePath)
            statusText = ""
        } catch (e: Exception) {
            statusText = "Error: ${e.message}"
            Toast.makeText(context, "Failed to load video: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Overlay file picker
    val overlayPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        statusText = "Copying overlay video..."
        try {
            val index = overlayClips.size
            val cacheFile = File(context.cacheDir, "overlay_$index.mp4")
            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            overlayClips.add(cacheFile.absolutePath)
            statusText = ""
        } catch (e: Exception) {
            statusText = "Error: ${e.message}"
            Toast.makeText(context, "Failed to load overlay: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Set up EOF callback
    LaunchedEffect(engineView) {
        engineView?.engine?.onPlaybackCompleted = {
            isPlaying = false
        }
    }

    // Poll position and duration while timeline is active
    LaunchedEffect(timelineStarted, isPlaying) {
        while (timelineStarted) {
            engineView?.engine?.let {
                val dur = it.getDuration() / 1000
                if (dur > 0) durationMs = dur
                if (isPlaying) {
                    currentPositionMs = it.getPosition() / 1000
                }
            }
            delay(200)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            engineView?.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video view
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    RenderEngineView(ctx).also { view ->
                        engineView = view
                        versionText = view.engine.version()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (!timelineStarted) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = versionText.ifEmpty { "RenderEngine" },
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    if (statusText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = statusText, color = Color.Yellow, fontSize = 12.sp)
                    }
                }
            }
        }

        // Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A))
                .padding(16.dp)
        ) {
            // Clip list - Primary
            if (clips.isNotEmpty()) {
                Text("Primary Track (${clips.size}):", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(clips) { index, path ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF333333))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "#${index + 1} ${File(path).name}",
                                color = Color.White,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Clip list - Overlay
            if (overlayClips.isNotEmpty()) {
                Text("Overlay Track (${overlayClips.size}):", color = Color(0xFFFF9800), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(overlayClips) { index, path ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF4A3000))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "OV#${index + 1} ${File(path).name}",
                                color = Color(0xFFFF9800),
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

                // Alpha slider
                Text(
                    "Overlay Alpha: %.0f%%".format(overlayAlpha * 100),
                    color = Color(0xFFFF9800),
                    fontSize = 11.sp
                )
                Slider(
                    value = overlayAlpha,
                    onValueChange = {
                        overlayAlpha = it
                        engineView?.engine?.setOverlayAlpha(it)
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (clips.isNotEmpty() || overlayClips.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (!timelineStarted) {
                // Add clip + overlay + start buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { filePickerLauncher.launch("video/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ Clip")
                    }
                    Button(
                        onClick = { overlayPickerLauncher.launch("video/*") },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFFFF9800)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ Overlay", color = Color.White)
                    }
                }
                if (clips.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            engineView?.engine?.let { engine ->
                                val tracks = mutableListOf<VideoTrack>()
                                // Primary track
                                tracks.add(VideoTrack(clips.map { VideoClip(it) }))
                                // Overlay track (if any)
                                if (overlayClips.isNotEmpty()) {
                                    tracks.add(VideoTrack(
                                        overlayClips.map { VideoClip(it) },
                                        overlayAlpha
                                    ))
                                }
                                if (engine.setMultiTrackTimeline(tracks)) {
                                    timelineStarted = true
                                    statusText = ""
                                } else {
                                    statusText = "Failed to set timeline"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF4CAF50)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val label = buildString {
                            append("Play ${clips.size} Clip${if (clips.size > 1) "s" else ""}")
                            if (overlayClips.isNotEmpty()) {
                                append(" + ${overlayClips.size} Overlay")
                            }
                        }
                        Text(label, color = Color.White)
                    }
                }
            }

            if (timelineStarted) {
                // Time display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(currentPositionMs), color = Color.White, fontSize = 12.sp)
                    Text(formatTime(durationMs), color = Color.White, fontSize = 12.sp)
                }

                // Seek bar
                Slider(
                    value = if (durationMs > 0) currentPositionMs.toFloat() / durationMs.toFloat() else 0f,
                    onValueChange = { fraction ->
                        currentPositionMs = (fraction * durationMs).toLong()
                        engineView?.engine?.seekFast(currentPositionMs * 1000)
                    },
                    onValueChangeFinished = {
                        engineView?.engine?.seekTo(currentPositionMs * 1000)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Play/Pause
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(onClick = {
                        engineView?.engine?.let { engine ->
                            if (isPlaying) {
                                engine.pause()
                            } else {
                                engine.play()
                            }
                            isPlaying = !isPlaying
                        }
                    }) {
                        Text(
                            text = if (isPlaying) "Pause" else "Play",
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
