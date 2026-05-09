package com.iwatchme.player.feature.playerpage.page

import android.util.Log
import com.iwatchme.player.core.di.PageScope
import com.iwatchme.player.feature.playerpage.di.PlayerBizFacade
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * 当前激活的 BizComponent 引用，类型是 [PlayerBizFacade]——可能是 UGCBizComponent 也可能是 OGVBizComponent，
 * 由 [BizScopeDriver.driveBusinessScope] 按 sealed DetailData 类型分发选择。
 *
 * Fragment 通过 facade 接口访问通用能力（list / title / info），不需要知道具体业务类型。
 */
@PageScope
class CurrentBizComponentRepository @Inject constructor() {

    private val _componentFlow = MutableStateFlow<PlayerBizFacade?>(null)
    val componentFlow: StateFlow<PlayerBizFacade?> = _componentFlow

    fun update(component: PlayerBizFacade?) {
        Log.d(
            "Player",
            "[CurrentBizComponentRepo] BizComponent updated: ${component?.javaClass?.simpleName ?: "null"}",
        )
        _componentFlow.value = component
    }
}
