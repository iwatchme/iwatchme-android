package com.iwatchme.player.feature.playerpage.di

import com.iwatchme.player.core.di.BizCoroutineScope
import com.iwatchme.player.core.di.BizScope
import com.iwatchme.player.feature.playerpage.biz.di.OGVSeasonBannerParser
import com.iwatchme.player.feature.playerpage.biz.di.VideoListModuleParser
import com.iwatchme.player.model.OGVDetail
import dagger.BindsInstance
import dagger.Subcomponent
import kotlinx.coroutines.CoroutineScope

/**
 * OGV 业务 Subcomponent。与 [UGCBizComponent] 平行，差异由 [OGVBizModule] + [OGVSeasonBannerParser] 注入。
 */
@BizScope
@Subcomponent(
    modules = [
        BizScopeModule::class,
        BizSubcomponentsModule::class,
        VideoListModuleParser::class,        // 共享：跟 UGCBizComponent 引用同一个 object
        OGVSeasonBannerParser::class,        // 私有：仅 OGV 域可见
        OGVBizModule::class,
    ],
)
interface OGVBizComponent : PlayerBizFacade {

    @Subcomponent.Factory
    interface Factory {
        fun create(
            @BindsInstance @BizCoroutineScope scope: CoroutineScope,
            @BindsInstance detail: OGVDetail,
        ): OGVBizComponent
    }
}
