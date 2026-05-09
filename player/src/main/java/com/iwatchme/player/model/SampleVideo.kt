package com.iwatchme.player.model

/**
 * 样片源全部使用国内网络更友好的公共 MP4 直链。
 *
 * 这些 URL 已在 2026-05-09 通过 curl HEAD 探测确认 200 OK，且都支持 range 请求。
 */
enum class SampleVideo(
    val displayName: String,
    val url: String,
    val durationMs: Long,
) {
    XIGUA_DEMO_360P(
        displayName = "西瓜播放器 Demo 360P",
        url = "https://sf1-cdn-tos.huoshanstatic.com/obj/media-fe/xgplayer_doc_video/mp4/xgplayer-demo-360p.mp4",
        durationMs = 30_000L,
    ),
    XIGUA_DEMO_720P(
        displayName = "西瓜播放器 Demo 720P",
        url = "https://sf1-cdn-tos.huoshanstatic.com/obj/media-fe/xgplayer_doc_video/mp4/xgplayer-demo-720p.mp4",
        durationMs = 30_000L,
    ),
    ALIYUN_MEDIA(
        displayName = "阿里云播放器样片",
        url = "https://player.alicdn.com/video/aliyunmedia.mp4",
        durationMs = 240_000L,
    ),
    ALIYUN_QUPAI(
        displayName = "阿里云趣拍样片",
        url = "https://player.alicdn.com/resource/player/qupai.mp4",
        durationMs = 60_000L,
    ),
    IQILU_NEWS_1(
        displayName = "齐鲁网新闻样片 1",
        url = "https://stream7.iqilu.com/10339/upload_transcode/202002/09/20200209105011F0zPoYzHry.mp4",
        durationMs = 60_000L,
    ),
    IQILU_NEWS_2(
        displayName = "齐鲁网新闻样片 2",
        url = "https://stream7.iqilu.com/10339/upload_transcode/202002/09/20200209104902N3v5Vpxuvb.mp4",
        durationMs = 45_000L,
    ),
    RUNOOB_BBB(
        displayName = "Big Buck Bunny（菜鸟教程）",
        url = "https://www.runoob.com/try/demo_source/mov_bbb.mp4",
        durationMs = 60_000L,
    ),
    RUNOOB_MOVIE(
        displayName = "测试影片（菜鸟教程）",
        url = "https://www.runoob.com/try/demo_source/movie.mp4",
        durationMs = 30_000L,
    ),
    W3SCHOOL_BBB(
        displayName = "Big Buck Bunny（W3School）",
        url = "https://www.w3school.com.cn/example/html5/mov_bbb.mp4",
        durationMs = 60_000L,
    ),
}
