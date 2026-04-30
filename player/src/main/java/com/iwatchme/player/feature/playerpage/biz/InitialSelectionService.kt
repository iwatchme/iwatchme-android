package com.iwatchme.player.feature.playerpage.biz

import android.util.Log
import com.iwatchme.player.core.di.BizCoroutineScope
import com.iwatchme.player.core.di.BizScope
import com.iwatchme.player.feature.playerpage.page.PlayRequestRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@BizScope
class InitialSelectionService @Inject constructor(
    @BizCoroutineScope scope: CoroutineScope,
    private val videoListRepository: VideoListRepository,
    private val selectionRepository: SelectionRepository,
    private val playRequestRepository: PlayRequestRepository,
) {
    init {
        scope.launch {
            val items = videoListRepository.itemsFlow
                .filter { it.isNotEmpty() }
                .first()
            val firstItem = items.first()
            Log.d("Player", "[InitialSelection] Auto-selecting first item: id=${firstItem.id}, title=${firstItem.title}")
            selectionRepository.select(firstItem.id)
            playRequestRepository.requestPlay(firstItem)
        }
    }
}
