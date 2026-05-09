package com.iwatchme.player.feature.playerpage.page

import android.util.Log
import com.iwatchme.player.core.di.PageCoroutineScope
import com.iwatchme.player.core.di.PageScope
import com.iwatchme.player.feature.playerpage.mock.MockData
import com.iwatchme.player.feature.playerpage.uicomponent.DetailTitleUIComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 顶部"合集标题"UI 的 Service。
 *
 * 数据源是 [BizScopeDriver.stateFlow]（不是 PageDetailRepository——它现在是无状态 IO）。
 * 点击事件构造 [BizScopeDriver.StartParams] 调 switchToNewVideo，next bvid 从 MockData 算出来。
 */
@PageScope
class DetailTitleService @Inject constructor(
    @PageCoroutineScope private val scope: CoroutineScope,
    private val bizScopeDriver: BizScopeDriver,
) {

    private val _stateFlow = MutableStateFlow(
        DetailTitleUIComponent.State(text = "等待加载...", clickable = false),
    )

    val viewModel: DetailTitleUIComponent.ViewModel = object : DetailTitleUIComponent.ViewModel {
        override val state: StateFlow<DetailTitleUIComponent.State> = _stateFlow
        override val onClick: () -> Unit = ::handleClick
    }

    init {
        scope.launch {
            bizScopeDriver.stateFlow.collectLatest { state ->
                _stateFlow.value = when (state) {
                    is BizScopeDriver.State.InBusiness -> DetailTitleUIComponent.State(
                        text = "${state.detail.title}  （点击切合集）",
                        clickable = true,
                    )
                    is BizScopeDriver.State.Loading -> DetailTitleUIComponent.State(
                        text = "加载中...",
                        clickable = false,
                    )
                    is BizScopeDriver.State.Failure -> DetailTitleUIComponent.State(
                        text = "加载失败  （点击重试）",
                        clickable = true,
                    )
                    BizScopeDriver.State.Idle -> DetailTitleUIComponent.State(
                        text = "等待加载...",
                        clickable = false,
                    )
                }
            }
        }
    }

    private fun handleClick() {
        when (val state = bizScopeDriver.stateFlow.value) {
            is BizScopeDriver.State.InBusiness -> {
                val next = MockData.nextDetailAfter(state.detail)
                Log.d("Player", "[DetailTitleService] click — switch to ${next.bvid}")
                bizScopeDriver.switchToNewVideo(BizScopeDriver.StartParams(bvid = next.bvid))
            }
            is BizScopeDriver.State.Failure -> {
                Log.d("Player", "[DetailTitleService] click — retry ${state.startParams.bvid}")
                bizScopeDriver.switchToNewVideo(state.startParams)
            }
            else -> {
                Log.d("Player", "[DetailTitleService] click ignored, state=$state")
            }
        }
    }
}
