package com.iwatchme.player.feature.playerpage.di

import androidx.activity.ComponentActivity
import com.iwatchme.player.core.di.PageCoroutineScope
import com.iwatchme.player.core.di.PageScope
import com.iwatchme.player.core.player.ExoPlayerHolder
import com.iwatchme.player.core.player.PlayerViewBinder
import com.iwatchme.player.feature.playerpage.page.CurrentBizComponentRepository
import com.iwatchme.player.feature.playerpage.page.DetailTitleService
import com.iwatchme.player.feature.playerpage.page.MediaScopeDriver
import com.iwatchme.player.feature.playerpage.page.PageBootstrap
import com.iwatchme.player.feature.playerpage.page.PlayerErrorService
import com.iwatchme.player.feature.playerpage.page.PlayerLoadingService
import com.iwatchme.player.feature.playerpage.page.ScreenStateRepository
import com.iwatchme.player.feature.playerpage.page.ScreenStateService
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

    fun bootstrap(): PageBootstrap

    fun currentBizComponentRepository(): CurrentBizComponentRepository

    fun detailTitleService(): DetailTitleService

    fun playerLoadingService(): PlayerLoadingService

    fun playerErrorService(): PlayerErrorService

    fun mediaScopeDriver(): MediaScopeDriver

    fun screenStateRepository(): ScreenStateRepository

    fun screenStateService(): ScreenStateService

    @Subcomponent.Factory
    interface Factory {
        fun create(
            @BindsInstance @PageCoroutineScope scope: CoroutineScope,
            @BindsInstance activity: ComponentActivity,
        ): PlayerPageComponent
    }
}

@dagger.Module(
    subcomponents = [
        UGCBizComponent::class,
        OGVBizComponent::class,
        CurrentMediaComponent::class,
    ],
)
object PageSubcomponentsModule
