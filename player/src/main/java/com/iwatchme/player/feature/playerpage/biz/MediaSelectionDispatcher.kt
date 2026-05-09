package com.iwatchme.player.feature.playerpage.biz

import android.util.Log
import com.iwatchme.player.core.di.BizCoroutineScope
import com.iwatchme.player.core.di.BizScope
import com.iwatchme.player.feature.playerpage.page.MediaScopeDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 把 BizScope 内 [SelectionRepository] 的"用户选了哪一集"翻译成对 PageScope 级
 * [MediaScopeDriver] 的"切换到这个 item"投递。
 *
 * 这一层是新结构的关键 —— selection 不再直接驱动 EpisodeScope，而是通过 MediaScopeDriver
 * 拉起 MediaScope；EpisodeScope 反过来由 MediaScopeDriver.currentItemFlow 驱动，于是
 * "切集"的真相变成了播放器域而不是 UI selection。
 */
@BizScope
class MediaSelectionDispatcher @Inject constructor(
    @BizCoroutineScope scope: CoroutineScope,
    private val selectionRepository: SelectionRepository,
    private val videoListRepository: VideoListRepository,
    private val mediaScopeDriver: MediaScopeDriver,
) {
    init {
        scope.launch {
            combine(
                selectionRepository.selectedItemIdFlow,
                videoListRepository.itemsFlow,
            ) { id, items -> if (id != null) items.find { it.id == id } else null }
                .filterNotNull()
                .distinctUntilChangedBy { it.id }
                .collectLatest { item ->
                    Log.d(
                        "Player",
                        "[MediaSelectionDispatcher] selection=${item.id}/${item.title} -> mediaScopeDriver.switchTo",
                    )
                    mediaScopeDriver.switchTo(item)
                }
        }
    }
}
