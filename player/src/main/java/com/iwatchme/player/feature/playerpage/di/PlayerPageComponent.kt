package com.iwatchme.player.feature.playerpage.di

import com.iwatchme.player.core.di.PageCoroutineScope
import com.iwatchme.player.core.di.PageScope
import com.iwatchme.player.core.player.ExoPlayerHolder
import com.iwatchme.player.core.player.PlayerViewBinder
import com.iwatchme.player.feature.playerpage.page.BizComponentBridge
import com.iwatchme.player.feature.playerpage.page.PageDetailRepository
import com.iwatchme.player.feature.playerpage.page.PageScopeAnchor
import com.iwatchme.player.feature.playerpage.page.PlayRequestRepository
import com.iwatchme.player.feature.playerpage.page.PlayerUiStateRepository
import dagger.BindsInstance
import dagger.Subcomponent
import kotlinx.coroutines.CoroutineScope

@PageScope
@Subcomponent(
    modules = [
        PageScopeModule::class,
        PageSubcomponentsModule::class,
    ],
)
interface PlayerPageComponent {

    fun exoPlayerHolder(): ExoPlayerHolder

    fun playerViewBinder(): PlayerViewBinder

    fun bizComponentFactory(): PlayerBizComponent.Factory

    fun anchor(): PageScopeAnchor

    fun bizComponentBridge(): BizComponentBridge

    fun pageDetailRepository(): PageDetailRepository

    fun playerUiStateRepository(): PlayerUiStateRepository

    fun playRequestRepository(): PlayRequestRepository

    @Subcomponent.Factory
    interface Factory {
        fun create(
            @BindsInstance @PageCoroutineScope scope: CoroutineScope,
        ): PlayerPageComponent
    }
}

@dagger.Module(subcomponents = [PlayerBizComponent::class, CurrentMediaComponent::class])
object PageSubcomponentsModule
