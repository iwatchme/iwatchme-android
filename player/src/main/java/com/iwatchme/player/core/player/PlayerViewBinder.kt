package com.iwatchme.player.core.player

import androidx.media3.ui.PlayerView

interface PlayerViewBinder {
    fun bind(playerView: PlayerView)
    fun unbind(playerView: PlayerView)
}
