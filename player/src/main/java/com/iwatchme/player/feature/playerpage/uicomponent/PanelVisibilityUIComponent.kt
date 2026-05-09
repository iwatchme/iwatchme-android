package com.iwatchme.player.feature.playerpage.uicomponent

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.iwatchme.player.core.ui.UIComponent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 通用面板可见性 UIComponent。包装一个**已存在**的 View（如 `binding.videoList`），仅根据
 * ViewModel 的 visible flag 切换 view.isVisible。
 *
 * 用法：Fragment 创建后调 wrapExistingView(view) 拿 entry，再 bindToView。本 component **不**
 * 自己 createViewEntry —— 它的目的就是给一个无法用 UIComponent 创建的 view（比如 RecyclerView
 * 这种被外部 setup 过 adapter 的）加可见性绑定。
 */
class PanelVisibilityUIComponent(
    private val viewModel: ViewModel,
) : UIComponent<UIComponent.ViewViewEntry<View>> {

    interface ViewModel {
        val visibleFlow: StateFlow<Boolean>
    }

    override fun createViewEntry(
        context: Context,
        parent: ViewGroup?,
    ): UIComponent.ViewViewEntry<View> {
        // 本 component 总是包外部 view；createViewEntry 仅作占位实现。
        error("PanelVisibilityUIComponent only supports wrapExistingView; do not call createViewEntry.")
    }

    fun wrapExistingView(view: View): UIComponent.ViewViewEntry<View> {
        return UIComponent.ViewViewEntry(view)
    }

    override suspend fun bindToView(viewEntry: UIComponent.ViewViewEntry<View>) {
        val view = viewEntry.value
        coroutineScope {
            launch {
                viewModel.visibleFlow.collectLatest { visible ->
                    view.isVisible = visible
                }
            }
        }
    }
}
