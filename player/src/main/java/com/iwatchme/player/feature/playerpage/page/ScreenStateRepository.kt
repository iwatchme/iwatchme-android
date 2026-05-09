package com.iwatchme.player.feature.playerpage.page

import android.util.Log
import com.iwatchme.player.core.di.PageScope
import com.iwatchme.player.model.ScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * 屏幕状态——纯数据持有 + 业务语义入口，无任何订阅 / IO。
 *
 * 放在 PageScope 是因为这状态在切合集 / 切集 / 切业务时**都不该重置**：用户横屏看 a1 切到 a2
 * 仍应保持横屏。任何想切横竖屏的代码都调 [switchToFullscreen] / [switchToHalfscreen] / [toggle]，
 * 不要直接写 _flow.value。
 */
@PageScope
class ScreenStateRepository @Inject constructor() {

    private val _screenStateFlow = MutableStateFlow(ScreenState.PORTRAIT_HALF)
    val screenStateFlow: StateFlow<ScreenState> = _screenStateFlow

    val currentState: ScreenState get() = _screenStateFlow.value

    fun switchToFullscreen() {
        if (_screenStateFlow.value != ScreenState.LANDSCAPE_FULL) {
            Log.d("Player", "[ScreenStateRepo] -> LANDSCAPE_FULL")
            _screenStateFlow.value = ScreenState.LANDSCAPE_FULL
        }
    }

    fun switchToHalfscreen() {
        if (_screenStateFlow.value != ScreenState.PORTRAIT_HALF) {
            Log.d("Player", "[ScreenStateRepo] -> PORTRAIT_HALF")
            _screenStateFlow.value = ScreenState.PORTRAIT_HALF
        }
    }

    fun toggle() {
        when (_screenStateFlow.value) {
            ScreenState.PORTRAIT_HALF -> switchToFullscreen()
            ScreenState.LANDSCAPE_FULL -> switchToHalfscreen()
        }
    }
}
