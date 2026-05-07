package com.iwatchme.player.feature.playerpage.episode

import android.util.Log
import com.iwatchme.player.core.di.EpisodeScope
import com.iwatchme.player.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class MediaRequest(
    val item: VideoItem,
    val seekMs: Long = 0,
)

/**
 * EpisodeScope 内的"播放请求"单一数据源。Episode 创建后立刻发出第一条请求，
 * MediaScopeDriver 收到后建立 MediaScope。后续若要换清晰度/换音轨，可通过更新
 * 这个 flow 触发 MediaScope 重建（同一 EpisodeScope 内）。
 */
@EpisodeScope
class MediaRequestRepository @Inject constructor(
    private val item: VideoItem,
) {
    private val _requestFlow = MutableStateFlow<MediaRequest?>(MediaRequest(item))
    val requestFlow: StateFlow<MediaRequest?> = _requestFlow

    init {
        Log.d("Player", "[MediaRequestRepo] Seeded with item=${item.title}")
    }

    fun updateRequest(request: MediaRequest) {
        Log.d("Player", "[MediaRequestRepo] Request updated: item=${request.item.title}, seek=${request.seekMs}ms")
        _requestFlow.value = request
    }
}
