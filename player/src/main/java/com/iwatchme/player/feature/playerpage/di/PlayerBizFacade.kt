package com.iwatchme.player.feature.playerpage.di

import com.iwatchme.player.feature.playerpage.biz.BizBootstrap
import com.iwatchme.player.feature.playerpage.biz.BizInfoService
import com.iwatchme.player.feature.playerpage.biz.BizRecyclerViewService
import com.iwatchme.player.feature.playerpage.biz.CurrentEpisodeComponentRepository
import com.iwatchme.player.feature.playerpage.biz.EpisodeTitleService
import com.iwatchme.player.feature.playerpage.biz.VideoListPanelService

/**
 * 跨业务的 BizScope 公共门面。Fragment / PageScope 拿到的就是这个接口而不是具体的
 * UGCBizComponent / OGVBizComponent ——以此保证 PageScope 代码业务无关，UGC/OGV 差异完全收敛
 * 在各自 BizScope 内部。
 */
interface PlayerBizFacade {
    fun bootstrap(): BizBootstrap
    fun bizRecyclerViewService(): BizRecyclerViewService
    fun episodeTitleService(): EpisodeTitleService
    fun currentEpisodeComponentRepository(): CurrentEpisodeComponentRepository
    fun bizInfoService(): BizInfoService
    fun videoListPanelService(): VideoListPanelService
}
