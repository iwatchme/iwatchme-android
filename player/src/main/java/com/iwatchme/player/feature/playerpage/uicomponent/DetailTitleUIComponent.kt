package com.iwatchme.player.feature.playerpage.uicomponent

import android.content.Context
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import com.iwatchme.player.core.ui.UIComponent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 数据向下、事件向上：UI Component 只看到 [ViewModel]，不直接接触任何 Repo。
 */
class DetailTitleUIComponent(
    private val viewModel: ViewModel,
) : UIComponent<UIComponent.ViewViewEntry<TextView>> {

    interface ViewModel {
        val state: StateFlow<State>
        val onClick: () -> Unit
    }

    data class State(
        val text: String,
        val clickable: Boolean,
        val visible: Boolean = true,
    )

    override fun createViewEntry(
        context: Context,
        parent: ViewGroup?,
    ): UIComponent.ViewViewEntry<TextView> {
        val textView = TextView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
            setTextColor(0xFF333333.toInt())
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        return UIComponent.ViewViewEntry(textView)
    }

    fun wrapExistingView(textView: TextView): UIComponent.ViewViewEntry<TextView> {
        return UIComponent.ViewViewEntry(textView)
    }

    override suspend fun bindToView(viewEntry: UIComponent.ViewViewEntry<TextView>) {
        val view = viewEntry.value
        view.setOnClickListener { viewModel.onClick() }
        coroutineScope {
            launch {
                viewModel.state.collectLatest { state ->
                    view.text = state.text
                    view.isClickable = state.clickable
                    view.isVisible = state.visible
                }
            }
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics,
        ).toInt()
    }
}
