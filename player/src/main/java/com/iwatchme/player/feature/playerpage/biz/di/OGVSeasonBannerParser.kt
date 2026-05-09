package com.iwatchme.player.feature.playerpage.biz.di

import com.iwatchme.player.feature.playerpage.biz.OGVSeasonBannerService
import com.iwatchme.player.model.BizModuleType
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap

/**
 * OGV 私有 parser——**只挂在 OGVBizComponent.modules 里**。
 */
@Module
object OGVSeasonBannerParser {

    @Provides
    @IntoMap
    @BizModuleTypeKey(BizModuleType.OGV_SEASON_BANNER)
    fun parser(
        service: OGVSeasonBannerService,
    ): BizModuleMapper = BizModuleMapper {
        emit(service.create())
    }
}
