package com.iwatchme.player.feature.playerpage.media

import android.util.Log
import com.iwatchme.player.core.di.MediaScope
import javax.inject.Inject

@MediaScope
class MediaScopeAnchor @Inject constructor(
    private val videoPlaybackRepository: VideoPlaybackRepository,
    private val mediaPrepareService: MediaPrepareService,
    private val playbackStatusService: PlaybackStatusService,
) {
    fun start() {
        Log.d("Player", "[MediaScopeAnchor] start() — MediaScope services initialized (Playback + Prepare + Status)")
    }
}
