package com.iwatchme.player.feature.playerpage.page

import androidx.media3.common.C
import androidx.media3.common.Player
import com.iwatchme.player.core.di.PageCoroutineScope
import com.iwatchme.player.core.di.PageScope
import com.iwatchme.player.core.player.ExoPlayerHolder
import com.iwatchme.player.feature.playerpage.uicomponent.PlaybackProgressUIComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@PageScope
class PlaybackProgressService @Inject constructor(
    @PageCoroutineScope private val scope: CoroutineScope,
    private val exoPlayerHolder: ExoPlayerHolder,
) {

    private val _stateFlow = MutableStateFlow(EMPTY_STATE)

    val viewModel: PlaybackProgressUIComponent.ViewModel = object : PlaybackProgressUIComponent.ViewModel {
        override val state: StateFlow<PlaybackProgressUIComponent.State> = _stateFlow
        override fun onSeekStart() {
            isUserSeeking = true
        }
        override fun onSeekStop(positionMs: Long) {
            exoPlayerHolder.player.seekTo(positionMs)
            isUserSeeking = false
            // commit 后立即推一次，避免 500ms 轮询窗口里 SeekBar 显示陈旧值
            pushFromPlayer()
        }
    }

    // 拖动期间屏蔽轮询写入，避免 SeekBar 被 player 当前位置抢回
    @Volatile
    private var isUserSeeking: Boolean = false

    init {
        scope.launch {
            val player = exoPlayerHolder.player
            val listener = object : Player.Listener {
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    pushFromPlayer()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    pushFromPlayer()
                }
            }
            player.addListener(listener)
            try {
                while (isActive) {
                    if (!isUserSeeking) pushFromPlayer()
                    delay(POLL_INTERVAL_MS)
                }
            } finally {
                player.removeListener(listener)
            }
        }
    }

    private fun pushFromPlayer() {
        val player = exoPlayerHolder.player
        val duration = player.duration.let { if (it == C.TIME_UNSET) 0L else it }
        val position = player.currentPosition.coerceAtLeast(0L)
        val buffered = player.bufferedPosition.coerceAtLeast(0L)
        _stateFlow.value = PlaybackProgressUIComponent.State(
            positionMs = position,
            bufferedMs = buffered,
            durationMs = duration,
            visible = duration > 0L,
        )
    }

    companion object {
        private const val POLL_INTERVAL_MS = 500L
        private val EMPTY_STATE = PlaybackProgressUIComponent.State(
            positionMs = 0L,
            bufferedMs = 0L,
            durationMs = 0L,
            visible = false,
        )
    }
}
