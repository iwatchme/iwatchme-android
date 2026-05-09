package com.iwatchme.player.feature.playerpage.page

import android.content.res.Configuration
import android.util.Log
import android.view.OrientationEventListener
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.iwatchme.player.core.di.PageCoroutineScope
import com.iwatchme.player.core.di.PageScope
import com.iwatchme.player.feature.playerpage.uicomponent.FullscreenButtonUIComponent
import com.iwatchme.player.model.ScreenState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 屏幕状态的输入聚合 + 主要 imperative side-effect。
 *
 * 输入端（**写**到 [ScreenStateRepository]）：
 *  - 重力感应（OrientationEventListener）→ 真实旋转事件触发时调 repo.switchTo*；
 *  - 按钮点击（通过 [fullscreenButtonViewModel] 的 onClick 暴露）→ repo.toggle()。
 *
 * 输出端（**订阅** [ScreenStateRepository.screenStateFlow]）：
 *  - 调 `activity.requestedOrientation` 真正旋转屏幕；
 *  - 注册 [OnBackPressedCallback]，全屏时拦截返回键 → 切回半屏；
 *  - 更新 [fullscreenButtonViewModel] 状态供 UIComponent 渲染。
 *
 * **Fragment 不参与任何 screen state 的订阅**——所有 imperative 操作都在本 service 里完成（包括
 * activity orientation 切换）。这是 CLAUDE.md §9.8 要求的形态。
 */
@PageScope
class ScreenStateService @Inject constructor(
    @PageCoroutineScope private val coroutineScope: CoroutineScope,
    private val activity: ComponentActivity,
    private val screenStateRepository: ScreenStateRepository,
) {

    private val _buttonStateFlow = MutableStateFlow(buttonStateFor(screenStateRepository.currentState))

    val fullscreenButtonViewModel: FullscreenButtonUIComponent.ViewModel =
        object : FullscreenButtonUIComponent.ViewModel {
            override val state: StateFlow<FullscreenButtonUIComponent.State> = _buttonStateFlow
            override val onClick: () -> Unit = {
                Log.d("Player", "[ScreenStateService] fullscreen button click")
                screenStateRepository.toggle()
            }
        }

    private val backPressCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            Log.d("Player", "[ScreenStateService] back pressed in fullscreen → switchToHalfscreen")
            screenStateRepository.switchToHalfscreen()
        }
    }

    init {
        // 用 activity 的当前 configuration 反推初始 state——配置变更（横竖屏旋转）后 activity 重建，
        // 新 service 起来时如果用 repo 默认值会强行把屏幕转回去；按 activity 当前方向 seed 一次避免抖动
        if (activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            screenStateRepository.switchToFullscreen()
        }

        // 注册返回键拦截：activity 销毁时自动移除（addCallback 第一个参数传 LifecycleOwner）
        activity.onBackPressedDispatcher.addCallback(activity, backPressCallback)

        // 输出端：repo state 变化 → 改 activity orientation + back press 拦截 + 按钮状态
        coroutineScope.launch {
            screenStateRepository.screenStateFlow.collect { state ->
                Log.d("Player", "[ScreenStateService] screen state -> $state")
                if (activity.requestedOrientation != state.orientation) {
                    activity.requestedOrientation = state.orientation
                }
                backPressCallback.isEnabled = state.isFullscreen
                _buttonStateFlow.value = buttonStateFor(state)
            }
        }

        // 输入端：重力感应 → repo。
        // orientationFlow() 内部已经吃掉了 listener attach 后的首次回调（"当前姿势"快照不算事件），
        // 这里只会拿到真实旋转触发的角度。
        coroutineScope.launch {
            orientationFlow()
                .mapNotNull(::orientationToScreenState)
                .distinctUntilChanged()
                .collect { detected ->
                    when (detected) {
                        ScreenState.LANDSCAPE_FULL -> screenStateRepository.switchToFullscreen()
                        ScreenState.PORTRAIT_HALF -> screenStateRepository.switchToHalfscreen()
                    }
                }
        }
    }

    /**
     * OrientationEventListener 是 hot property——`enable()` 后第一次 onOrientationChanged
     * 报的是"当前姿势"快照，不是"刚刚旋转了"。如果直接转发，会出现"用户竖直拿手机点全屏 → sensor
     * 立刻报 portrait → 把 fullscreen 抢回去"的死循环（与 CLAUDE.md §6.2 同型问题）。
     *
     * 解法：在 listener 内部吃掉首次回调，让 flow 只发"真正发生过的旋转"。
     */
    private fun orientationFlow(): Flow<Int> = callbackFlow {
        val listener = object : OrientationEventListener(activity) {
            private var firstReading = true
            override fun onOrientationChanged(orientation: Int) {
                if (firstReading) {
                    firstReading = false
                    return
                }
                if (orientation != ORIENTATION_UNKNOWN) trySend(orientation)
            }
        }
        listener.enable()
        awaitClose { listener.disable() }
    }

    /**
     * 角度阈值：0/180 ±30 视为竖屏，90/270 ±30 视为横屏，其他角度落入死区返回 null 不更新。
     * 死区设计是为了避免设备刚好处于斜放时频繁来回切换。
     */
    private fun orientationToScreenState(degrees: Int): ScreenState? = when (degrees) {
        in 60..120 -> ScreenState.LANDSCAPE_FULL
        in 240..300 -> ScreenState.LANDSCAPE_FULL
        in 0..30 -> ScreenState.PORTRAIT_HALF
        in 330..359 -> ScreenState.PORTRAIT_HALF
        in 150..210 -> ScreenState.PORTRAIT_HALF
        else -> null
    }

    private fun buttonStateFor(state: ScreenState) = FullscreenButtonUIComponent.State(
        iconText = if (state.isFullscreen) "⤡" else "⛶",
        contentDescription = if (state.isFullscreen) "退出全屏" else "进入全屏",
    )
}
