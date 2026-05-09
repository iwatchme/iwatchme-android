package com.iwatchme.player.feature.playerpage.biz

import android.util.Log
import com.iwatchme.player.core.di.BizScope
import javax.inject.Inject

/**
 * BizScope 的 bootstrap：
 *
 *  - [InitialSelectionService] 在第一份 items 到达后挑首集投递 selection；
 *  - [MediaSelectionDispatcher] 把 selection 翻译成对 PageScope 级 MediaScopeDriver 的 switchTo；
 *  - [EpisodeScopeDriver] 监听 MediaScopeDriver 的 currentItemFlow，构造 EpisodeScope；
 *  - [EpisodeTitleService] 暴露 ViewModel 给 EpisodeTitleUIComponent；
 *  - [BizInfoService] 接口注入——UGC/OGV 各自通过 @Binds 提供不同实现，让"业务专属信息" service
 *    在 BizScope 启动时被 Dagger 实例化。这一项是多业务架构的演示位：BizBootstrap 自身代码业务无关，
 *    具体行为由 DI 决定。
 */
@BizScope
class BizBootstrap @Inject constructor(
    private val initialSelectionService: InitialSelectionService,
    private val mediaSelectionDispatcher: MediaSelectionDispatcher,
    private val episodeScopeDriver: EpisodeScopeDriver,
    private val episodeTitleService: EpisodeTitleService,
    private val bizInfoService: BizInfoService,
) {
    fun start() {
        Log.d(
            "Player",
            "[BizBootstrap] start() — BizScope eager services up (info=${bizInfoService.javaClass.simpleName})",
        )
    }
}
