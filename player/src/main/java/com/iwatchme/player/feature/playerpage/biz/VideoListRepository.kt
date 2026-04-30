package com.iwatchme.player.feature.playerpage.biz

import android.util.Log
import com.iwatchme.player.core.di.BizScope
import com.iwatchme.player.model.DetailData
import com.iwatchme.player.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@BizScope
class VideoListRepository @Inject constructor(
    detail: DetailData,
) {
    private val _itemsFlow = MutableStateFlow(detail.items)
    val itemsFlow: StateFlow<List<VideoItem>> = _itemsFlow

    init {
        Log.d("Player", "[VideoListRepository] Initialized with ${detail.items.size} items")
    }
}
