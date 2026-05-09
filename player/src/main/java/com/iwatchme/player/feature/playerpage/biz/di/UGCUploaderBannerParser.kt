package com.iwatchme.player.feature.playerpage.biz.di

import com.iwatchme.player.feature.playerpage.biz.UGCUploaderBannerService
import com.iwatchme.player.model.BizModuleType
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap

/**
 * UGC 私有 parser——**只挂在 UGCBizComponent.modules 里**。
 * OGVBizComponent 的 DI 图里没有这一项，所以 OGV 域的 modules 列表里就算误写
 * `BizModuleType.UGC_UPLOADER_BANNER`，也只会因为 mapper 不存在被静默跳过（参考 BizScopeModule
 * 里的 mappers[module.type]?.apply）。
 */
@Module
object UGCUploaderBannerParser {

    @Provides
    @IntoMap
    @BizModuleTypeKey(BizModuleType.UGC_UPLOADER_BANNER)
    fun parser(
        service: UGCUploaderBannerService,
    ): BizModuleMapper = BizModuleMapper {
        emit(service.create())
    }
}
