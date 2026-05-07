package com.iwatchme.player.feature.playerpage.di

import com.iwatchme.player.core.di.EpisodeCoroutineScope
import com.iwatchme.player.core.di.EpisodeScope
import com.iwatchme.player.feature.playerpage.episode.EpisodeScopeAnchor
import com.iwatchme.player.model.VideoItem
import dagger.BindsInstance
import dagger.Subcomponent
import kotlinx.coroutines.CoroutineScope

@EpisodeScope
@Subcomponent(
    modules = [
        EpisodeScopeModule::class,
        EpisodeSubcomponentsModule::class,
    ],
)
interface CurrentEpisodeComponent {

    fun anchor(): EpisodeScopeAnchor

    @Subcomponent.Factory
    interface Factory {
        fun create(
            @BindsInstance @EpisodeCoroutineScope scope: CoroutineScope,
            @BindsInstance item: VideoItem,
        ): CurrentEpisodeComponent
    }
}
