package com.iwatchme.player.model

enum class BizModuleType {
    VIDEO_LIST,
}

data class BizModule(
    val type: BizModuleType,
    val data: Any? = null,
)
