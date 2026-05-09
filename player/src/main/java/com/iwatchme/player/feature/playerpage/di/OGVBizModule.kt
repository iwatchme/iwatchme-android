package com.iwatchme.player.feature.playerpage.di

import com.iwatchme.player.core.di.BizScope
import com.iwatchme.player.feature.playerpage.biz.BizInfoService
import com.iwatchme.player.feature.playerpage.biz.OGVSeasonService
import com.iwatchme.player.model.DetailData
import com.iwatchme.player.model.OGVDetail
import dagger.Binds
import dagger.Module
import dagger.Provides

/**
 * OGVBizComponent 专属 module。两件事：
 *  1. [provideDetailData]：把 BindsInstance 进来的 OGVDetail 同时绑成 DetailData；
 *  2. [bindBizInfoService]：OGV 域里 BizInfoService = OGVSeasonService。
 */
@Module
abstract class OGVBizModule {

    @Binds
    @BizScope
    abstract fun bindBizInfoService(impl: OGVSeasonService): BizInfoService

    companion object {
        @Provides
        @BizScope
        fun provideDetailData(detail: OGVDetail): DetailData = detail
    }
}
