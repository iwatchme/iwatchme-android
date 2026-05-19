package com.iwatchme.android.demo.renderengine

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.iwatchme.renderengine.RenderEngineView
import com.iwatchme.renderengine.SubtitleEntry
import com.iwatchme.renderengine.VideoClip
import com.iwatchme.renderengine.VideoTrack
import kotlinx.coroutines.delay
import java.io.File

private const val PX_PER_SEC = 80f
private const val TRACK_ICON_WIDTH_DP = 44
private const val TRACK_ROW_HEIGHT_DP = 56
private val VIDEO_TRACK_COLOR = Color(0xFF455A64)
private val OVERLAY_TRACK_COLOR = Color(0xFFFF9800)
private val SUBTITLE_TRACK_COLOR = Color(0xFFE9A35A)

private data class EditorClip(
    val sourcePath: String,
    val durationUs: Long,
)

@Composable
fun RenderEngineEditorScreen() {
    val context = LocalContext.current

    var engineView by remember { mutableStateOf<RenderEngineView?>(null) }
    var timelineStarted by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionUs by remember { mutableStateOf(0L) }
    var durationUs by remember { mutableStateOf(0L) }

    val primaryClips = remember { mutableStateListOf<String>() }
    val overlayClips = remember { mutableStateListOf<String>() }
    val overlayAlpha by remember { mutableStateOf(0.5f) }

    var selectedSubtitleIdx by remember { mutableStateOf(0) }
    var selectedFontIdx by remember { mutableStateOf(0) }
    var subtitleFontSize by remember { mutableStateOf(48) }
    var subtitleEnabled by remember { mutableStateOf(false) }
    var subtitleEntries by remember { mutableStateOf(emptyList<SubtitleEntry>()) }
    var showSubtitleSheet by remember { mutableStateOf(false) }

    val availableSubtitles = remember { BUNDLED_SUBTITLES }
    val availableFonts = remember { BUNDLED_FONTS.filter { assetExistsForEditor(context, it.assetPath) } }

    val primaryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        copyVideoToCache(context, uri, "clip_${primaryClips.size}.mp4")?.let { primaryClips.add(it) }
    }
    val overlayPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        copyVideoToCache(context, uri, "overlay_${overlayClips.size}.mp4")?.let { overlayClips.add(it) }
    }

    fun applySubtitleConfig() {
        val engine = engineView?.engine ?: return
        val sub = availableSubtitles.getOrNull(selectedSubtitleIdx)
        val font = availableFonts.getOrNull(selectedFontIdx)
        if (sub == null || sub.assetPath.isEmpty() || font == null) {
            engine.setSubtitleEnabled(false)
            subtitleEntries = emptyList()
            return
        }
        val srtPath = copyAssetForEditor(context, sub.assetPath)
        val fontPath = copyAssetForEditor(context, font.assetPath)
        if (srtPath != null && fontPath != null) {
            engine.setSubtitle(srtPath, fontPath, subtitleFontSize)
            engine.setSubtitleEnabled(subtitleEnabled)
            subtitleEntries = engine.getSubtitleEntries()
        }
    }

    LaunchedEffect(engineView) {
        engineView?.engine?.onPlaybackCompleted = { isPlaying = false }
    }

    LaunchedEffect(timelineStarted) {
        while (timelineStarted) {
            engineView?.engine?.let {
                val dur = it.getDuration()
                if (dur > 0) durationUs = dur
                currentPositionUs = it.getPosition()
            }
            delay(33)
        }
    }

    DisposableEffect(Unit) {
        onDispose { engineView?.release() }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF111111))) {

        // 预览
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { ctx -> RenderEngineView(ctx).also { engineView = it } },
                modifier = Modifier.fillMaxSize(),
            )
            if (!timelineStarted) {
                Text(
                    text = "点击 ▶ 加载视频 / 通过下面 + 按钮添加片段",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp,
                )
            }
        }

        // 时间 + 播放按钮
        TransportBar(
            currentPositionUs = currentPositionUs,
            durationUs = durationUs,
            isPlaying = isPlaying,
            timelineStarted = timelineStarted,
            onPlayPause = {
                val engine = engineView?.engine ?: return@TransportBar
                if (timelineStarted) {
                    if (isPlaying) engine.pause() else engine.play()
                    isPlaying = !isPlaying
                } else if (primaryClips.isNotEmpty()) {
                    val tracks = mutableListOf(VideoTrack(primaryClips.map { VideoClip(it) }))
                    if (overlayClips.isNotEmpty()) {
                        tracks.add(VideoTrack(overlayClips.map { VideoClip(it) }, overlayAlpha))
                    }
                    if (engine.setMultiTrackTimeline(tracks)) {
                        timelineStarted = true
                        engine.play()
                        isPlaying = true
                    } else {
                        Toast.makeText(context, "setTimeline 失败", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "请先添加视频片段", Toast.LENGTH_SHORT).show()
                }
            },
        )

        // 轨道区
        TimelineArea(
            currentPositionUs = currentPositionUs,
            durationUs = durationUs,
            primaryClips = primaryClips,
            overlayClips = overlayClips,
            subtitleEntries = subtitleEntries,
            onAddPrimary = { primaryPicker.launch("video/*") },
            onAddOverlay = { overlayPicker.launch("video/*") },
            onAddSubtitle = { showSubtitleSheet = true },
            onClipSeek = { posUs ->
                engineView?.engine?.seekTo(posUs.coerceAtLeast(0L))
                currentPositionUs = posUs
            },
        )

        // 底栏：字幕入口
        BottomToolbar(onSubtitleEntry = { showSubtitleSheet = true })
    }

    if (showSubtitleSheet) {
        SubtitlePanel(
            availableSubtitles = availableSubtitles,
            availableFonts = availableFonts,
            selectedSubtitleIdx = selectedSubtitleIdx,
            selectedFontIdx = selectedFontIdx,
            fontSizePx = subtitleFontSize,
            enabled = subtitleEnabled,
            onSubtitleSelect = {
                selectedSubtitleIdx = it
                subtitleEnabled = availableSubtitles[it].assetPath.isNotEmpty()
                applySubtitleConfig()
            },
            onFontSelect = {
                selectedFontIdx = it
                applySubtitleConfig()
            },
            onFontSizeChange = {
                subtitleFontSize = it
                applySubtitleConfig()
            },
            onEnabledChange = {
                subtitleEnabled = it
                engineView?.engine?.setSubtitleEnabled(it)
            },
            onDismiss = { showSubtitleSheet = false },
        )
    }
}

@Composable
private fun TransportBar(
    currentPositionUs: Long,
    durationUs: Long,
    isPlaying: Boolean,
    timelineStarted: Boolean,
    onPlayPause: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111111))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${formatTimeUs(currentPositionUs)} / ${formatTimeUs(durationUs)}",
            color = Color.White,
            fontSize = 12.sp,
        )
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (timelineStarted) Color(0xFF1A1A1A) else Color(0xFF2D6A4F))
                .clickable { onPlayPause() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isPlaying) "❚❚" else "▶",
                color = Color.White,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun TimelineArea(
    currentPositionUs: Long,
    durationUs: Long,
    primaryClips: List<String>,
    overlayClips: List<String>,
    subtitleEntries: List<SubtitleEntry>,
    onAddPrimary: () -> Unit,
    onAddOverlay: () -> Unit,
    onAddSubtitle: () -> Unit,
    onClipSeek: (Long) -> Unit,
) {
    val density = LocalDensity.current
    val scroll = rememberScrollState()

    val primaryCount = primaryClips.size.coerceAtLeast(1)
    val perPrimaryDurationUs = if (durationUs > 0) durationUs / primaryCount else 5_000_000L
    val primaryItems = primaryClips.map { EditorClip(it, perPrimaryDurationUs) }
    val overlayItems = overlayClips.map { EditorClip(it, perPrimaryDurationUs) }

    val totalUs = maxOf(
        durationUs,
        primaryItems.sumOf { it.durationUs },
        subtitleEntries.lastOrNull()?.endUs ?: 0L,
    )

    // 时间线随播放自动滚动：让 playhead 大致停在屏幕左 1/3
    LaunchedEffect(currentPositionUs) {
        if (totalUs <= 0) return@LaunchedEffect
        val playheadPx = with(density) { usToDp(currentPositionUs).toPx() }.toInt()
        val target = (playheadPx - 200).coerceIn(0, scroll.maxValue)
        scroll.scrollTo(target)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .padding(vertical = 6.dp),
    ) {
        // 时间标尺（仅文字标签，跟随 scroll）
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(TRACK_ICON_WIDTH_DP.dp))
            Box(modifier = Modifier.fillMaxWidth().horizontalScroll(scroll)) {
                TimeRuler(totalUs = totalUs)
            }
        }

        TrackRow(
            iconText = "🎬",
            bgColor = VIDEO_TRACK_COLOR,
            clips = primaryItems,
            emptyLabel = "+ 添加视频",
            onAdd = onAddPrimary,
            onClipClick = { _, startUs -> onClipSeek(startUs) },
            scroll = scroll,
        )
        Spacer(modifier = Modifier.height(4.dp))
        TrackRow(
            iconText = "🖼",
            bgColor = OVERLAY_TRACK_COLOR,
            clips = overlayItems,
            emptyLabel = "+ 添加画中画",
            onAdd = onAddOverlay,
            onClipClick = { _, startUs -> onClipSeek(startUs) },
            scroll = scroll,
        )
        Spacer(modifier = Modifier.height(4.dp))
        SubtitleTrackRow(
            entries = subtitleEntries,
            onAdd = onAddSubtitle,
            onClipClick = { onClipSeek(it.startUs) },
            scroll = scroll,
        )
    }
}

@Composable
private fun TimeRuler(totalUs: Long) {
    val totalSec = (totalUs / 1_000_000).toInt().coerceAtLeast(10)
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (s in 0..totalSec step 2) {
            Box(
                modifier = Modifier
                    .width((PX_PER_SEC * 2).dp)
                    .height(18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = formatTimeUs(s * 1_000_000L),
                    color = Color(0xFF888888),
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun TrackRow(
    iconText: String,
    bgColor: Color,
    clips: List<EditorClip>,
    emptyLabel: String,
    onAdd: () -> Unit,
    onClipClick: (EditorClip, Long) -> Unit,
    scroll: androidx.compose.foundation.ScrollState,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TRACK_ROW_HEIGHT_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackIcon(iconText)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .horizontalScroll(scroll),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxHeight(),
            ) {
                if (clips.isEmpty()) {
                    AddButton(label = emptyLabel, onClick = onAdd)
                } else {
                    var cursorUs = 0L
                    clips.forEach { clip ->
                        val startUs = cursorUs
                        Box(
                            modifier = Modifier
                                .padding(end = 2.dp)
                                .height((TRACK_ROW_HEIGHT_DP - 12).dp)
                                .width(usToDp(clip.durationUs).coerceAtLeast(60.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .background(bgColor)
                                .clickable { onClipClick(clip, startUs) }
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = File(clip.sourcePath).name,
                                color = Color.White,
                                fontSize = 11.sp,
                                maxLines = 1,
                            )
                        }
                        cursorUs += clip.durationUs
                    }
                    AddButton(label = "+", onClick = onAdd)
                }
            }
        }
    }
}

@Composable
private fun SubtitleTrackRow(
    entries: List<SubtitleEntry>,
    onAdd: () -> Unit,
    onClipClick: (SubtitleEntry) -> Unit,
    scroll: androidx.compose.foundation.ScrollState,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TRACK_ROW_HEIGHT_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackIcon("T")
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .horizontalScroll(scroll),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxHeight(),
            ) {
                if (entries.isEmpty()) {
                    AddButton(label = "+ 添加字幕", onClick = onAdd)
                } else {
                    var cursorUs = 0L
                    entries.forEach { entry ->
                        if (entry.startUs > cursorUs) {
                            Spacer(modifier = Modifier.width(usToDp(entry.startUs - cursorUs)))
                        }
                        Box(
                            modifier = Modifier
                                .height((TRACK_ROW_HEIGHT_DP - 12).dp)
                                .width(usToDp(entry.endUs - entry.startUs).coerceAtLeast(40.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .background(SUBTITLE_TRACK_COLOR)
                                .clickable { onClipClick(entry) }
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = entry.text.replace('\n', ' '),
                                color = Color.White,
                                fontSize = 11.sp,
                                maxLines = 1,
                            )
                        }
                        cursorUs = entry.endUs
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    AddButton(label = "+", onClick = onAdd)
                }
            }
        }
    }
}

@Composable
private fun TrackIcon(text: String) {
    Box(
        modifier = Modifier
            .width(TRACK_ICON_WIDTH_DP.dp)
            .height((TRACK_ROW_HEIGHT_DP - 8).dp)
            .padding(end = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF222222)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun AddButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .height((TRACK_ROW_HEIGHT_DP - 16).dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF2A2A2A))
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color(0xFFCCCCCC), fontSize = 11.sp)
    }
}

@Composable
private fun BottomToolbar(onSubtitleEntry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable { onSubtitleEntry() }
                .padding(horizontal = 32.dp, vertical = 4.dp),
        ) {
            Text("T", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text("字幕", color = Color.White, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SubtitlePanel(
    availableSubtitles: List<SubtitleAssetItem>,
    availableFonts: List<FontAssetItem>,
    selectedSubtitleIdx: Int,
    selectedFontIdx: Int,
    fontSizePx: Int,
    enabled: Boolean,
    onSubtitleSelect: (Int) -> Unit,
    onFontSelect: (Int) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000))
            .clickable { onDismiss() },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A))
                .clickable(enabled = false) {}
                .padding(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("字幕设置", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "×",
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier.clickable { onDismiss() }.padding(8.dp),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text("字幕文件", color = Color(0xFF80CBC4), fontSize = 11.sp)
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(count = availableSubtitles.size) { idx ->
                    Chip(availableSubtitles[idx].label, idx == selectedSubtitleIdx) {
                        onSubtitleSelect(idx)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (availableFonts.isEmpty()) {
                Text("字体未捆绑", color = Color(0xFFEF9A9A), fontSize = 11.sp)
            } else {
                Text("字体", color = Color(0xFF80CBC4), fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(count = availableFonts.size) { idx ->
                        Chip(availableFonts[idx].label, idx == selectedFontIdx) {
                            onFontSelect(idx)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text("字号 ${fontSizePx}px", color = Color(0xFF80CBC4), fontSize = 11.sp)
            Slider(
                value = fontSizePx.toFloat(),
                onValueChange = { onFontSizeChange(it.toInt()) },
                valueRange = 16f..96f,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = { onEnabledChange(!enabled) },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (enabled) Color(0xFF00897B) else Color(0xFF555555),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (enabled) "字幕已开启" else "字幕已关闭", color = Color.White)
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Color(0xFF00897B) else Color(0xFF333333))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}

// ---------- assets ----------

data class SubtitleAssetItem(val label: String, val assetPath: String)
data class FontAssetItem(val label: String, val assetPath: String)

private val BUNDLED_SUBTITLES = listOf(
    SubtitleAssetItem("无字幕", ""),
    SubtitleAssetItem("中文示例", "subtitles/sample_zh.srt"),
    SubtitleAssetItem("英文示例", "subtitles/sample_en.srt"),
    SubtitleAssetItem("中英混排", "subtitles/sample_mixed.srt"),
)

private val BUNDLED_FONTS = listOf(
    FontAssetItem("Noto Sans SC", "fonts/NotoSansSC-Regular.ttf"),
    FontAssetItem("Noto Sans SC Bold", "fonts/NotoSansSC-Bold.ttf"),
    FontAssetItem("Noto Serif SC", "fonts/NotoSerifSC-Regular.ttf"),
    FontAssetItem("Roboto", "fonts/Roboto-Regular.ttf"),
)

private fun usToDp(us: Long): Dp =
    ((us / 1_000_000.0).toFloat() * PX_PER_SEC).dp

private fun formatTimeUs(us: Long): String {
    if (us < 0) return "00:00"
    val totalSec = us / 1_000_000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}

private fun copyAssetForEditor(ctx: Context, assetPath: String): String? {
    if (assetPath.isEmpty()) return null
    val name = assetPath.substringAfterLast('/')
    val out = File(ctx.cacheDir, name)
    if (out.exists() && out.length() > 0) return out.absolutePath
    return try {
        ctx.assets.open(assetPath).use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        out.absolutePath
    } catch (_: Exception) {
        null
    }
}

private fun assetExistsForEditor(ctx: Context, assetPath: String): Boolean = try {
    ctx.assets.open(assetPath).use { true }
} catch (_: Exception) { false }

private fun copyVideoToCache(ctx: Context, uri: Uri?, name: String): String? {
    if (uri == null) return null
    return try {
        val out = File(ctx.cacheDir, name)
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        out.absolutePath
    } catch (e: Exception) {
        Toast.makeText(ctx, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
        null
    }
}
