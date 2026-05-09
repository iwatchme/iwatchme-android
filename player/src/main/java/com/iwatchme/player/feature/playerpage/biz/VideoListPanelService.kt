package com.iwatchme.player.feature.playerpage.biz

import com.iwatchme.player.core.di.BizCoroutineScope
import com.iwatchme.player.core.di.BizScope
import com.iwatchme.player.feature.playerpage.page.ScreenStateRepository
import com.iwatchme.player.feature.playerpage.uicomponent.PanelVisibilityUIComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 视频列表面板的可见性 service。订阅 PageScope 的 [ScreenStateRepository]，横屏全屏时把
 * `binding.videoList` 隐藏掉。Fragment 通过 [PanelVisibilityUIComponent] 把这个 viewModel
 * 接到 RecyclerView 上即可。
 */
@BizScope
class VideoListPanelService @Inject constructor(
    @BizCoroutineScope private val scope: CoroutineScope,
    private val screenStateRepository: ScreenStateRepository,
) {

    private val _visibleFlow = MutableStateFlow(true)

    val viewModel: PanelVisibilityUIComponent.ViewModel =
        object : PanelVisibilityUIComponent.ViewModel {
            override val visibleFlow: StateFlow<Boolean> = _visibleFlow
        }

    init {
        scope.launch {
            screenStateRepository.screenStateFlow.collectLatest { screenState ->
                _visibleFlow.value = !screenState.isFullscreen
            }
        }
    }
}
