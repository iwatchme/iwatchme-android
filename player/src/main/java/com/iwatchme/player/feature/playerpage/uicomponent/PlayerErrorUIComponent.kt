package com.iwatchme.player.feature.playerpage.uicomponent

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.iwatchme.player.core.ui.UIComponent
import com.iwatchme.player.model.PlaybackState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine

class PlayerErrorUIComponent(
    private val playbackStateFlow: StateFlow<PlaybackState>,
    private val errorMessageFlow: StateFlow<String?>,
) : UIComponent<UIComponent.ViewViewEntry<FrameLayout>> {

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
            text = "播放出错"
        }

        frame.addView(textView)
        return UIComponent.ViewViewEntry(frame)
    }

    override suspend fun bindToView(viewEntry: UIComponent.ViewViewEntry<FrameLayout>) {
        combine(playbackStateFlow, errorMessageFlow) { state, error ->
            state to error
        }.collectLatest { (state, error) ->
            if (state == PlaybackState.ERROR) {
                viewEntry.value.visibility = View.VISIBLE
                viewEntry.value.findViewById<TextView>(android.R.id.message)?.text =
                    error ?: "播放出错"
            } else {
                viewEntry.value.visibility = View.GONE
            }
        }
    }
}
