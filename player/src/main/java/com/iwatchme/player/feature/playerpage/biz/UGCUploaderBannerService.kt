package com.iwatchme.player.feature.playerpage.biz

import android.graphics.Color
import com.iwatchme.player.core.di.BizScope
import com.iwatchme.player.core.ui.RunningUIComponent
import com.iwatchme.player.feature.playerpage.uicomponent.BizBannerUIComponent
import com.iwatchme.player.model.UGCDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * UGC 私有 service。注入 [UGCDetail] 这个**只在 UGCBizComponent 里有 binding 的具体类型**——
 * 编译期就保证它不会在 OGVBizComponent 里被构造。
 *
 * 暴露 [create] 给 parser，让"UP 主信息卡片"作为列表里的一个 RunningUIComponent。
 */
@BizScope
class UGCUploaderBannerService @Inject constructor(
    private val detail: UGCDetail,
) {

    fun create(): RunningUIComponent {
        val state = MutableStateFlow(
            BizBannerUIComponent.State(
                emoji = "🎬",
                title = "UP 主：${detail.uploaderName}",
                subtitle = "投稿合集 · ${detail.items.size} 个视频",
                accentColor = Color.parseColor("#FB7299"),
            ),
        )
        val viewModel = object : BizBannerUIComponent.ViewModel {
            override val state: StateFlow<BizBannerUIComponent.State> = state
        }
        return RunningUIComponent(
            BizBannerUIComponent(
                viewModel = viewModel,
                identityKeyValue = "ugc-banner-${detail.bvid}",
            ),
        )
    }
}
