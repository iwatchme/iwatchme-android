package com.iwatchme.player.feature.playerpage.uicomponent

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.TextView
import com.iwatchme.player.core.ui.UIComponent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 业务专属信息条 UI。两个 biz scope 提供不同的 [BizInfoService] 实现：
 *  - UGC：UP 主名 + 视频数；
 *  - OGV：seasonId + 集数 + 是否 VIP。
 *
 * UIComponent 本身只关心 [State] 的字符串与色调，业务差异完全在 ViewModel 实现里。
 */
class BizInfoUIComponent(
    private val viewModel: ViewModel,
) : UIComponent<UIComponent.ViewViewEntry<TextView>> {

    interface ViewModel {
        val state: StateFlow<State>
    }

    data class State(val text: String, val tagColor: Int = Color.parseColor("#666666"))

    override fun createViewEntry(
        context: Context,
        parent: ViewGroup?,
    ): UIComponent.ViewViewEntry<TextView> {
        val tv = TextView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            textSize = 12f
        }
        return UIComponent.ViewViewEntry(tv)
    }

    fun wrapExistingView(textView: TextView): UIComponent.ViewViewEntry<TextView> {
        return UIComponent.ViewViewEntry(textView)
    }

    override suspend fun bindToView(viewEntry: UIComponent.ViewViewEntry<TextView>) {
        coroutineScope {
            launch {
                viewModel.state.collectLatest { state ->
                    viewEntry.value.text = state.text
                    viewEntry.value.setTextColor(state.tagColor)
                }
            }
        }
    }
}
