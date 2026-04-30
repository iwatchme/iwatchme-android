package com.iwatchme.player.core.player

import android.content.Context
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer

class ExoPlayerHolderImpl(
    private val context: Context,
) : ExoPlayerHolder {

    private var _player: ExoPlayer? = null

    override val player: ExoPlayer get() = ensurePlayer()

    override fun ensurePlayer(): ExoPlayer {
        return _player ?: ExoPlayer.Builder(context).build().also {
            _player = it
            Log.d("Player", "[ExoPlayerHolder] ExoPlayer created")
        }
    }

    override fun release() {
        _player?.release()
        _player = null
        Log.d("Player", "[ExoPlayerHolder] ExoPlayer released")
    }
}
