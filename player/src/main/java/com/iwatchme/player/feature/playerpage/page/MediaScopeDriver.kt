package com.iwatchme.player.feature.playerpage.page

import android.util.Log
import com.iwatchme.player.core.di.PageCoroutineScope
import com.iwatchme.player.core.di.PageScope
import com.iwatchme.player.feature.playerpage.di.CurrentMediaComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@PageScope
class MediaScopeDriver @Inject constructor(
    @PageCoroutineScope private val pageScope: CoroutineScope,
    private val playRequestRepository: PlayRequestRepository,
    private val mediaComponentFactory: CurrentMediaComponent.Factory,
) {
    init {
        pageScope.launch {
            playRequestRepository.playRequestFlow
                .filterNotNull()
                .distinctUntilChangedBy { it.id }
                .collectLatest { item ->
                    Log.d("Player", "[MediaScopeDriver] >>> MediaScope CREATING for item: id=${item.id}, title=${item.title}")
                    coroutineScope {
                        val component = mediaComponentFactory.create(
                            scope = this,
                            item = item,
                        )
                        component.anchor().start()
                        Log.d("Player", "[MediaScopeDriver] MediaScope started, awaiting cancellation...")
                        try {
                            awaitCancellation()
                        } finally {
                            Log.d("Player", "[MediaScopeDriver] <<< MediaScope DESTROYED for item: id=${item.id}")
                        }
                    }
                }
        }
    }
}
