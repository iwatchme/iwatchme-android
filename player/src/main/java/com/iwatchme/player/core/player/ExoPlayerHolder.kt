package com.iwatchme.player.core.player

import androidx.media3.exoplayer.ExoPlayer

interface ExoPlayerHolder {
    val player: ExoPlayer
    fun ensurePlayer(): ExoPlayer
    fun release()
}
