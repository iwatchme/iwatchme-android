package com.iwatchme.player.feature.playerpage.biz.di

import com.iwatchme.player.feature.playerpage.biz.VideoListItemService
import com.iwatchme.player.feature.playerpage.biz.VideoListRepository
import com.iwatchme.player.model.BizModuleType
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap

@Module
object VideoListModuleParser {

    @Provides
    @IntoMap
    @BizModuleTypeKey(BizModuleType.VIDEO_LIST)
    fun videoList(
        videoListRepository: VideoListRepository,
        videoListItemService: VideoListItemService,
    ): BizModuleMapper = BizModuleMapper {
        videoListRepository.itemsFlow.value.forEach { item ->
            emit(videoListItemService.create(item))
        }
    }
}
