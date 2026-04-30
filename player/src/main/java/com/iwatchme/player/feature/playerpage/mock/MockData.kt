package com.iwatchme.player.feature.playerpage.mock

import com.iwatchme.player.model.DetailData
import com.iwatchme.player.model.PlaybackInfo
import com.iwatchme.player.model.VideoItem

object MockData {

    private val url1 = "https://media.w3.org/2010/05/sintel/trailer.mp4"
    private val url2 = "https://media.w3.org/2010/05/bunny/trailer.mp4"
    private val url3 = "https://media.w3.org/2010/05/bunny/movie.mp4"
    private val url4 = "https://media.w3.org/2010/05/video/movie_300.mp4"

    // 三份详情，互相引用形成循环
    private val detailA = DetailData(
        bvid = "BV1mock001",
        title = "Kotlin 进阶教程",
        coverUrl = null,
        items = listOf(
            VideoItem(id = "a1", cid = 1001, title = "第1P - Kotlin 基础", durationMs = 180_000),
            VideoItem(id = "a2", cid = 1002, title = "第2P - Coroutine 入门", durationMs = 240_000),
            VideoItem(id = "a3", cid = 1003, title = "第3P - Flow 实战", durationMs = 300_000),
        ),
    )

    private val detailB = DetailData(
        bvid = "BV1mock002",
        title = "Android 架构实战",
        coverUrl = null,
        items = listOf(
            VideoItem(id = "b1", cid = 2001, title = "第1P - Dagger 2 详解", durationMs = 200_000),
            VideoItem(id = "b2", cid = 2002, title = "第2P - Scope 生命周期", durationMs = 260_000),
            VideoItem(id = "b3", cid = 2003, title = "第3P - UIComponent 模式", durationMs = 220_000),
            VideoItem(id = "b4", cid = 2004, title = "第4P - 多层 Scope 协作", durationMs = 280_000),
        ),
    )

    private val detailC = DetailData(
        bvid = "BV1mock003",
        title = "ExoPlayer 从入门到精通",
        coverUrl = null,
        items = listOf(
            VideoItem(id = "c1", cid = 3001, title = "第1P - 播放器初始化", durationMs = 150_000),
            VideoItem(id = "c2", cid = 3002, title = "第2P - MediaItem 与 Source", durationMs = 190_000),
        ),
    )

    val initialDetail = detailA

    // 每个 item 点击后跳到哪个详情：a→B, b→C, c→A 形成循环
    private val detailMap = mapOf(
        "a1" to detailB, "a2" to detailB, "a3" to detailB,
        "b1" to detailC, "b2" to detailC, "b3" to detailC, "b4" to detailC,
        "c1" to detailA, "c2" to detailA,
    )

    private val playbackMap = mapOf(
        "a1" to PlaybackInfo(itemId = "a1", mediaUrl = url1),
        "a2" to PlaybackInfo(itemId = "a2", mediaUrl = url2),
        "a3" to PlaybackInfo(itemId = "a3", mediaUrl = url3),
        "b1" to PlaybackInfo(itemId = "b1", mediaUrl = url2),
        "b2" to PlaybackInfo(itemId = "b2", mediaUrl = url3),
        "b3" to PlaybackInfo(itemId = "b3", mediaUrl = url1),
        "b4" to PlaybackInfo(itemId = "b4", mediaUrl = url4),
        "c1" to PlaybackInfo(itemId = "c1", mediaUrl = url3),
        "c2" to PlaybackInfo(itemId = "c2", mediaUrl = url1),
    )

    fun getDetailForItem(itemId: String): DetailData? = detailMap[itemId]

    fun getPlaybackInfo(itemId: String): PlaybackInfo? = playbackMap[itemId]
}
