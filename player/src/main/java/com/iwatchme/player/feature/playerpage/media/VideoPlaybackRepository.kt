package com.iwatchme.player.feature.playerpage.media

import android.util.Log
import com.iwatchme.player.core.di.MediaCoroutineScope
import com.iwatchme.player.core.di.MediaScope
import com.iwatchme.player.model.PlaybackInfo
import com.iwatchme.player.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@MediaScope
class VideoPlaybackRepository @Inject constructor(
    @MediaCoroutineScope private val scope: CoroutineScope,
    private val item: VideoItem,
    private val currentMediaRepository: CurrentMediaRepository,
) {
    init {
        scope.launch {
            Log.d("Player", "[VideoPlaybackRepo] Requesting playback info for item: id=${item.id}, title=${item.title}")
            delay(200)
            val info = PlaybackInfo(itemId = item.id, mediaUrl = item.mediaUrl)
            Log.d("Player", "[VideoPlaybackRepo] Playback info ready: url=${info.mediaUrl}")
            currentMediaRepository.updatePlaybackInfo(info)
        }
    }
}
