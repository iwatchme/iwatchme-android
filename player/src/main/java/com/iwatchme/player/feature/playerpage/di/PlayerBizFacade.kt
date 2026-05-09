package com.iwatchme.player.feature.playerpage.di

import com.iwatchme.player.feature.playerpage.biz.BizBootstrap
import com.iwatchme.player.feature.playerpage.biz.BizInfoService
import com.iwatchme.player.feature.playerpage.biz.BizRecyclerViewService
import com.iwatchme.player.feature.playerpage.biz.CurrentEpisodeComponentRepository
import com.iwatchme.player.feature.playerpage.biz.EpisodeTitleService

/**
 * 跨业务的 BizScope 公共门面。Fragment / PageScope 拿到的就是这个接口而不是具体的
 * UGCBizComponent / OGVBizComponent ——以此保证 PageScope 代码业务无关。
 *
 * 对齐 theseus：那边没有显式 facade，他们的 PageAnchor 直接拿 anchor，业务专属方法走 `as?` 转。
 * 我们用 facade 的方式更直观，跟 Fragment 的对接也只通过它走。
 */
interface PlayerBizFacade {
    fun bootstrap(): BizBootstrap
    fun bizRecyclerViewService(): BizRecyclerViewService
    fun episodeTitleService(): EpisodeTitleService
    fun currentEpisodeComponentRepository(): CurrentEpisodeComponentRepository
    fun bizInfoService(): BizInfoService
}
