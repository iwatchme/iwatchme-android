package com.iwatchme.player.feature.playerpage.page

import android.util.Log
import com.iwatchme.player.core.di.PageScope
import com.iwatchme.player.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@PageScope
class PlayRequestRepository @Inject constructor() {

    private val _playRequestFlow = MutableStateFlow<VideoItem?>(null)
    val playRequestFlow: StateFlow<VideoItem?> = _playRequestFlow

    fun requestPlay(item: VideoItem) {
        Log.d("Player", "[PlayRequestRepo] Play requested: id=${item.id}, title=${item.title}")
        _playRequestFlow.value = item
    }
}
