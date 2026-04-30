package com.iwatchme.player.feature.playerpage.uicomponent

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import com.iwatchme.player.core.ui.UIComponent
import com.iwatchme.player.model.PlaybackState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

class PlayerLoadingUIComponent(
    private val playbackStateFlow: StateFlow<PlaybackState>,
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
            isClickable = false
            isFocusable = false
        }

        val progressBar = ProgressBar(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                dp(context, 48),
                dp(context, 48),
                Gravity.CENTER,
            )
        }

        frame.addView(progressBar)
        return UIComponent.ViewViewEntry(frame)
    }

    override suspend fun bindToView(viewEntry: UIComponent.ViewViewEntry<FrameLayout>) {
        playbackStateFlow.collectLatest { state ->
            viewEntry.value.visibility = if (state == PlaybackState.LOADING) View.VISIBLE else View.GONE
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics,
        ).toInt()
    }
}
