package com.iwatchme.player.feature.playerpage.uicomponent

import android.content.Context
import android.view.ViewGroup
import android.widget.TextView
import com.iwatchme.player.core.ui.UIComponent
import com.iwatchme.player.model.DetailData
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

class DetailTitleUIComponent(
    private val detailFlow: StateFlow<DetailData?>,
) : UIComponent<UIComponent.ViewViewEntry<TextView>> {

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
            text = "等待加载..."
        }
        return UIComponent.ViewViewEntry(textView)
    }

    fun wrapExistingView(textView: TextView): UIComponent.ViewViewEntry<TextView> {
        return UIComponent.ViewViewEntry(textView)
    }

    override suspend fun bindToView(viewEntry: UIComponent.ViewViewEntry<TextView>) {
        detailFlow.collectLatest { detail ->
            viewEntry.value.text = detail?.title ?: "等待加载..."
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
