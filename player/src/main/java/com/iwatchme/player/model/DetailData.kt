package com.iwatchme.player.model

data class DetailData(
    val bvid: String,
    val title: String,
    val coverUrl: String? = null,
    val items: List<VideoItem>,
    val modules: List<BizModule> = listOf(BizModule(BizModuleType.VIDEO_LIST)),
)
