package com.iwatchme.player.feature.playerpage.page

import android.content.Context
import android.graphics.PointF
import android.media.AudioManager
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.media3.common.C
import com.iwatchme.player.core.di.PageCoroutineScope
import com.iwatchme.player.core.di.PageScope
import com.iwatchme.player.core.player.ExoPlayerHolder
import com.iwatchme.player.feature.playerpage.uicomponent.PlayerGestureOverlayUIComponent
import com.iwatchme.player.feature.playerpage.uicomponent.PlayerGestureSurfaceUIComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToLong

@PageScope
class PlayerGestureService @Inject constructor(
    @PageCoroutineScope private val coroutineScope: CoroutineScope,
    activity: ComponentActivity,
    context: Context,
    private val exoPlayerHolder: ExoPlayerHolder,
    audioManager: AudioManager,
) {

    private val _overlayStateFlow = MutableStateFlow<PlayerGestureOverlayUIComponent.State>(
        PlayerGestureOverlayUIComponent.State.Hidden,
    )

    val gestureOverlayViewModel: PlayerGestureOverlayUIComponent.ViewModel =
        object : PlayerGestureOverlayUIComponent.ViewModel {
            override val state: StateFlow<PlayerGestureOverlayUIComponent.State> = _overlayStateFlow
        }

    fun emitOverlayState(state: PlayerGestureOverlayUIComponent.State) {
        _overlayStateFlow.value = state
    }

    // 由 surface UIComponent 在每次 touch 回填，detector 的 widthProvider 读这块缓存
    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0

    private val detector = PlayerGestureDetector(
        context = context,
        widthProvider = { surfaceWidth },
    )

    private val brightnessVolume = BrightnessVolumeController(
        activity = activity,
        audioManager = audioManager,
        stateChange = { state ->
            _overlayStateFlow.value = when (state) {
                is BrightnessVolumeController.State.Hidden -> PlayerGestureOverlayUIComponent.State.Hidden
                is BrightnessVolumeController.State.Volume ->
                    PlayerGestureOverlayUIComponent.State.Volume(state.percent)
                is BrightnessVolumeController.State.Brightness ->
                    PlayerGestureOverlayUIComponent.State.Brightness(state.percent)
            }
        },
    )

    val gestureSurfaceUIComponent: PlayerGestureSurfaceUIComponent =
        PlayerGestureSurfaceUIComponent { event, w, h ->
            surfaceWidth = w
            surfaceHeight = h
            detector.dispatchTouchEvent(event)
        }

    private var seekStartPositionMs: Long = 0L
    private var seekDurationMs: Long = 0L
    private var seekTargetMs: Long = 0L

    private var hideOverlayJob: Job? = null

    fun addOnDoubleTapListener(
        l: PlayerGestureDetector.OnDoubleTapListener,
        priority: Int = PriorityGestureProcessor.PRIORITY_NORMAL,
    ) = detector.addOnDoubleTapListener(l, priority)

    fun removeOnDoubleTapListener(l: PlayerGestureDetector.OnDoubleTapListener) =
        detector.removeOnDoubleTapListener(l)

    fun addOnSingleTapListener(
        l: PlayerGestureDetector.OnSingleTapListener,
        priority: Int = PriorityGestureProcessor.PRIORITY_NORMAL,
    ) = detector.addOnSingleTapListener(l, priority)

    fun removeOnSingleTapListener(l: PlayerGestureDetector.OnSingleTapListener) =
        detector.removeOnSingleTapListener(l)

    fun addOnLongPressListener(
        l: PlayerGestureDetector.OnLongPressListener,
        priority: Int = PriorityGestureProcessor.PRIORITY_NORMAL,
    ) = detector.addOnLongPressListener(l, priority)

    fun removeOnLongPressListener(l: PlayerGestureDetector.OnLongPressListener) =
        detector.removeOnLongPressListener(l)

    fun setVerticalScrollListener(l: PlayerGestureDetector.OnVerticalScrollListener?) =
        detector.setVerticalScrollListener(l)

    fun restoreDefaultVerticalListener() = detector.setVerticalScrollListener(defaultVerticalListener)

    fun setHorizontalScrollListener(l: PlayerGestureDetector.OnHorizontalScrollListener?) =
        detector.setHorizontalScrollListener(l)

    fun restoreDefaultHorizontalListener() =
        detector.setHorizontalScrollListener(defaultHorizontalListener)

    private val defaultDoubleTapListener = object : PlayerGestureDetector.OnDoubleTapListener {
        override fun onDoubleTap(event: MotionEvent): Boolean {
            val player = exoPlayerHolder.player
            if (player.playWhenReady) player.pause() else player.play()
            return true
        }
    }

    private val defaultVerticalListener = object : PlayerGestureDetector.OnVerticalScrollListener {
        override fun onScrollStart(side: PlayerGestureDetector.Side, point: PointF) {
            cancelPendingHide()
            brightnessVolume.onScrollStart(sideToType(side))
        }

        override fun onScroll(side: PlayerGestureDetector.Side, totalDy: Float) {
            brightnessVolume.onScroll(sideToType(side), totalDy, surfaceHeight)
        }

        override fun onScrollStop(side: PlayerGestureDetector.Side) {
            brightnessVolume.onScrollStop()
            schedulePendingHide()
        }

        override fun onCancel() {
            brightnessVolume.onCancel()
        }
    }

    private val defaultHorizontalListener = object : PlayerGestureDetector.OnHorizontalScrollListener {
        override fun onScrollStart(point: PointF) {
            cancelPendingHide()
            val player = exoPlayerHolder.player
            seekStartPositionMs = player.currentPosition.takeIf { it >= 0 } ?: 0L
            seekDurationMs = player.duration.let { if (it == C.TIME_UNSET) 0L else it }
            seekTargetMs = seekStartPositionMs
            if (seekDurationMs <= 0L) return
            emitSeekingState()
        }

        override fun onScroll(totalDx: Float) {
            if (seekDurationMs <= 0L || surfaceWidth <= 0) return
            val deltaMs = (totalDx.toDouble() / surfaceWidth * seekDurationMs).roundToLong()
            seekTargetMs = (seekStartPositionMs + deltaMs).coerceIn(0L, seekDurationMs)
            emitSeekingState()
        }

        override fun onScrollStop(totalDx: Float) {
            if (seekDurationMs > 0L) {
                exoPlayerHolder.player.seekTo(seekTargetMs)
            }
            schedulePendingHide()
        }

        override fun onCancel() {
            _overlayStateFlow.value = PlayerGestureOverlayUIComponent.State.Hidden
        }
    }

    init {
        // 默认实现挂 LOWEST，业务在 NORMAL 注入即可覆盖
        detector.addOnDoubleTapListener(defaultDoubleTapListener, PriorityGestureProcessor.PRIORITY_LOWEST)
        detector.setVerticalScrollListener(defaultVerticalListener)
        detector.setHorizontalScrollListener(defaultHorizontalListener)
    }

    private fun emitSeekingState() {
        val deltaSec = ((seekTargetMs - seekStartPositionMs) / 1000.0).roundToLong().toInt()
        _overlayStateFlow.value = PlayerGestureOverlayUIComponent.State.Seeking(
            deltaSec = deltaSec,
            positionMs = seekTargetMs,
            durationMs = seekDurationMs,
        )
    }

    private fun sideToType(side: PlayerGestureDetector.Side): BrightnessVolumeController.Type =
        when (side) {
            PlayerGestureDetector.Side.LEFT -> BrightnessVolumeController.Type.BRIGHTNESS
            PlayerGestureDetector.Side.RIGHT -> BrightnessVolumeController.Type.VOLUME
        }

    private fun schedulePendingHide() {
        hideOverlayJob?.cancel()
        hideOverlayJob = coroutineScope.launch {
            delay(HIDE_DELAY_MS)
            _overlayStateFlow.value = PlayerGestureOverlayUIComponent.State.Hidden
        }
    }

    private fun cancelPendingHide() {
        hideOverlayJob?.cancel()
        hideOverlayJob = null
    }

    companion object {
        private const val HIDE_DELAY_MS = 600L
    }
}
