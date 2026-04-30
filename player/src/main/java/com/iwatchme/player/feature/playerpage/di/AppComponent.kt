package com.iwatchme.player.feature.playerpage.di

import android.content.Context
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AppModule::class,
        AppSubcomponentsModule::class,
    ],
)
interface AppComponent {
    fun playerPageComponentFactory(): PlayerPageComponent.Factory

    @Component.Factory
    interface Factory {
        fun create(@BindsInstance context: Context): AppComponent
    }
}

@dagger.Module(subcomponents = [PlayerPageComponent::class])
object AppSubcomponentsModule
