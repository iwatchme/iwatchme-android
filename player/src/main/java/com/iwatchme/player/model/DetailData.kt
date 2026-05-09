package com.iwatchme.player.model

/**
 * 详情数据——sealed interface，每种业务带不同的业务专属字段，
 * 由 [BizScopeDriver] 在 driveBusinessScope 时按 sealed 类型分发到不同的 BizScope subcomponent。
 */
sealed interface DetailData {
    val bvid: String
    val title: String
    val coverUrl: String?
    val items: List<VideoItem>
    val modules: List<BizModule>
    val businessType: BusinessType
}

data class UGCDetail(
    override val bvid: String,
    override val title: String,
    override val coverUrl: String? = null,
    override val items: List<VideoItem>,
    override val modules: List<BizModule> = listOf(
        BizModule(BizModuleType.UGC_UPLOADER_BANNER),  // UGC 私有 module
        BizModule(BizModuleType.VIDEO_LIST),           // 共享 module
    ),
    /** UGC 业务专属：UP 主名 */
    val uploaderName: String,
    val uploaderAvatarUrl: String? = null,
) : DetailData {
    override val businessType: BusinessType get() = BusinessType.UGC
}

data class OGVDetail(
    override val bvid: String,
    override val title: String,
    override val coverUrl: String? = null,
    override val items: List<VideoItem>,
    override val modules: List<BizModule> = listOf(
        BizModule(BizModuleType.OGV_SEASON_BANNER),    // OGV 私有 module
        BizModule(BizModuleType.VIDEO_LIST),           // 共享 module
    ),
    /** OGV 业务专属：season id */
    val seasonId: Long,
    val totalEpisodes: Int,
    val vipOnly: Boolean = false,
) : DetailData {
    override val businessType: BusinessType get() = BusinessType.OGV
}
