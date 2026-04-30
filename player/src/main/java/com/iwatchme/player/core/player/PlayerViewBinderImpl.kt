package com.iwatchme.player.core.player

import android.util.Log
import androidx.media3.ui.PlayerView

class PlayerViewBinderImpl(
    private val exoPlayerHolder: ExoPlayerHolder,
) : PlayerViewBinder {

    override fun bind(playerView: PlayerView) {
        playerView.player = exoPlayerHolder.ensurePlayer()
        Log.d("Player", "[PlayerViewBinder] PlayerView bound to ExoPlayer")
    }

    override fun unbind(playerView: PlayerView) {
        playerView.player = null
        Log.d("Player", "[PlayerViewBinder] PlayerView unbound from ExoPlayer")
    }
}
