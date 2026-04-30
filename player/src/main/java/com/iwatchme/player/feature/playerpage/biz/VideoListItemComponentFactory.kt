package com.iwatchme.player.feature.playerpage.biz

import com.iwatchme.player.core.di.BizScope
import com.iwatchme.player.feature.playerpage.page.PageDetailRepository
import com.iwatchme.player.feature.playerpage.uicomponent.VideoListItemUIComponent
import com.iwatchme.player.model.VideoItem
import javax.inject.Inject

@BizScope
class VideoListItemComponentFactory @Inject constructor(
    private val selectionRepository: SelectionRepository,
    private val pageDetailRepository: PageDetailRepository,
) {
    fun create(item: VideoItem): VideoListItemUIComponent {
        return VideoListItemUIComponent(
            item = item,
            selectedIdFlow = selectionRepository.selectedItemIdFlow,
            onClick = {
                pageDetailRepository.loadForItem(item.id)
            },
        )
    }
}
