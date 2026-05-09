package com.iwatchme.player.feature.playerpage.uicomponent

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.iwatchme.player.core.ui.UIComponent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 业务专属横幅卡片，进入 RecyclerView 列表（在视频列表上方）。
 *
 * UGC 和 OGV 共用同一个 UIComponent 类——业务差异完全由 [State] 的 emoji / 文案 / 配色决定，
 * 而 State 由各自 biz scope 内不同的 service（UGCUploaderBannerService / OGVSeasonBannerService）
 * 在 parser 里 emit 时填好。这就是"UI 通用、数据业务化"。
 */
class BizBannerUIComponent(
    private val viewModel: ViewModel,
    private val identityKeyValue: String,
) : UIComponent<UIComponent.ViewViewEntry<LinearLayout>> {

    interface ViewModel {
        val state: StateFlow<State>
    }

    data class State(
        val emoji: String,
        val title: String,
        val subtitle: String,
        val accentColor: Int,
    )

    override fun createViewEntry(
        context: Context,
        parent: ViewGroup?,
    ): UIComponent.ViewViewEntry<LinearLayout> {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))
            gravity = Gravity.CENTER_VERTICAL
        }

        val emojiView = TextView(context).apply {
            id = android.R.id.icon
            textSize = 22f
            setPadding(0, 0, dp(context, 12), 0)
        }
        card.addView(emojiView)

        val textBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val titleView = TextView(context).apply {
            id = android.R.id.text1
            textSize = 14f
            setTextColor(Color.parseColor("#222222"))
        }
        val subtitleView = TextView(context).apply {
            id = android.R.id.text2
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
        }
        textBox.addView(titleView)
        textBox.addView(subtitleView)
        card.addView(textBox)
        return UIComponent.ViewViewEntry(card)
    }

    override suspend fun bindToView(viewEntry: UIComponent.ViewViewEntry<LinearLayout>) {
        val card = viewEntry.value
        val emojiView = card.findViewById<TextView>(android.R.id.icon)
        val titleView = card.findViewById<TextView>(android.R.id.text1)
        val subtitleView = card.findViewById<TextView>(android.R.id.text2)
        coroutineScope {
            launch {
                viewModel.state.collectLatest { state ->
                    emojiView.text = state.emoji
                    titleView.text = state.title
                    subtitleView.text = state.subtitle
                    val bg = GradientDrawable().apply {
                        cornerRadius = dp(card.context, 8).toFloat()
                        setStroke(dp(card.context, 1), state.accentColor)
                        setColor(adjustAlpha(state.accentColor, 0.08f))
                    }
                    card.background = bg
                }
            }
        }
    }

    override fun viewReusingKey(): Any = "BizBanner"

    override fun contentEqualityKey(): Any = identityKeyValue

    override fun identityEqualityKey(): Any = identityKeyValue

    private fun dp(context: Context, value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        context.resources.displayMetrics,
    ).toInt()

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val a = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }
}
