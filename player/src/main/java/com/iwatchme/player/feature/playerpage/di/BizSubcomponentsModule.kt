package com.iwatchme.player.feature.playerpage.di

import dagger.Module

/**
 * BizScope 下的子 component 注册（业务无关）。UGCBizComponent / OGVBizComponent 都引用它。
 */
@Module(subcomponents = [CurrentEpisodeComponent::class])
object BizSubcomponentsModule
