package com.iwatchme.player.feature.playerpage.uicomponent

import android.content.Context
import android.graphics.Color
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

class VideoListItemUIComponent(
    private val viewModel: ViewModel,
) : UIComponent<UIComponent.ViewViewEntry<LinearLayout>> {

    interface ViewModel {
        val state: StateFlow<State>
        val onClick: () -> Unit
        val identityKey: String
    }

    data class State(
        val title: String,
        val duration: String,
        val isSelected: Boolean,
    )

    override fun createViewEntry(
        context: Context,
        parent: ViewGroup?,
    ): UIComponent.ViewViewEntry<LinearLayout> {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56f, context.resources.displayMetrics).toInt(),
            )
            setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8))
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true

            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
        }

        val titleView = TextView(context).apply {
            id = android.R.id.text1
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            textSize = 15f
            setTextColor(Color.parseColor("#333333"))
        }

        val durationView = TextView(context).apply {
            id = android.R.id.text2
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            textSize = 12f
            setTextColor(Color.parseColor("#999999"))
        }

        layout.addView(titleView)
        layout.addView(durationView)

        return UIComponent.ViewViewEntry(layout)
    }

    override suspend fun bindToView(viewEntry: UIComponent.ViewViewEntry<LinearLayout>) {
        val layout = viewEntry.value
        val titleView = layout.findViewById<TextView>(android.R.id.text1)
        val durationView = layout.findViewById<TextView>(android.R.id.text2)

        layout.setOnClickListener { viewModel.onClick() }

        coroutineScope {
            launch {
                viewModel.state.collectLatest { state ->
                    titleView.text = state.title
                    durationView.text = state.duration
                    if (state.isSelected) {
                        titleView.setTextColor(Color.parseColor("#FB7299"))
                        layout.setBackgroundColor(Color.parseColor("#FFF0F5"))
                    } else {
                        titleView.setTextColor(Color.parseColor("#333333"))
                        layout.setBackgroundColor(Color.TRANSPARENT)
                    }
                }
            }
        }
    }

    override fun viewReusingKey(): Any = "VideoListItem"

    override fun contentEqualityKey(): Any = viewModel.identityKey

    override fun identityEqualityKey(): Any = viewModel.identityKey

    private fun dp(context: Context, value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics,
        ).toInt()
    }
}
