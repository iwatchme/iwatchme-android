package com.iwatchme.player.feature.playerpage.biz

import com.iwatchme.player.core.di.BizCoroutineScope
import com.iwatchme.player.core.di.BizScope
import com.iwatchme.player.feature.playerpage.page.ScreenStateRepository
import com.iwatchme.player.feature.playerpage.uicomponent.EpisodeTitleUIComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "正在播放：xxx" 标题的 Service。
 *
 * 数据源：
 *  - selection × items 解出当前 item 决定文案
 *  - PageScope 的 ScreenStateRepository 决定可见性（横屏隐藏，对齐 CLAUDE.md §9.9）
 */
@BizScope
class EpisodeTitleService @Inject constructor(
    @BizCoroutineScope private val scope: CoroutineScope,
    private val selectionRepository: SelectionRepository,
    private val videoListRepository: VideoListRepository,
    private val screenStateRepository: ScreenStateRepository,
) {

    private val _stateFlow = MutableStateFlow(EpisodeTitleUIComponent.State(text = ""))

    val viewModel: EpisodeTitleUIComponent.ViewModel = object : EpisodeTitleUIComponent.ViewModel {
        override val state: StateFlow<EpisodeTitleUIComponent.State> = _stateFlow
    }

    init {
        scope.launch {
            combine(
                selectionRepository.selectedItemIdFlow,
                videoListRepository.itemsFlow,
                screenStateRepository.screenStateFlow,
            ) { id, items, screenState ->
                val item = if (id != null) items.find { it.id == id } else null
                val text = if (item != null) "正在播放：${item.title}" else ""
                EpisodeTitleUIComponent.State(text = text, visible = !screenState.isFullscreen)
            }.collectLatest { _stateFlow.value = it }
        }
    }
}
