package com.iwatchme.player.feature.playerpage.media

import android.util.Log
import com.iwatchme.player.core.di.MediaCoroutineScope
import com.iwatchme.player.core.di.MediaScope
import com.iwatchme.player.model.PlaybackInfo
import com.iwatchme.player.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MediaScope 内的当前播放数据 repo。
 *
 * - 持有 playbackInfoFlow（小粒度数据 + 暴露 flow）
 * - 提供 loadPlaybackInfo()：repo 自己加载、写自己的 flow（参考详情页简介流程 PDF
 *   里 TheseusDemoRepository.loadData 的模式）
 *
 * 注意：repo 不持有其他 repo，不在构造期主动跑加载（避免副作用），由 Service 触发。
 */
@MediaScope
class CurrentMediaRepository @Inject constructor(
    @MediaCoroutineScope private val scope: CoroutineScope,
    private val item: VideoItem,
) {
    private val _playbackInfoFlow = MutableStateFlow<PlaybackInfo?>(null)
    val playbackInfoFlow: StateFlow<PlaybackInfo?> = _playbackInfoFlow

    init {
        Log.d("Player", "[CurrentMediaRepo] Initialized for item: id=${item.id}, title=${item.title}")
    }

    fun loadPlaybackInfo() {
        scope.launch {
            Log.d("Player", "[CurrentMediaRepo] Requesting playback info for item: id=${item.id}, title=${item.title}")
            delay(200)
            val info = PlaybackInfo(itemId = item.id, mediaUrl = item.mediaUrl)
            Log.d("Player", "[CurrentMediaRepo] Playback info ready: url=${info.mediaUrl}")
            _playbackInfoFlow.value = info
        }
    }
}
