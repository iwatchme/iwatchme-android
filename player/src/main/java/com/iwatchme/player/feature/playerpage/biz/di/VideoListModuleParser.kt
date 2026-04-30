package com.iwatchme.player.feature.playerpage.biz.di

import com.iwatchme.player.core.ui.RunningUIComponent
import com.iwatchme.player.feature.playerpage.biz.VideoListItemComponentFactory
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
        factory: VideoListItemComponentFactory,
    ): BizModuleMapper = BizModuleMapper {
        videoListRepository.itemsFlow.value.forEach { item ->
            emit(RunningUIComponent(factory.create(item)))
        }
    }
}
