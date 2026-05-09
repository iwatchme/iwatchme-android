package com.iwatchme.player.feature.playerpage.episode

import android.util.Log
import com.iwatchme.player.core.di.EpisodeScope
import com.iwatchme.player.model.VideoItem
import javax.inject.Inject

/**
 * EpisodeScope 的 bootstrap：
 *  - [EpisodeMetaRepository] 加载本集元数据（章节、互动节点等占位演示）；
 *  - [EpisodeCompletedService] 订阅播放状态，COMPLETED 时跳下一集（连续播放）。
 *    放在 EpisodeScope 是为了切集时整层重建，避免跨集状态串味。
 */
@EpisodeScope
class EpisodeBootstrap @Inject constructor(
    private val item: VideoItem,
    private val episodeMetaRepository: EpisodeMetaRepository,
    @Suppress("unused") private val episodeCompletedService: EpisodeCompletedService,
) {
    fun start() {
        Log.d("Player", "[EpisodeBootstrap] start() — EpisodeScope services initialized for item=${item.title}")
        episodeMetaRepository.loadMeta()
    }
}
