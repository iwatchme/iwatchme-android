package com.iwatchme.player.feature.playerpage.biz

import android.graphics.Color
import com.iwatchme.player.core.di.BizScope
import com.iwatchme.player.feature.playerpage.uicomponent.BizInfoUIComponent
import com.iwatchme.player.model.OGVDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * OGV 业务专属 service：注入 [OGVDetail] 具体类型，渲染季度信息 + VIP 标记。
 * 只能在 [com.iwatchme.player.feature.playerpage.di.OGVBizComponent] 里被构造——UGCBizComponent 没有 OGVDetail 实例可绑。
 */
@BizScope
class OGVSeasonService @Inject constructor(
    private val detail: OGVDetail,
) : BizInfoService {

    private val _stateFlow = MutableStateFlow(
        BizInfoUIComponent.State(
            text = buildString {
                append("OGV · 季 ID:${detail.seasonId}  ｜  共 ${detail.totalEpisodes} 集")
                if (detail.vipOnly) append("  ｜  ⭐ 大会员限定")
            },
            tagColor = if (detail.vipOnly) Color.parseColor("#FF9F00") else Color.parseColor("#1E88E5"),
        ),
    )

    override val viewModel: BizInfoUIComponent.ViewModel = object : BizInfoUIComponent.ViewModel {
        override val state: StateFlow<BizInfoUIComponent.State> = _stateFlow
    }
}
