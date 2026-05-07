package com.iwatchme.player.model

data class VideoItem(
    val id: String,
    val cid: Long,
    val title: String,
    val mediaUrl: String,
    val coverUrl: String? = null,
    val durationMs: Long? = null,
)
