package com.iwatchme.player.feature.playerpage.biz

import com.iwatchme.player.core.di.BizScope
import com.iwatchme.player.core.ui.RunningUIComponent
import com.iwatchme.player.feature.playerpage.uicomponent.VideoListItemUIComponent
import com.iwatchme.player.model.VideoItem
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 列表项 Service。每个列表项一份"create()"调用，对应一个 [RunningUIComponent]：
 *  - 闭包持有 per-item 的 [VideoListItemUIComponent.State]
 *  - state 驱动写在 [RunningUIComponent.runUntilCancellation] 的 stateDriver 里，
 *    随组件被 [BizRecyclerViewService] 拉起 / 销毁
 *
 * 这正是 PDF《详情页简介流程》里 TheseusDemoService.create() 的写法。
 */
@BizScope
class VideoListItemService @Inject constructor(
    private val selectionRepository: SelectionRepository,
) {

    fun create(item: VideoItem): RunningUIComponent {
        val stateFlow = MutableStateFlow(
            VideoListItemUIComponent.State(
                title = item.title,
                duration = formatDuration(item.durationMs),
                isSelected = false,
            ),
        )

        fun handleClick() {
            selectionRepository.select(item.id)
        }

        val viewModel = object : VideoListItemUIComponent.ViewModel {
            override val state: StateFlow<VideoListItemUIComponent.State> = stateFlow
            override val onClick: () -> Unit = ::handleClick
            override val identityKey: String = item.id
        }

        return RunningUIComponent(VideoListItemUIComponent(viewModel)) {
            coroutineScope {
                launch {
                    selectionRepository.selectedItemIdFlow.collectLatest { selectedId ->
                        stateFlow.value = stateFlow.value.copy(isSelected = selectedId == item.id)
                    }
                }
            }
        }
    }

    private fun formatDuration(ms: Long?): String {
        if (ms == null) return ""
        if (ms < 0) return "直播"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
}
