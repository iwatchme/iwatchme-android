package com.iwatchme.jetpackstarter.demo.renderengine

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.iwatchme.renderengine.RenderEngineView
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
    var videoLoaded by remember { mutableStateOf(false) }
    var versionText by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("") }
    val context = LocalContext.current

    // File picker: copy selected video to cache dir, then load
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        statusText = "Copying video..."
        try {
            val cacheFile = File(context.cacheDir, "input_video.mp4")
            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            engineView?.engine?.let { engine ->
                engine.setVideoSource(cacheFile.absolutePath)
                videoLoaded = true
                statusText = ""
            }
        } catch (e: Exception) {
            statusText = "Error: ${e.message}"
            Toast.makeText(context, "Failed to load video: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Set up EOF callback
    LaunchedEffect(engineView) {
        engineView?.engine?.onPlaybackCompleted = {
            isPlaying = false
        }
    }

    // Poll position and duration while video is loaded
    LaunchedEffect(videoLoaded, isPlaying) {
        while (videoLoaded) {
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

            if (!videoLoaded) {
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
            if (!videoLoaded) {
                Button(
                    onClick = { filePickerLauncher.launch("video/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Select Video")
                }
            }

            if (videoLoaded) {
                // Time display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(currentPositionMs), color = Color.White, fontSize = 12.sp)
                    Text(formatTime(durationMs), color = Color.White, fontSize = 12.sp)
                }

                // Seek bar: 拖动中用快速 seek（关键帧预览），松手后精确定位
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

                // Play/Pause button
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
