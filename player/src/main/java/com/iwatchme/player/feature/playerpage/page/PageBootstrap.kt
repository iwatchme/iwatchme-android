package com.iwatchme.player.feature.playerpage.page

import android.util.Log
import com.iwatchme.player.core.di.PageScope
import com.iwatchme.player.feature.playerpage.mock.MockData
import javax.inject.Inject

/**
 * PageScope 的 bootstrap：负责把所有 eager 的 driver / service 拉起来（构造期就要订阅 flow 的那些
 * 必须出现在这里，否则不会被 Dagger 实例化），并通过 [start] 把首屏 startParams 投递给
 * [BizScopeDriver]。
 */
@PageScope
class PageBootstrap @Inject constructor(
    private val bizScopeDriver: BizScopeDriver,
    @Suppress("unused") private val mediaScopeDriver: MediaScopeDriver,
    private val detailTitleService: DetailTitleService,
    private val playerLoadingService: PlayerLoadingService,
    private val playerErrorService: PlayerErrorService,
) {
    fun start() {
        Log.d("Player", "[PageBootstrap] start() — kicking off initial switchToNewVideo")
        bizScopeDriver.switchToNewVideo(BizScopeDriver.StartParams(bvid = MockData.initialBvid))
    }
}
