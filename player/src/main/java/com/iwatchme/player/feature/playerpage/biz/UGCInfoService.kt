package com.iwatchme.player.feature.playerpage.biz

import android.graphics.Color
import com.iwatchme.player.core.di.BizCoroutineScope
import com.iwatchme.player.core.di.BizScope
import com.iwatchme.player.feature.playerpage.page.ScreenStateRepository
import com.iwatchme.player.feature.playerpage.uicomponent.BizInfoUIComponent
import com.iwatchme.player.model.UGCDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UGC 业务专属 service：注入 [UGCDetail] 具体类型，渲染 UP 主信息。
 * 横屏时隐藏自己（订阅 PageScope 的 ScreenStateRepository，对齐 CLAUDE.md §9.9）。
 */
@BizScope
class UGCInfoService @Inject constructor(
    @BizCoroutineScope private val scope: CoroutineScope,
    private val detail: UGCDetail,
    private val screenStateRepository: ScreenStateRepository,
) : BizInfoService {

    private val baseState = BizInfoUIComponent.State(
        text = "UGC · UP 主：${detail.uploaderName}  ｜  共 ${detail.items.size} 个视频",
        tagColor = Color.parseColor("#FB7299"),
    )

    private val _stateFlow = MutableStateFlow(baseState)

    override val viewModel: BizInfoUIComponent.ViewModel = object : BizInfoUIComponent.ViewModel {
        override val state: StateFlow<BizInfoUIComponent.State> = _stateFlow
    }

    init {
        scope.launch {
            screenStateRepository.screenStateFlow.collectLatest { screenState ->
                _stateFlow.value = baseState.copy(visible = !screenState.isFullscreen)
            }
        }
    }
}
