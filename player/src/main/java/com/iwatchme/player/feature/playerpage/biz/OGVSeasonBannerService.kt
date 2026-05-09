package com.iwatchme.player.feature.playerpage.biz

import android.graphics.Color
import com.iwatchme.player.core.di.BizScope
import com.iwatchme.player.core.ui.RunningUIComponent
import com.iwatchme.player.feature.playerpage.uicomponent.BizBannerUIComponent
import com.iwatchme.player.model.OGVDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * OGV 私有 service。注入 [OGVDetail] 这个**只在 OGVBizComponent 里有 binding 的具体类型**。
 * 暴露 [create] 给 parser，作为"季度信息卡片"加入列表。
 */
@BizScope
class OGVSeasonBannerService @Inject constructor(
    private val detail: OGVDetail,
) {

    fun create(): RunningUIComponent {
        val accent = if (detail.vipOnly) Color.parseColor("#FF9F00") else Color.parseColor("#1E88E5")
        val state = MutableStateFlow(
            BizBannerUIComponent.State(
                emoji = if (detail.vipOnly) "⭐" else "📺",
                title = "Season #${detail.seasonId}",
                subtitle = buildString {
                    append("共 ${detail.totalEpisodes} 集")
                    if (detail.vipOnly) append(" · 大会员限定")
                },
                accentColor = accent,
            ),
        )
        val viewModel = object : BizBannerUIComponent.ViewModel {
            override val state: StateFlow<BizBannerUIComponent.State> = state
        }
        return RunningUIComponent(
            BizBannerUIComponent(
                viewModel = viewModel,
                identityKeyValue = "ogv-banner-${detail.bvid}",
            ),
        )
    }
}
