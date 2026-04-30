package com.iwatchme.player.feature.playerpage.page

import android.util.Log
import com.iwatchme.player.core.di.PageScope
import com.iwatchme.player.feature.playerpage.di.PlayerBizComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * 桥接 BizScope 到 Fragment 的 UI 层。
 * BizScopeDriver 创建 BizComponent 后写入这里，Fragment 从这里读取。
 */
@PageScope
class BizComponentBridge @Inject constructor() {

    private val _bizComponentFlow = MutableStateFlow<PlayerBizComponent?>(null)
    val bizComponentFlow: StateFlow<PlayerBizComponent?> = _bizComponentFlow

    fun update(component: PlayerBizComponent?) {
        Log.d("Player", "[BizComponentBridge] BizComponent updated: ${if (component != null) "available" else "null"}")
        _bizComponentFlow.value = component
    }
}
