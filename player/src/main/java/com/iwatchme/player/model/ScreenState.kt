package com.iwatchme.player.model

import android.content.pm.ActivityInfo

/**
 * 屏幕状态——demo 简化版只两态：竖屏半屏 / 横屏全屏。生产代码应该拆成 isPortrait / isFullscreen /
 * isReversed 三个独立维度并附加重力感应锁机制，demo 用枚举先把链路打通。
 */
enum class ScreenState(
    val orientation: Int,
    val isFullscreen: Boolean,
) {
    PORTRAIT_HALF(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, false),
    LANDSCAPE_FULL(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, true),
}
