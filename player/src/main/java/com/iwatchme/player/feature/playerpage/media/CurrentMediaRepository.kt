package com.iwatchme.player.feature.playerpage.media

import android.util.Log
import com.iwatchme.player.core.di.MediaScope
import com.iwatchme.player.model.PlaybackInfo
import com.iwatchme.player.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@MediaScope
class CurrentMediaRepository @Inject constructor(
    private val item: VideoItem,
) {
    val currentItem: VideoItem = item

    private val _playbackInfoFlow = MutableStateFlow<PlaybackInfo?>(null)
    val playbackInfoFlow: StateFlow<PlaybackInfo?> = _playbackInfoFlow

    fun updatePlaybackInfo(info: PlaybackInfo) {
        Log.d("Player", "[CurrentMediaRepo] PlaybackInfo updated: url=${info.mediaUrl}")
        _playbackInfoFlow.value = info
    }

    init {
        Log.d("Player", "[CurrentMediaRepo] Initialized for item: id=${item.id}, title=${item.title}")
    }
}
