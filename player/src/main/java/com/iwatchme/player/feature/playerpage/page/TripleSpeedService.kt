package com.iwatchme.player.feature.playerpage.page

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import com.iwatchme.player.core.di.PageCoroutineScope
import com.iwatchme.player.core.di.PageScope
import com.iwatchme.player.core.player.ExoPlayerHolder
import com.iwatchme.player.feature.playerpage.uicomponent.PlayerGestureOverlayUIComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import javax.inject.Inject

@PageScope
class TripleSpeedService @Inject constructor(
    @PageCoroutineScope private val coroutineScope: CoroutineScope,
    private val context: Context,
    private val exoPlayerHolder: ExoPlayerHolder,
    private val gestureService: PlayerGestureService,
) {
    init {
        coroutineScope.launch {
            val listener = object : PlayerGestureDetector.OnLongPressListener {
                override fun onLongPress(event: MotionEvent?): Boolean {
                    val player = exoPlayerHolder.player
                    if (!player.playWhenReady || !player.isPlaying) return false
                    player.setPlaybackSpeed(LONG_PRESS_SPEED)
                    gestureService.emitOverlayState(
                        PlayerGestureOverlayUIComponent.State.TripleSpeed(LONG_PRESS_SPEED),
                    )
                    vibrate()
                    return true
                }

                override fun onLongPressEnd(event: MotionEvent?) {
                    exoPlayerHolder.player.setPlaybackSpeed(1f)
                    gestureService.emitOverlayState(PlayerGestureOverlayUIComponent.State.Hidden)
                }
            }
            gestureService.addOnLongPressListener(listener)
            try {
                awaitCancellation()
            } finally {
                gestureService.removeOnLongPressListener(listener)
                // PageScope 取消时若仍在长按，恢复 1x 防 speed 遗留
                runCatching { exoPlayerHolder.player.setPlaybackSpeed(1f) }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(30L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(30L)
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val LONG_PRESS_SPEED = 3f
    }
}
