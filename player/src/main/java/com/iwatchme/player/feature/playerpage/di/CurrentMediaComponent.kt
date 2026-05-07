package com.iwatchme.player.feature.playerpage.di

import com.iwatchme.player.core.di.MediaCoroutineScope
import com.iwatchme.player.core.di.MediaScope
import com.iwatchme.player.feature.playerpage.media.MediaScopeAnchor
import dagger.BindsInstance
import dagger.Subcomponent
import kotlinx.coroutines.CoroutineScope

@MediaScope
@Subcomponent(
    modules = [
        MediaScopeModule::class,
    ],
)
interface CurrentMediaComponent {

    fun anchor(): MediaScopeAnchor

    @Subcomponent.Factory
    interface Factory {
        fun create(
            @BindsInstance @MediaCoroutineScope scope: CoroutineScope,
        ): CurrentMediaComponent
    }
}
