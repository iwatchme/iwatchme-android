package com.iwatchme.player.feature.playerpage.uicomponent

import android.content.Context
import android.view.ViewGroup
import android.widget.TextView
import com.iwatchme.player.core.ui.UIComponent
import com.iwatchme.player.model.VideoItem
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

class EpisodeTitleUIComponent(
    private val episodeFlow: StateFlow<VideoItem?>,
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
            setTextColor(0xFFFB7299.toInt())
            textSize = 13f
        }
        return UIComponent.ViewViewEntry(textView)
    }

    fun wrapExistingView(textView: TextView): UIComponent.ViewViewEntry<TextView> {
        return UIComponent.ViewViewEntry(textView)
    }

    override suspend fun bindToView(viewEntry: UIComponent.ViewViewEntry<TextView>) {
        episodeFlow.collectLatest { item ->
            viewEntry.value.text = if (item != null) "正在播放：${item.title}" else ""
        }
    }
}
