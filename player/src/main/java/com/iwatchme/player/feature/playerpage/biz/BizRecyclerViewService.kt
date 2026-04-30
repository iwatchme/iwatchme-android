package com.iwatchme.player.feature.playerpage.biz

import android.util.Log
import com.iwatchme.player.core.di.BizCoroutineScope
import com.iwatchme.player.core.di.BizScope
import com.iwatchme.player.core.ui.RunningUIComponent
import com.iwatchme.player.feature.playerpage.biz.di.BizModuleListMapper
import com.iwatchme.player.model.DetailData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@BizScope
class BizRecyclerViewService @Inject constructor(
    @BizCoroutineScope private val scope: CoroutineScope,
    detail: DetailData,
    bizModuleListMapper: BizModuleListMapper,
) {
    val components: List<RunningUIComponent> = bizModuleListMapper.map(detail.modules)

    init {
        Log.d("Player", "[BizRecyclerViewService] ${components.size} components from ${detail.modules.size} modules")
        components.forEach { rc ->
            scope.launch { rc.runUntilCancellation() }
        }
    }
}
