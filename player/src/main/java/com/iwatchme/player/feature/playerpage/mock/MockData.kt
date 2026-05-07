package com.iwatchme.player.feature.playerpage.mock

import com.iwatchme.player.model.DetailData
import com.iwatchme.player.model.SampleVideo
import com.iwatchme.player.model.VideoItem

object MockData {

    private fun item(id: String, cid: Long, sample: SampleVideo) = VideoItem(
        id = id,
        cid = cid,
        title = sample.displayName,
        mediaUrl = sample.url,
        durationMs = sample.durationMs,
    )

    private val detailA = DetailData(
        bvid = "BV1mock001",
        title = "教学示例片合集",
        coverUrl = null,
        items = listOf(
            item("a1", 1001, SampleVideo.RUNOOB_BBB),
            item("a2", 1002, SampleVideo.RUNOOB_MOVIE),
            item("a3", 1003, SampleVideo.W3SCHOOL_BBB),
        ),
    )

    private val detailB = DetailData(
        bvid = "BV1mock002",
        title = "浙江广电 · 综合频道",
        coverUrl = null,
        items = listOf(
            item("b1", 2001, SampleVideo.CZTV_CH1),
            item("b2", 2002, SampleVideo.CZTV_CH2),
            item("b3", 2003, SampleVideo.CZTV_CH3),
            item("b4", 2004, SampleVideo.CZTV_CH4),
        ),
    )

    private val detailC = DetailData(
        bvid = "BV1mock003",
        title = "浙江广电 · 专题频道",
        coverUrl = null,
        items = listOf(
            item("c1", 3001, SampleVideo.CZTV_CH5),
            item("c2", 3002, SampleVideo.CZTV_CH6),
            item("c3", 3003, SampleVideo.CZTV_CH7),
        ),
    )

    private val detailD = DetailData(
        bvid = "BV1mock004",
        title = "混合源演示",
        coverUrl = null,
        items = listOf(
            item("d1", 4001, SampleVideo.RUNOOB_BBB),
            item("d2", 4002, SampleVideo.CZTV_CH8),
            item("d3", 4003, SampleVideo.RUNOOB_MOVIE),
            item("d4", 4004, SampleVideo.CZTV_CH9),
            item("d5", 4005, SampleVideo.CZTV_CH10),
        ),
    )

    private val orderedDetails = listOf(detailA, detailB, detailC, detailD)

    val initialDetail: DetailData = detailA

    fun nextDetailAfter(current: DetailData): DetailData {
        val idx = orderedDetails.indexOfFirst { it.bvid == current.bvid }
        val nextIdx = if (idx < 0) 0 else (idx + 1) % orderedDetails.size
        return orderedDetails[nextIdx]
    }
}
