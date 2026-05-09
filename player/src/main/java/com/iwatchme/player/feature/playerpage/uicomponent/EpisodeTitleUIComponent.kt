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

class EpisodeTitleUIComponent(
    private val viewModel: ViewModel,
) : UIComponent<UIComponent.ViewViewEntry<TextView>> {

    interface ViewModel {
        val state: StateFlow<State>
    }
    data class State(val text: String, val visible: Boolean = true)

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
        coroutineScope {
            launch {
                viewModel.state.collectLatest { state ->
                    viewEntry.value.text = state.text
                    viewEntry.value.isVisible = state.visible
                }
            }
        }
    }
}
