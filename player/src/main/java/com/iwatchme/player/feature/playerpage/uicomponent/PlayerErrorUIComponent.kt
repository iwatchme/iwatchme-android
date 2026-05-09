package com.iwatchme.player.feature.playerpage.uicomponent

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.iwatchme.player.core.ui.UIComponent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlayerErrorUIComponent(
    private val viewModel: ViewModel,
) : UIComponent<UIComponent.ViewViewEntry<FrameLayout>> {

    interface ViewModel {
        val state: StateFlow<State>
    }

    data class State(
        val visible: Boolean,
        val message: String,
    )

    override fun createViewEntry(
        context: Context,
        parent: ViewGroup?,
    ): UIComponent.ViewViewEntry<FrameLayout> {
        val frame = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.parseColor("#CC000000"))
        }

        val textView = TextView(context).apply {
            id = android.R.id.message
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            )
            setTextColor(Color.WHITE)
            textSize = 14f
        }

        frame.addView(textView)
        return UIComponent.ViewViewEntry(frame)
    }

    override suspend fun bindToView(viewEntry: UIComponent.ViewViewEntry<FrameLayout>) {
        val frame = viewEntry.value
        val text = frame.findViewById<TextView>(android.R.id.message)
        coroutineScope {
            launch {
                viewModel.state.collectLatest { state ->
                    frame.visibility = if (state.visible) View.VISIBLE else View.GONE
                    text.text = state.message
                }
            }
        }
    }
}
