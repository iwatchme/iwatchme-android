package com.iwatchme.renderengine

import android.os.Handler
import android.os.Looper
import android.view.Surface

data class VideoClip(
    val sourcePath: String,
    val trimInUs: Long = 0,
    val trimOutUs: Long = -1  // -1 = 使用完整时长
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
        if (nativeHandle == 0L || clips.isEmpty()) return false
        val paths = clips.map { it.sourcePath }.toTypedArray()
        val trimIns = clips.map { it.trimInUs }.toLongArray()
        val trimOuts = clips.map { it.trimOutUs }.toLongArray()
        return nativeSetTimeline(nativeHandle, paths, trimIns, trimOuts)
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
    private external fun nativePlay(handle: Long)
    private external fun nativePause(handle: Long)
    private external fun nativeSeek(handle: Long, positionUs: Long)
    private external fun nativeSeekFast(handle: Long, positionUs: Long)
    private external fun nativeGetDuration(handle: Long): Long
    private external fun nativeGetPosition(handle: Long): Long
    private external fun nativeGetVideoWidth(handle: Long): Int
    private external fun nativeGetVideoHeight(handle: Long): Int
}
