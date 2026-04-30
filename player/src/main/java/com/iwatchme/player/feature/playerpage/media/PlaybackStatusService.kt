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
                trySend(player.playbackState)
                awaitClose {
                    player.removeListener(listener)
                    Log.d("Player", "[PlaybackStatus] Listener removed from player")
                }
            }.collectLatest { state ->
                val mapped = when (state) {
                    Player.STATE_IDLE -> PlaybackState.IDLE
                    Player.STATE_BUFFERING -> PlaybackState.LOADING
                    Player.STATE_READY -> PlaybackState.READY
                    Player.STATE_ENDED -> PlaybackState.IDLE
                    else -> PlaybackState.IDLE
                }
                Log.d("Player", "[PlaybackStatus] ExoPlayer state=$state -> PlaybackState=$mapped")
                playerUiStateRepository.updatePlaybackState(mapped)
                playerUiStateRepository.updateError(null)
            }
        }
    }
}
