package com.iwatchme.player.feature.playerpage.biz

import com.iwatchme.player.core.di.BizCoroutineScope
import com.iwatchme.player.core.di.BizScope
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
 * Service 同时持有 [SelectionRepository] 与 [VideoListRepository]，自己组合 id→item，
 * 满足"repo 之间不互相依赖、组合逻辑下沉到 Service"。
 */
@BizScope
class EpisodeTitleService @Inject constructor(
    @BizCoroutineScope private val scope: CoroutineScope,
    private val selectionRepository: SelectionRepository,
    private val videoListRepository: VideoListRepository,
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
            ) { id, items ->
                if (id != null) items.find { it.id == id } else null
            }.collectLatest { item ->
                _stateFlow.value = EpisodeTitleUIComponent.State(
                    text = if (item != null) "正在播放：${item.title}" else "",
                )
            }
        }
    }
}
