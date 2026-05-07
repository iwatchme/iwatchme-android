package com.iwatchme.player.core.di

import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class PageScope

@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class BizScope

@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class EpisodeScope

@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class MediaScope
