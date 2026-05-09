package com.iwatchme.player.feature.playerpage.biz

import android.graphics.Color
import com.iwatchme.player.core.di.BizScope
import com.iwatchme.player.feature.playerpage.uicomponent.BizInfoUIComponent
import com.iwatchme.player.model.UGCDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * UGC 业务专属 service：注入 [UGCDetail] 具体类型，渲染 UP 主信息。
 * 只能在 [com.iwatchme.player.feature.playerpage.di.UGCBizComponent] 里被构造——OGVBizComponent 没有 UGCDetail 实例可绑。
 */
@BizScope
class UGCInfoService @Inject constructor(
    private val detail: UGCDetail,
) : BizInfoService {

    private val _stateFlow = MutableStateFlow(
        BizInfoUIComponent.State(
            text = "UGC · UP 主：${detail.uploaderName}  ｜  共 ${detail.items.size} 个视频",
            tagColor = Color.parseColor("#FB7299"),
        ),
    )

    override val viewModel: BizInfoUIComponent.ViewModel = object : BizInfoUIComponent.ViewModel {
        override val state: StateFlow<BizInfoUIComponent.State> = _stateFlow
    }
}
