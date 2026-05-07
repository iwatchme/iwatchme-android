package com.iwatchme.player.feature.playerpage.di

import com.iwatchme.player.core.di.BizCoroutineScope
import com.iwatchme.player.core.di.BizScope
import com.iwatchme.player.feature.playerpage.biz.BizRecyclerViewService
import com.iwatchme.player.feature.playerpage.biz.BizScopeAnchor
import com.iwatchme.player.feature.playerpage.biz.EpisodeComponentBridge
import com.iwatchme.player.feature.playerpage.biz.SelectionRepository
import com.iwatchme.player.feature.playerpage.biz.di.VideoListModuleParser
import com.iwatchme.player.model.DetailData
import dagger.BindsInstance
import dagger.Subcomponent
import kotlinx.coroutines.CoroutineScope

@BizScope
@Subcomponent(
    modules = [
        BizScopeModule::class,
        BizSubcomponentsModule::class,
        VideoListModuleParser::class,
    ],
)
interface PlayerBizComponent {

    fun bizRecyclerViewService(): BizRecyclerViewService

    fun selectionRepository(): SelectionRepository

    fun episodeComponentBridge(): EpisodeComponentBridge

    fun anchor(): BizScopeAnchor

    @Subcomponent.Factory
    interface Factory {
        fun create(
            @BindsInstance @BizCoroutineScope scope: CoroutineScope,
            @BindsInstance detail: DetailData,
        ): PlayerBizComponent
    }
}

@dagger.Module(subcomponents = [CurrentEpisodeComponent::class])
object BizSubcomponentsModule
