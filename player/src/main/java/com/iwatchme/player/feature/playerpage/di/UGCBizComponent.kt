package com.iwatchme.player.feature.playerpage.di

import com.iwatchme.player.core.di.BizCoroutineScope
import com.iwatchme.player.core.di.BizScope
import com.iwatchme.player.feature.playerpage.biz.di.UGCUploaderBannerParser
import com.iwatchme.player.feature.playerpage.biz.di.VideoListModuleParser
import com.iwatchme.player.model.UGCDetail
import dagger.BindsInstance
import dagger.Subcomponent
import kotlinx.coroutines.CoroutineScope

/**
 * UGC 业务 Subcomponent——对齐 theseus 的 UGCVideoComponent。
 * 与 [OGVBizComponent] 共用 `@BizScope`、共用 [BizScopeModule] / [BizSubcomponentsModule]
 * / [VideoListModuleParser] 等业务无关 module，差异由 [UGCBizModule] 注入。
 */
@BizScope
@Subcomponent(
    modules = [
        BizScopeModule::class,
        BizSubcomponentsModule::class,
        VideoListModuleParser::class,        // 共享：UGC / OGV 都用
        UGCUploaderBannerParser::class,      // 私有：仅 UGC 域可见
        UGCBizModule::class,
    ],
)
interface UGCBizComponent : PlayerBizFacade {

    @Subcomponent.Factory
    interface Factory {
        fun create(
            @BindsInstance @BizCoroutineScope scope: CoroutineScope,
            @BindsInstance detail: UGCDetail,
        ): UGCBizComponent
    }
}
