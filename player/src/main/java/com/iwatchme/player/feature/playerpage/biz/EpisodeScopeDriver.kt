package com.iwatchme.player.feature.playerpage.biz

import android.util.Log
import com.iwatchme.player.core.di.BizCoroutineScope
import com.iwatchme.player.core.di.BizScope
import com.iwatchme.player.feature.playerpage.di.CurrentEpisodeComponent
import com.iwatchme.player.feature.playerpage.page.MediaScopeDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * EpisodeScope 由"当前正在播放的 item"反向驱动：
 * 数据来源是 PageScope 的 [MediaScopeDriver.currentItemFlow]，而不是 BizScope 内的
 * [SelectionRepository]。selection 的写入由 [MediaSelectionDispatcher] 翻译成对
 * MediaScopeDriver 的 switchTo 调用，由播放器域统一决定"现在到底是哪一集在播"。
 */
@BizScope
class EpisodeScopeDriver @Inject constructor(
    @BizCoroutineScope private val bizScope: CoroutineScope,
    private val mediaScopeDriver: MediaScopeDriver,
    private val episodeComponentFactory: CurrentEpisodeComponent.Factory,
    private val currentEpisodeComponentRepository: CurrentEpisodeComponentRepository,
) {
    init {
        bizScope.launch {
            mediaScopeDriver.currentItemFlow
                .filterNotNull()
                .distinctUntilChangedBy { it.id }
                .collectLatest { item ->
                    Log.d("Player", "[EpisodeScopeDriver] >>> EpisodeScope CREATING for item=${item.title}")
                    coroutineScope {
                        val component = episodeComponentFactory.create(
                            scope = this,
                            item = item,
                        )
                        currentEpisodeComponentRepository.update(component)
                        component.bootstrap().start()
                        Log.d("Player", "[EpisodeScopeDriver] EpisodeScope started, awaiting cancellation...")
                        try {
                            awaitCancellation()
                        } finally {
                            currentEpisodeComponentRepository.update(null)
                            Log.d("Player", "[EpisodeScopeDriver] <<< EpisodeScope DESTROYED for item=${item.title}")
                        }
                    }
                }
        }
    }
}
