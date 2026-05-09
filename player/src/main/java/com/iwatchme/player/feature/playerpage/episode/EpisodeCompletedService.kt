package com.iwatchme.player.feature.playerpage.episode

import android.util.Log
import com.iwatchme.player.core.di.EpisodeCoroutineScope
import com.iwatchme.player.core.di.EpisodeScope
import com.iwatchme.player.feature.playerpage.biz.SelectionRepository
import com.iwatchme.player.feature.playerpage.biz.VideoListRepository
import com.iwatchme.player.feature.playerpage.page.PlayerUiStateRepository
import com.iwatchme.player.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 一集播完的处理：监听 PageScope 的完播事件流，自动跳到列表中下一集。
 *
 * 跨 scope 注入：
 *  - [item]：本 EpisodeScope 的 VideoItem（@BindsInstance）；
 *  - [videoListRepository] / [selectionRepository]：BizScope 提供（向上一层）；
 *  - [playerUiStateRepository]：PageScope 提供（向上两层），但**只订阅它的事件流**而不是 stateFlow。
 *
 * 用 SharedFlow 事件流（[PlayerUiStateRepository.completionEvents]）而不是 StateFlow + drop(1)：
 * 事件 = 一次性发生的事；StateFlow 会向新订阅者重放当前值，导致上一集的 COMPLETED 被新
 * EpisodeScope 当作"刚发生的事"再次触发，形成死循环。SharedFlow(replay=0) 天然没这问题。
 */
@EpisodeScope
class EpisodeCompletedService @Inject constructor(
    @EpisodeCoroutineScope private val scope: CoroutineScope,
    private val item: VideoItem,
    private val playerUiStateRepository: PlayerUiStateRepository,
    private val videoListRepository: VideoListRepository,
    private val selectionRepository: SelectionRepository,
) {
    init {
        scope.launch {
            playerUiStateRepository.completionEvents.collect {
                handleCompleted()
            }
        }
    }

    private fun handleCompleted() {
        val items = videoListRepository.itemsFlow.value
        if (items.isEmpty()) return
        val currentIdx = items.indexOfFirst { it.id == item.id }
        if (currentIdx < 0) {
            Log.w("Player", "[EpisodeCompletedService] current item ${item.id} not in list, skip")
            return
        }
        val nextIdx = (currentIdx + 1) % items.size
        val nextItem = items[nextIdx]
        Log.d(
            "Player",
            "[EpisodeCompletedService] ▶▶ auto-next: ${item.title} -> ${nextItem.title} (idx $currentIdx -> $nextIdx)",
        )
        selectionRepository.select(nextItem.id)
    }
}
