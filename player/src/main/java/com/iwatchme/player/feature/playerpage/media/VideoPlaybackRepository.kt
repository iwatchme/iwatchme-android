package com.iwatchme.player.feature.playerpage.media

import android.util.Log
import com.iwatchme.player.core.di.MediaCoroutineScope
import com.iwatchme.player.core.di.MediaScope
import com.iwatchme.player.feature.playerpage.mock.MockData
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
            Log.d("Player", "[VideoPlaybackRepo] Requesting playback info for item: id=${item.id}")
            delay(200) // 模拟网络延迟
            val info = MockData.getPlaybackInfo(item.id)
            if (info != null) {
                Log.d("Player", "[VideoPlaybackRepo] Playback info received: url=${info.mediaUrl}")
                currentMediaRepository.updatePlaybackInfo(info)
            } else {
                Log.e("Player", "[VideoPlaybackRepo] No playback info found for item: id=${item.id}")
            }
        }
    }
}
