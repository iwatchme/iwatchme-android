package com.iwatchme.player.feature.playerpage.di

import com.iwatchme.player.core.di.BizScope
import com.iwatchme.player.feature.playerpage.biz.BizInfoService
import com.iwatchme.player.feature.playerpage.biz.UGCInfoService
import com.iwatchme.player.model.DetailData
import com.iwatchme.player.model.UGCDetail
import dagger.Binds
import dagger.Module
import dagger.Provides

/**
 * UGCBizComponent 专属 module。两件事：
 *  1. [provideDetailData]：把 BindsInstance 进来的 UGCDetail 同时绑成 DetailData，让通用 service
 *     （VideoListRepository / BizRecyclerViewService）能注入到抽象类型；
 *  2. [bindBizInfoService]：UGC 域里 BizInfoService = UGCInfoService。
 */
@Module
abstract class UGCBizModule {

    @Binds
    @BizScope
    abstract fun bindBizInfoService(impl: UGCInfoService): BizInfoService

    companion object {
        @Provides
        @BizScope
        fun provideDetailData(detail: UGCDetail): DetailData = detail
    }
}
