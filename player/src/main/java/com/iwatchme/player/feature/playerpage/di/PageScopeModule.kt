package com.iwatchme.player.feature.playerpage.di

import android.content.Context
import com.iwatchme.player.core.di.PageCoroutineScope
import com.iwatchme.player.core.di.PageScope
import com.iwatchme.player.core.player.ExoPlayerHolder
import com.iwatchme.player.core.player.ExoPlayerHolderImpl
import com.iwatchme.player.core.player.PlayerViewBinder
import com.iwatchme.player.core.player.PlayerViewBinderImpl
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope

@Module
object PageScopeModule {

    @Provides
    @PageScope
    fun provideExoPlayerHolder(context: Context): ExoPlayerHolder {
        return ExoPlayerHolderImpl(context)
    }

    @Provides
    @PageScope
    fun providePlayerViewBinder(holder: ExoPlayerHolder): PlayerViewBinder {
        return PlayerViewBinderImpl(holder)
    }
}
