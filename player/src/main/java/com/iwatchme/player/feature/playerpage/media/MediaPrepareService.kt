package com.iwatchme.player.feature.playerpage.media

import android.util.Log
import com.iwatchme.player.core.di.MediaCoroutineScope
import com.iwatchme.player.core.di.MediaScope
import com.iwatchme.player.core.player.ExoPlayerHolder
import com.iwatchme.player.feature.playerpage.page.PlayerUiStateRepository
import com.iwatchme.player.model.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MediaScope 内的"准备播放"业务 Service：
 *  1. 触发 CurrentMediaRepository 加载播放信息
 *  2. 订阅 playbackInfoFlow，拿到 PlaybackInfo 后驱动 ExoPlayer 准备播放
 *  3. 同步初始 loading 状态到 PlayerUiStateRepository（跨 scope 通信）
 */
@MediaScope
class MediaPrepareService @Inject constructor(
    @MediaCoroutineScope private val scope: CoroutineScope,
    private val currentMediaRepository: CurrentMediaRepository,
    private val mediaItemFactoryService: MediaItemFactoryService,
    private val exoPlayerHolder: ExoPlayerHolder,
    private val playerUiStateRepository: PlayerUiStateRepository,
) {
    init {
        scope.launch {
            playerUiStateRepository.updatePlaybackState(PlaybackState.LOADING)

            // Service 主动触发 repo 加载
            currentMediaRepository.loadPlaybackInfo()

            currentMediaRepository.playbackInfoFlow
                .filterNotNull()
                .collectLatest { playbackInfo ->
                    val mediaItem = mediaItemFactoryService.createMediaItem(playbackInfo)
                    val player = exoPlayerHolder.player

                    Log.d("Player", "[MediaPrepareService] Setting MediaItem and preparing player for item: ${playbackInfo.itemId}")
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.playWhenReady = true

                    Log.d("Player", "[MediaPrepareService] Player prepared, playWhenReady=true")
                }
        }
    }
}
