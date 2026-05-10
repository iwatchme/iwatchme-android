package com.iwatchme.player.feature.playerpage.uicomponent

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.iwatchme.player.R
import com.iwatchme.player.core.ui.UIComponent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// 中央 indicator：horizontal LinearLayout = icon + text + 可选 progress bar；不可点击避免拦手势
class PlayerGestureOverlayUIComponent(
    private val viewModel: ViewModel,
) : UIComponent<UIComponent.ViewViewEntry<FrameLayout>> {

    interface ViewModel {
        val state: StateFlow<State>
    }

    sealed interface State {
        object Hidden : State
        data class Seeking(val deltaSec: Int, val positionMs: Long, val durationMs: Long) : State
        data class Volume(val percent: Int) : State
        data class Brightness(val percent: Int) : State
        data class TripleSpeed(val speed: Float) : State
    }

    // 用 tag 索引子 view，避免动态生成 id 冲突
    private object Tags {
        const val INDICATOR = "indicator"
        const val ICON = "icon"
        const val TEXT = "text"
        const val PROGRESS = "progress"
    }

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

        val indicator = LinearLayout(context).apply {
            tag = Tags.INDICATOR
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 12).toFloat()
                setColor(0xCC000000.toInt())
            }
            setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            )
        }

        val icon = ImageView(context).apply {
            tag = Tags.ICON
            layoutParams = LinearLayout.LayoutParams(dp(context, 24), dp(context, 24))
        }
        indicator.addView(icon)

        val text = TextView(context).apply {
            tag = Tags.TEXT
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(context, 12) }
        }
        indicator.addView(text)

        val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            tag = Tags.PROGRESS
            max = 100
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(dp(context, 100), dp(context, 4)).apply {
                marginStart = dp(context, 12)
            }
        }
        indicator.addView(progress)

        frame.addView(indicator)
        return UIComponent.ViewViewEntry(frame)
    }

    override suspend fun bindToView(viewEntry: UIComponent.ViewViewEntry<FrameLayout>) {
        val frame = viewEntry.value
        val indicator = frame.findViewWithTag<LinearLayout>(Tags.INDICATOR)
        val icon = indicator.findViewWithTag<ImageView>(Tags.ICON)
        val text = indicator.findViewWithTag<TextView>(Tags.TEXT)
        val progress = indicator.findViewWithTag<ProgressBar>(Tags.PROGRESS)
        coroutineScope {
            launch {
                viewModel.state.collectLatest { state ->
                    when (state) {
                        is State.Hidden -> indicator.visibility = View.GONE
                        is State.Volume -> {
                            indicator.visibility = View.VISIBLE
                            icon.setImageResource(R.drawable.ic_gesture_volume)
                            icon.visibility = View.VISIBLE
                            text.text = "${state.percent}%"
                            progress.visibility = View.VISIBLE
                            progress.progress = state.percent
                        }
                        is State.Brightness -> {
                            indicator.visibility = View.VISIBLE
                            icon.setImageResource(R.drawable.ic_gesture_brightness)
                            icon.visibility = View.VISIBLE
                            text.text = "${state.percent}%"
                            progress.visibility = View.VISIBLE
                            progress.progress = state.percent
                        }
                        is State.Seeking -> {
                            indicator.visibility = View.VISIBLE
                            icon.setImageResource(
                                if (state.deltaSec >= 0) R.drawable.ic_gesture_seek_forward
                                else R.drawable.ic_gesture_seek_back,
                            )
                            icon.visibility = View.VISIBLE
                            text.text = formatSeeking(state)
                            progress.visibility = View.GONE
                        }
                        is State.TripleSpeed -> {
                            indicator.visibility = View.VISIBLE
                            icon.setImageResource(R.drawable.ic_gesture_speed)
                            icon.visibility = View.VISIBLE
                            text.text = "${formatSpeed(state.speed)}x 倍速"
                            progress.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun formatSeeking(s: State.Seeking): String {
        val sign = if (s.deltaSec >= 0) "+" else ""
        return "${sign}${s.deltaSec}s\n${formatTime(s.positionMs)} / ${formatTime(s.durationMs)}"
    }

    private fun formatTime(ms: Long): String {
        if (ms < 0) return "00:00"
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return "%02d:%02d".format(m, s)
    }

    private fun formatSpeed(speed: Float): String =
        if (speed % 1f == 0f) speed.toInt().toString() else "%.1f".format(speed)

    private fun dp(context: Context, value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        context.resources.displayMetrics,
    ).toInt()
}
