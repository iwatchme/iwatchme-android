package com.iwatchme.player.feature.playerpage.episode

import android.util.Log
import com.iwatchme.player.core.di.EpisodeScope
import com.iwatchme.player.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@EpisodeScope
class CurrentEpisodeRepository @Inject constructor(
    private val item: VideoItem,
) {
    val currentItem: VideoItem = item

    private val _episodeFlow = MutableStateFlow(item)
    val episodeFlow: StateFlow<VideoItem> = _episodeFlow

    init {
        Log.d("Player", "[CurrentEpisodeRepo] Initialized for item: id=${item.id}, title=${item.title}, cid=${item.cid}")
    }
}
