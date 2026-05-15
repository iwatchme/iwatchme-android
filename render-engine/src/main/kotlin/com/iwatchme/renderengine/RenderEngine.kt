package com.iwatchme.renderengine

import android.os.Handler
import android.os.Looper
import android.view.Surface

data class VideoClip(
    val sourcePath: String,
    val trimInUs: Long = 0,
    val trimOutUs: Long = -1  // -1 = 使用完整时长
)

data class VideoTrack(
    val clips: List<VideoClip>,
    val alpha: Float = 1.0f
)

class RenderEngine {

    companion object {
        init {
            System.loadLibrary("render-engine")
        }
    }

    private var nativeHandle: Long = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    var onPlaybackCompleted: (() -> Unit)? = null

    fun create() {
        if (nativeHandle == 0L) {
            nativeHandle = nativeCreate()
        }
    }

    fun destroy() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0L
        }
    }

    fun setSurface(surface: Surface?) {
        if (nativeHandle != 0L) {
            nativeSetSurface(nativeHandle, surface)
        }
    }

    fun setVideoSource(filePath: String): Boolean {
        if (nativeHandle == 0L) return false
        return nativeSetVideoSource(nativeHandle, filePath)
    }

    /**
     * 设置多片段时间线，替代 setVideoSource 用于多片段拼接播放。
     */
    fun setTimeline(clips: List<VideoClip>): Boolean {
        return setMultiTrackTimeline(listOf(VideoTrack(clips, 1.0f)))
    }

    /**
     * 设置多轨道时间线：primaryTrack + overlay tracks。
     * tracks[0] = 主轨道, tracks[1] = 叠加轨道 (目前只支持 1 个叠加轨道)。
     */
    fun setMultiTrackTimeline(tracks: List<VideoTrack>): Boolean {
        if (nativeHandle == 0L || tracks.isEmpty()) return false
        val trackCount = tracks.size
        val clipCounts = tracks.map { it.clips.size }.toIntArray()
        val allPaths = tracks.flatMap { t -> t.clips.map { it.sourcePath } }.toTypedArray()
        val allTrimIns = tracks.flatMap { t -> t.clips.map { it.trimInUs } }.toLongArray()
        val allTrimOuts = tracks.flatMap { t -> t.clips.map { it.trimOutUs } }.toLongArray()
        val overlayAlpha = if (tracks.size > 1) tracks[1].alpha else 1.0f
        return nativeSetMultiTrackTimeline(nativeHandle, trackCount, clipCounts,
                                            allPaths, allTrimIns, allTrimOuts, overlayAlpha)
    }

    fun setOverlayAlpha(alpha: Float) {
        if (nativeHandle != 0L) nativeSetOverlayAlpha(nativeHandle, alpha)
    }

    fun setSubtitle(srtPath: String, fontPath: String, fontSizePx: Int = 48) {
        if (nativeHandle != 0L) nativeSetSubtitle(nativeHandle, srtPath, fontPath, fontSizePx)
    }

    fun setSubtitleEnabled(enabled: Boolean) {
        if (nativeHandle != 0L) nativeSetSubtitleEnabled(nativeHandle, enabled)
    }

    fun play() {
        if (nativeHandle != 0L) nativePlay(nativeHandle)
    }

    fun pause() {
        if (nativeHandle != 0L) nativePause(nativeHandle)
    }

    /** 精确 seek：解码到目标帧（松手时调用） */
    fun seekTo(positionUs: Long) {
        if (nativeHandle != 0L) nativeSeek(nativeHandle, positionUs)
    }

    /** 快速 seek：只显示最近关键帧（拖动中调用） */
    fun seekFast(positionUs: Long) {
        if (nativeHandle != 0L) nativeSeekFast(nativeHandle, positionUs)
    }

    fun getDuration(): Long {
        if (nativeHandle == 0L) return 0L
        return nativeGetDuration(nativeHandle)
    }

    fun getPosition(): Long {
        if (nativeHandle == 0L) return 0L
        return nativeGetPosition(nativeHandle)
    }

    fun getVideoWidth(): Int {
        if (nativeHandle == 0L) return 0
        return nativeGetVideoWidth(nativeHandle)
    }

    fun getVideoHeight(): Int {
        if (nativeHandle == 0L) return 0
        return nativeGetVideoHeight(nativeHandle)
    }

    fun version(): String = nativeVersion()

    // Called from native code (render thread) when playback reaches EOF
    @Suppress("unused")
    private fun onNativePlaybackCompleted() {
        mainHandler.post {
            onPlaybackCompleted?.invoke()
        }
    }

    // Native methods
    external fun nativeVersion(): String
    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeSetSurface(handle: Long, surface: Surface?)
    private external fun nativeSetVideoSource(handle: Long, filePath: String): Boolean
    private external fun nativeSetTimeline(handle: Long, paths: Array<String>, trimIns: LongArray, trimOuts: LongArray): Boolean
    private external fun nativeSetMultiTrackTimeline(handle: Long, trackCount: Int, clipCounts: IntArray, allPaths: Array<String>, allTrimIns: LongArray, allTrimOuts: LongArray, overlayAlpha: Float): Boolean
    private external fun nativeSetOverlayAlpha(handle: Long, alpha: Float)
    private external fun nativeSetSubtitle(handle: Long, srtPath: String, fontPath: String, fontSizePx: Int)
    private external fun nativeSetSubtitleEnabled(handle: Long, enabled: Boolean)
    private external fun nativePlay(handle: Long)
    private external fun nativePause(handle: Long)
    private external fun nativeSeek(handle: Long, positionUs: Long)
    private external fun nativeSeekFast(handle: Long, positionUs: Long)
    private external fun nativeGetDuration(handle: Long): Long
    private external fun nativeGetPosition(handle: Long): Long
    private external fun nativeGetVideoWidth(handle: Long): Int
    private external fun nativeGetVideoHeight(handle: Long): Int
}
