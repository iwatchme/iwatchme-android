package com.iwatchme.player.feature.playerpage.biz

import android.util.Log
import com.iwatchme.player.core.di.BizCoroutineScope
import com.iwatchme.player.core.di.BizScope
import com.iwatchme.player.feature.playerpage.di.CurrentEpisodeComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@BizScope
class EpisodeScopeDriver @Inject constructor(
    @BizCoroutineScope private val bizScope: CoroutineScope,
    private val selectionRepository: SelectionRepository,
    private val episodeComponentFactory: CurrentEpisodeComponent.Factory,
    private val episodeComponentBridge: EpisodeComponentBridge,
) {
    init {
        bizScope.launch {
            selectionRepository.selectedItemFlow
                .filterNotNull()
                .distinctUntilChangedBy { it.id }
                .collectLatest { item ->
                    Log.d("Player", "[EpisodeScopeDriver] >>> EpisodeScope CREATING for item=${item.title}")
                    coroutineScope {
                        val component = episodeComponentFactory.create(
                            scope = this,
                            item = item,
                        )
                        episodeComponentBridge.update(component)
                        component.anchor().start()
                        Log.d("Player", "[EpisodeScopeDriver] EpisodeScope started, awaiting cancellation...")
                        try {
                            awaitCancellation()
                        } finally {
                            episodeComponentBridge.update(null)
                            Log.d("Player", "[EpisodeScopeDriver] <<< EpisodeScope DESTROYED for item=${item.title}")
                        }
                    }
                }
        }
    }
}
