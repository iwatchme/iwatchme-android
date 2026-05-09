package com.iwatchme.player.feature.playerpage.media

import android.util.Log
import com.iwatchme.player.core.di.MediaCoroutineScope
import com.iwatchme.player.core.di.MediaScope
import com.iwatchme.player.core.player.ExoPlayerHolder
import com.iwatchme.player.feature.playerpage.page.PlayerUiStateRepository
import com.iwatchme.player.model.PlaybackState
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@MediaScope
class PlaybackStatusService @Inject constructor(
    @MediaCoroutineScope private val scope: CoroutineScope,
    private val exoPlayerHolder: ExoPlayerHolder,
    private val playerUiStateRepository: PlayerUiStateRepository,
) {
    init {
        scope.launch {
            val player = exoPlayerHolder.player
            callbackFlow {
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        trySend(playbackState)
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        playerUiStateRepository.updatePlaybackState(PlaybackState.ERROR)
                        playerUiStateRepository.updateError(error.message)
                        Log.e("Player", "[PlaybackStatus] Player error: ${error.message}")
                    }
                }
                player.addListener(listener)
                // 不主动读 player.playbackState：ExoPlayer 的 state 是热属性，会一直保留
                // 上一集结束时的 STATE_ENDED 直到 setMediaItem 重置。新 MediaScope 起来时
                // listener 一接上就 trySend 当前值，会把"上一集的 ENDED"当成新事件发出，
                // 触发 EpisodeCompletedService 误以为本集已经播完——跟 PlayerUiStateRepository
                // 改用 SharedFlow 是同一类问题（状态被新订阅者重放）。这里只对真实的
                // onPlaybackStateChanged 回调反应，由 setMediaItem/prepare 自然驱动 BUFFERING/READY。
                awaitClose {
                    player.removeListener(listener)
                    Log.d("Player", "[PlaybackStatus] Listener removed from player")
                }
            }.collectLatest { state ->
                val mapped = when (state) {
                    Player.STATE_IDLE -> PlaybackState.IDLE
                    Player.STATE_BUFFERING -> PlaybackState.LOADING
                    Player.STATE_READY -> PlaybackState.READY
                    Player.STATE_ENDED -> PlaybackState.COMPLETED
                    else -> PlaybackState.IDLE
                }
                Log.d("Player", "[PlaybackStatus] ExoPlayer state=$state -> PlaybackState=$mapped")
                playerUiStateRepository.updatePlaybackState(mapped)
                playerUiStateRepository.updateError(null)
                if (state == Player.STATE_ENDED) {
                    // 一次性事件，避免新 EpisodeScope 起来时被陈旧 state 触发，详见 PlayerUiStateRepository.completionEvents
                    playerUiStateRepository.notifyCompletion()
                }
            }
        }
    }
}
