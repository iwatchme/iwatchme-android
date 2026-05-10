package com.iwatchme.player.feature.playerpage.di

import android.content.Context
import android.media.AudioManager
import com.iwatchme.player.core.di.PageScope
import com.iwatchme.player.core.player.ExoPlayerHolder
import com.iwatchme.player.core.player.ExoPlayerHolderImpl
import com.iwatchme.player.core.player.PlayerViewBinder
import com.iwatchme.player.core.player.PlayerViewBinderImpl
import dagger.Module
import dagger.Provides

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

    /**
     * 系统服务，page 内单例即可。亮度调节走 Activity.window.attributes，不在这里 provide。
     */
    @Provides
    @PageScope
    fun provideAudioManager(context: Context): AudioManager {
        return context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
}
