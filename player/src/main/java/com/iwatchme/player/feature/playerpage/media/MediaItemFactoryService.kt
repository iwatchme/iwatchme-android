package com.iwatchme.player.feature.playerpage.media

import android.util.Log
import com.iwatchme.player.core.di.MediaScope
import com.iwatchme.player.model.PlaybackInfo
import androidx.media3.common.MediaItem
import javax.inject.Inject

@MediaScope
class MediaItemFactoryService @Inject constructor() {

    fun createMediaItem(playbackInfo: PlaybackInfo): MediaItem {
        Log.d("Player", "[MediaItemFactory] Creating MediaItem from url: ${playbackInfo.mediaUrl}")
        return MediaItem.fromUri(playbackInfo.mediaUrl)
    }
}
