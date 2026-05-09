package com.iwatchme.player.model

enum class BizModuleType {
    /** 视频列表——UGC / OGV 都有，对应共享 parser。 */
    VIDEO_LIST,
    /** UGC 私有：UP 主信息卡片，只在 UGCBizComponent 的 DI 图里有 parser。 */
    UGC_UPLOADER_BANNER,
    /** OGV 私有：季度信息卡片，只在 OGVBizComponent 的 DI 图里有 parser。 */
    OGV_SEASON_BANNER,
}

data class BizModule(
    val type: BizModuleType,
    val data: Any? = null,
)
