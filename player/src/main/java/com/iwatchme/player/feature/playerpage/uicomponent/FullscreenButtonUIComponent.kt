package com.iwatchme.player.feature.playerpage.uicomponent

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.iwatchme.player.core.ui.UIComponent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 播放器右下角的全屏切换按钮。绑 [ScreenStateService.fullscreenButtonViewModel]：
 *  - state.iconText 决定文案 / icon（demo 用 emoji 代替图标）
 *  - state.contentDescription 给无障碍
 *  - onClick 直接调 service 暴露的 click handler，不让 Fragment 知道 ScreenStateRepository 存在
 */
class FullscreenButtonUIComponent(
    private val viewModel: ViewModel,
) : UIComponent<UIComponent.ViewViewEntry<TextView>> {

    interface ViewModel {
        val state: StateFlow<State>
        val onClick: () -> Unit
    }

    data class State(
        val iconText: String,
        val contentDescription: String,
    )

    override fun createViewEntry(
        context: Context,
        parent: ViewGroup?,
    ): UIComponent.ViewViewEntry<TextView> {
        val tv = TextView(context).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(0x66000000)
            setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
            isClickable = true
            isFocusable = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END,
            ).apply {
                val margin = dp(context, 12)
                setMargins(margin, margin, margin, margin)
            }
        }
        return UIComponent.ViewViewEntry(tv)
    }

    override suspend fun bindToView(viewEntry: UIComponent.ViewViewEntry<TextView>) {
        val tv = viewEntry.value
        tv.setOnClickListener { viewModel.onClick() }
        coroutineScope {
            launch {
                viewModel.state.collectLatest { state ->
                    tv.text = state.iconText
                    tv.contentDescription = state.contentDescription
                }
            }
        }
    }

    private fun dp(context: Context, value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        context.resources.displayMetrics,
    ).toInt()
}
