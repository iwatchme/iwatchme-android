package com.iwatchme.player.feature.playerpage.episode

import android.util.Log
import com.iwatchme.player.core.di.EpisodeCoroutineScope
import com.iwatchme.player.core.di.EpisodeScope
import com.iwatchme.player.feature.playerpage.di.CurrentMediaComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@EpisodeScope
class MediaScopeDriver @Inject constructor(
    @EpisodeCoroutineScope private val episodeScope: CoroutineScope,
    private val mediaRequestRepository: MediaRequestRepository,
    private val mediaComponentFactory: CurrentMediaComponent.Factory,
) {
    init {
        episodeScope.launch {
            mediaRequestRepository.requestFlow
                .filterNotNull()
                .distinctUntilChangedBy { it.item.id to it.seekMs }
                .collectLatest { request ->
                    Log.d("Player", "[MediaScopeDriver] >>> MediaScope CREATING for item=${request.item.title}")
                    coroutineScope {
                        val component = mediaComponentFactory.create(
                            scope = this,
                        )
                        component.anchor().start()
                        Log.d("Player", "[MediaScopeDriver] MediaScope started, awaiting cancellation...")
                        try {
                            awaitCancellation()
                        } finally {
                            Log.d("Player", "[MediaScopeDriver] <<< MediaScope DESTROYED for item=${request.item.title}")
                        }
                    }
                }
        }
    }
}
