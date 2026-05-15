package com.iwatchme.android.demo.renderengine

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import android.content.Context
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

private data class SubtitleAsset(val label: String, val assetPath: String)
private data class FontAsset(val label: String, val assetPath: String)

// 字幕/字体资产清单。这里只列出 assets 下已捆绑的文件，
// 字体文件需提前放入 app/src/main/assets/fonts/ 才会显示。
private val BUNDLED_SUBTITLES = listOf(
    SubtitleAsset("无字幕", ""),
    SubtitleAsset("中文示例", "subtitles/sample_zh.srt"),
    SubtitleAsset("英文示例", "subtitles/sample_en.srt"),
    SubtitleAsset("中英混排", "subtitles/sample_mixed.srt"),
)

private val BUNDLED_FONTS = listOf(
    FontAsset("Noto Sans SC (Regular)", "fonts/NotoSansSC-Regular.ttf"),
    FontAsset("Noto Sans SC (Bold)", "fonts/NotoSansSC-Bold.ttf"),
    FontAsset("Noto Serif SC", "fonts/NotoSerifSC-Regular.ttf"),
    FontAsset("Roboto (Latin)", "fonts/Roboto-Regular.ttf"),
)

private fun copyAssetToCache(ctx: Context, assetPath: String): String? {
    if (assetPath.isEmpty()) return null
    val name = assetPath.substringAfterLast('/')
    val outFile = File(ctx.cacheDir, name)
    if (outFile.exists() && outFile.length() > 0) return outFile.absolutePath
    return try {
        ctx.assets.open(assetPath).use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
        outFile.absolutePath
    } catch (e: Exception) {
        null
    }
}

private fun assetExists(ctx: Context, assetPath: String): Boolean {
    return try {
        ctx.assets.open(assetPath).use { true }
    } catch (_: Exception) {
        false
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

    // 字幕状态
    val availableSubtitles = remember { BUNDLED_SUBTITLES }
    val availableFonts = remember { BUNDLED_FONTS.filter { assetExists(context, it.assetPath) } }
    var selectedSubtitleIdx by remember { mutableStateOf(0) }
    var selectedFontIdx by remember { mutableStateOf(0) }
    var subtitleEnabled by remember { mutableStateOf(false) }
    var subtitleFontSize by remember { mutableStateOf(48) }

    fun applySubtitleConfig() {
        val engine = engineView?.engine ?: return
        val sub = availableSubtitles.getOrNull(selectedSubtitleIdx)
        val font = availableFonts.getOrNull(selectedFontIdx)
        if (sub == null || sub.assetPath.isEmpty() || font == null) {
            engine.setSubtitleEnabled(false)
            return
        }
        val srtPath = copyAssetToCache(context, sub.assetPath)
        val fontPath = copyAssetToCache(context, font.assetPath)
        if (srtPath != null && fontPath != null) {
            engine.setSubtitle(srtPath, fontPath, subtitleFontSize)
            engine.setSubtitleEnabled(subtitleEnabled)
        } else {
            statusText = "Failed to stage subtitle/font assets"
        }
    }

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

                Spacer(modifier = Modifier.height(12.dp))

                // ---- 字幕区 ----
                Text("字幕", color = Color(0xFF80CBC4), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(availableSubtitles) { index, item ->
                        val selected = index == selectedSubtitleIdx
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) Color(0xFF00897B) else Color(0xFF333333))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .clickable {
                                    selectedSubtitleIdx = index
                                    subtitleEnabled = item.assetPath.isNotEmpty()
                                    applySubtitleConfig()
                                }
                        ) {
                            Text(item.label, color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (availableFonts.isEmpty()) {
                    Text(
                        "字体未捆绑 — 请把 .ttf 放进 app/src/main/assets/fonts/",
                        color = Color(0xFFEF9A9A),
                        fontSize = 11.sp
                    )
                } else {
                    Text("字体", color = Color(0xFF80CBC4), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(availableFonts) { index, item ->
                            val selected = index == selectedFontIdx
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) Color(0xFF00897B) else Color(0xFF333333))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .clickable {
                                        selectedFontIdx = index
                                        applySubtitleConfig()
                                    }
                            ) {
                                Text(item.label, color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "字号: ${subtitleFontSize}px",
                    color = Color(0xFF80CBC4),
                    fontSize = 11.sp
                )
                Slider(
                    value = subtitleFontSize.toFloat(),
                    onValueChange = { subtitleFontSize = it.toInt().coerceIn(16, 96) },
                    onValueChangeFinished = { applySubtitleConfig() },
                    valueRange = 16f..96f,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            subtitleEnabled = !subtitleEnabled
                            engineView?.engine?.setSubtitleEnabled(subtitleEnabled)
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (subtitleEnabled) Color(0xFF00897B) else Color(0xFF555555)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (subtitleEnabled) "Sub ON" else "Sub OFF", color = Color.White)
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
