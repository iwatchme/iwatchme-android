package com.iwatchme.player.model

/**
 * 样片源全部用国内可访问的公共 CDN（菜鸟教程 / W3School / 浙江广电直播流）。
 * - mp4 来自 runoob.com / w3school.com.cn 的 HTML5 教程示例资源；
 * - HLS 来自浙江广电（cztv.com.cn）的公开直播流，channel001-010 共 10 个频道。
 *
 * 这些 URL 经过 curl 探测确认 200 OK；W3C / Google 桶在国内移动网络上经常超时，
 * 所以全部替换。HLS 通过 media3-exoplayer-hls 依赖支持。
 */
enum class SampleVideo(
    val displayName: String,
    val url: String,
    val durationMs: Long,
) {
    // mp4 — 菜鸟教程 / W3School 教学资源
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

    // HLS — 浙江广电直播流（cztv.com.cn），实时画面，所有时长按估算填一个值便于显示
    CZTV_CH1(
        displayName = "浙江卫视 直播",
        url = "https://ali-m-l.cztv.com/channels/lantian/channel001/1080p.m3u8",
        durationMs = -1L, // -1 表示直播
    ),
    CZTV_CH2(
        displayName = "钱江频道 直播",
        url = "https://ali-m-l.cztv.com/channels/lantian/channel002/1080p.m3u8",
        durationMs = -1L,
    ),
    CZTV_CH3(
        displayName = "经视频道 直播",
        url = "https://ali-m-l.cztv.com/channels/lantian/channel003/1080p.m3u8",
        durationMs = -1L,
    ),
    CZTV_CH4(
        displayName = "民生休闲 直播",
        url = "https://ali-m-l.cztv.com/channels/lantian/channel004/1080p.m3u8",
        durationMs = -1L,
    ),
    CZTV_CH5(
        displayName = "教育科技 直播",
        url = "https://ali-m-l.cztv.com/channels/lantian/channel005/1080p.m3u8",
        durationMs = -1L,
    ),
    CZTV_CH6(
        displayName = "影视娱乐 直播",
        url = "https://ali-m-l.cztv.com/channels/lantian/channel006/1080p.m3u8",
        durationMs = -1L,
    ),
    CZTV_CH7(
        displayName = "少儿频道 直播",
        url = "https://ali-m-l.cztv.com/channels/lantian/channel007/1080p.m3u8",
        durationMs = -1L,
    ),
    CZTV_CH8(
        displayName = "公共新闻 直播",
        url = "https://ali-m-l.cztv.com/channels/lantian/channel008/1080p.m3u8",
        durationMs = -1L,
    ),
    CZTV_CH9(
        displayName = "国际频道 直播",
        url = "https://ali-m-l.cztv.com/channels/lantian/channel009/1080p.m3u8",
        durationMs = -1L,
    ),
    CZTV_CH10(
        displayName = "高清综合 直播",
        url = "https://ali-m-l.cztv.com/channels/lantian/channel010/1080p.m3u8",
        durationMs = -1L,
    ),
}
