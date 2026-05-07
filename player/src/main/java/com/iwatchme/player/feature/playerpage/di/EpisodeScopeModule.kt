package com.iwatchme.player.feature.playerpage.di

import dagger.Module

@Module
object EpisodeScopeModule

@dagger.Module(subcomponents = [CurrentMediaComponent::class])
object EpisodeSubcomponentsModule
