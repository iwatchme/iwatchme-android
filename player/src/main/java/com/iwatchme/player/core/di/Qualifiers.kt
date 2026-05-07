package com.iwatchme.player.core.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class PageCoroutineScope

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class BizCoroutineScope

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class EpisodeCoroutineScope

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class MediaCoroutineScope
