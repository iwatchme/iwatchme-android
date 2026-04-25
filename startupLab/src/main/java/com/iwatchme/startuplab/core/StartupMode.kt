package com.iwatchme.startuplab.core

enum class StartupMode(
    val label: String,
) {
    LEGACY(label = "Legacy"),
    OPTIMIZED(label = "Optimized"),
    ;

    fun opposite(): StartupMode = if (this == LEGACY) OPTIMIZED else LEGACY
}
