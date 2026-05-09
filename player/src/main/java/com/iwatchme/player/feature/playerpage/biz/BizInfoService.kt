package com.iwatchme.player.feature.playerpage.biz

import com.iwatchme.player.feature.playerpage.uicomponent.BizInfoUIComponent

/**
 * 业务专属信息 service 的统一接口。BizScope 内一份具体实现：
 *
 *  - [UGCInfoService]——注入 UGCDetail，输出"UP 主：xxx | N 个视频"
 *  - [OGVSeasonService]——注入 OGVDetail，输出"季 ID:xxx | 共 N 集 | 大会员限定"
 *
 * 两份实现通过 @Binds 在各自 biz subcomponent 的 module 里绑成 BizInfoService，于是
 * Fragment 在 PlayerBizFacade 上只看见 `bizInfoService(): BizInfoService`，业务差异
 * 完全收敛在 DI 图里。这就是 theseus 的"interface + DI 多实现"。
 */
interface BizInfoService {
    val viewModel: BizInfoUIComponent.ViewModel
}
