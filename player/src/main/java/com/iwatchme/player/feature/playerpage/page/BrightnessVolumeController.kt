package com.iwatchme.player.feature.playerpage.page

import android.app.Activity
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager

class BrightnessVolumeController(
    private val activity: Activity,
    private val audioManager: AudioManager,
    private val stateChange: (State) -> Unit,
) {

    enum class Type { VOLUME, BRIGHTNESS }

    sealed interface State {
        object Hidden : State
        data class Volume(val percent: Int) : State
        data class Brightness(val percent: Int) : State
    }

    private val maxVolume: Int = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        .coerceAtLeast(1)

    private var baselineVolume: Int = 0
    private var baselineBrightness: Float = 0f

    fun onScrollStart(type: Type) {
        when (type) {
            Type.VOLUME -> {
                baselineVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                stateChange(State.Volume(percent = baselineVolume * 100 / maxVolume))
            }
            Type.BRIGHTNESS -> {
                baselineBrightness = readCurrentBrightness()
                stateChange(State.Brightness(percent = (baselineBrightness * 100).toInt()))
            }
        }
    }

    fun onScroll(type: Type, dy: Float, surfaceHeight: Int) {
        if (surfaceHeight <= 0) return
        // 上滑 dy<0 = 值增大；坐标方向与调节方向相反，所以取负
        val delta = -dy / surfaceHeight
        when (type) {
            Type.VOLUME -> {
                val newPercent = ((baselineVolume.toFloat() / maxVolume) + delta)
                    .coerceIn(0f, 1f)
                val newVolume = (newPercent * maxVolume).toInt().coerceIn(0, maxVolume)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                stateChange(State.Volume(percent = (newPercent * 100).toInt()))
            }
            Type.BRIGHTNESS -> {
                val newBrightness = (baselineBrightness + delta).coerceIn(0f, 1f)
                applyBrightness(newBrightness)
                stateChange(State.Brightness(percent = (newBrightness * 100).toInt()))
            }
        }
    }

    fun onScrollStop() {
        stateChange(State.Hidden)
    }

    fun onCancel() {
        stateChange(State.Hidden)
    }

    private fun readCurrentBrightness(): Float {
        // window 属性 -1f 表示跟随系统；首次进入时回落到系统设置（0..255 整数）
        val current = activity.window.attributes.screenBrightness
        if (current in 0f..1f) return current
        return try {
            Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (e: Settings.SettingNotFoundException) {
            0.5f
        }
    }

    private fun applyBrightness(value: Float) {
        val lp: WindowManager.LayoutParams = activity.window.attributes
        lp.screenBrightness = value
        activity.window.attributes = lp
    }
}
