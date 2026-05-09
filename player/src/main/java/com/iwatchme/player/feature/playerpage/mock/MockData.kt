package com.iwatchme.player.feature.playerpage.mock

import com.iwatchme.player.model.DetailData
import com.iwatchme.player.model.OGVDetail
import com.iwatchme.player.model.SampleVideo
import com.iwatchme.player.model.UGCDetail
import com.iwatchme.player.model.VideoItem

object MockData {

    private fun item(id: String, cid: Long, sample: SampleVideo) = VideoItem(
        id = id,
        cid = cid,
        title = sample.displayName,
        mediaUrl = sample.url,
        durationMs = sample.durationMs,
    )

    // UGC 合集：教学示例
    private val ugcA = UGCDetail(
        bvid = "BV1ugc001",
        title = "教学示例片合集",
        items = listOf(
            item("a1", 1001, SampleVideo.RUNOOB_BBB),
            item("a2", 1002, SampleVideo.RUNOOB_MOVIE),
            item("a3", 1003, SampleVideo.W3SCHOOL_BBB),
        ),
        uploaderName = "教程菌",
        uploaderAvatarUrl = null,
    )

    // OGV 合集：浙江综合频道（用 OGV 类型展示季度信息）
    private val ogvB = OGVDetail(
        bvid = "BV1ogv002",
        title = "浙江广电 · 综合频道",
        items = listOf(
            item("b1", 2001, SampleVideo.CZTV_CH1),
            item("b2", 2002, SampleVideo.CZTV_CH2),
            item("b3", 2003, SampleVideo.CZTV_CH3),
            item("b4", 2004, SampleVideo.CZTV_CH4),
        ),
        seasonId = 33550336L,
        totalEpisodes = 4,
        vipOnly = false,
    )

    // OGV 合集：专题频道（VIP 限定）
    private val ogvC = OGVDetail(
        bvid = "BV1ogv003",
        title = "浙江广电 · 专题频道",
        items = listOf(
            item("c1", 3001, SampleVideo.CZTV_CH5),
            item("c2", 3002, SampleVideo.CZTV_CH6),
            item("c3", 3003, SampleVideo.CZTV_CH7),
        ),
        seasonId = 99887766L,
        totalEpisodes = 3,
        vipOnly = true,
    )

    // UGC 合集：混合源
    private val ugcD = UGCDetail(
        bvid = "BV1ugc004",
        title = "混合源演示",
        items = listOf(
            item("d1", 4001, SampleVideo.RUNOOB_BBB),
            item("d2", 4002, SampleVideo.CZTV_CH8),
            item("d3", 4003, SampleVideo.RUNOOB_MOVIE),
            item("d4", 4004, SampleVideo.CZTV_CH9),
            item("d5", 4005, SampleVideo.CZTV_CH10),
        ),
        uploaderName = "混剪 UP 主",
        uploaderAvatarUrl = null,
    )

    // 顺序混排：UGC → OGV → OGV → UGC，让用户多次点击就能看到两种 biz 切换
    private val orderedDetails: List<DetailData> = listOf(ugcA, ogvB, ogvC, ugcD)

    val initialBvid: String = ugcA.bvid

    fun findByBvid(bvid: String): DetailData? = orderedDetails.firstOrNull { it.bvid == bvid }

    fun nextDetailAfter(current: DetailData): DetailData {
        val idx = orderedDetails.indexOfFirst { it.bvid == current.bvid }
        val nextIdx = if (idx < 0) 0 else (idx + 1) % orderedDetails.size
        return orderedDetails[nextIdx]
    }
}
