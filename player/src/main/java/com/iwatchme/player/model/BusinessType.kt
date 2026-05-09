package com.iwatchme.player.model

/**
 * 业务类型——demo 演示 UGC（用户投稿）与 OGV（专业内容季度）两种。需要扩展时按 sealed
 * [DetailData] 子类的形式新增即可。
 */
enum class BusinessType {
    UGC,
    OGV,
}
