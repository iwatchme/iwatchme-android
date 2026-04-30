package com.iwatchme.player.feature.playerpage.page

import android.util.Log
import com.iwatchme.player.core.di.PageCoroutineScope
import com.iwatchme.player.core.di.PageScope
import com.iwatchme.player.feature.playerpage.di.PlayerBizComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@PageScope
class BizScopeDriver @Inject constructor(
    @PageCoroutineScope private val pageScope: CoroutineScope,
    private val pageDetailRepository: PageDetailRepository,
    private val bizComponentFactory: PlayerBizComponent.Factory,
    private val bizComponentBridge: BizComponentBridge,
) {
    init {
        pageScope.launch {
            pageDetailRepository.detailFlow
                .filterNotNull()
                .collectLatest { detail ->
                    Log.d("Player", "[BizScopeDriver] >>> BizScope CREATING for detail: bvid=${detail.bvid}")
                    coroutineScope {
                        val component = bizComponentFactory.create(
                            scope = this,
                            detail = detail,
                        )
                        bizComponentBridge.update(component)
                        component.anchor().start()
                        Log.d("Player", "[BizScopeDriver] BizScope started, awaiting cancellation...")
                        try {
                            awaitCancellation()
                        } finally {
                            bizComponentBridge.update(null)
                            Log.d("Player", "[BizScopeDriver] <<< BizScope DESTROYED for detail: bvid=${detail.bvid}")
                        }
                    }
                }
        }
    }
}
