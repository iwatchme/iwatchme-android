package com.iwatchme.player.feature.playerpage.uicomponent

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.iwatchme.player.core.ui.UIComponent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// 只支持 wrapExistingView：SeekBar/timeText 与 fullscreen 按钮共处一行，由 layout 提供
class PlaybackProgressUIComponent(
    private val viewModel: ViewModel,
) : UIComponent<PlaybackProgressUIComponent.ViewEntry> {

    interface ViewModel {
        val state: StateFlow<State>
        fun onSeekStart()
        fun onSeekStop(positionMs: Long)
    }

    data class State(
        val positionMs: Long,
        val bufferedMs: Long,
        val durationMs: Long,
        val visible: Boolean,
    )

    class ViewEntry(
        val seekBar: SeekBar,
        val timeText: TextView,
    ) : UIComponent.ViewEntry {
        override val root: View get() = seekBar
    }

    override fun createViewEntry(context: Context, parent: ViewGroup?): ViewEntry {
        error("PlaybackProgressUIComponent 仅支持 wrapExistingViews")
    }

    fun wrapExistingViews(seekBar: SeekBar, timeText: TextView): ViewEntry =
        ViewEntry(seekBar, timeText)

    override suspend fun bindToView(viewEntry: ViewEntry) {
        val seekBar = viewEntry.seekBar
        val timeText = viewEntry.timeText

        var lastKnownDurationMs = 0L

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    timeText.text = "${formatTime(progress.toLong())} / ${formatTime(lastKnownDurationMs)}"
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar) {
                viewModel.onSeekStart()
            }

            override fun onStopTrackingTouch(sb: SeekBar) {
                viewModel.onSeekStop(sb.progress.toLong())
            }
        })

        coroutineScope {
            launch {
                viewModel.state.collectLatest { s ->
                    seekBar.isVisible = s.visible
                    timeText.isVisible = s.visible
                    if (!s.visible) return@collectLatest
                    lastKnownDurationMs = s.durationMs
                    seekBar.max = s.durationMs.toInt().coerceAtLeast(1)
                    seekBar.secondaryProgress = s.bufferedMs.toInt()
                    seekBar.progress = s.positionMs.toInt()
                    timeText.text = "${formatTime(s.positionMs)} / ${formatTime(s.durationMs)}"
                }
            }
        }
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return "%02d:%02d".format(m, s)
    }
}
