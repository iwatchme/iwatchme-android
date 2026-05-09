package com.iwatchme.player.feature.playerpage.media

import android.util.Log
import com.iwatchme.player.core.di.MediaScope
import javax.inject.Inject

/**
 * MediaScope 的 bootstrap：拉起播放准备 + 播放状态监听这两个 service，让 Dagger 在 MediaScope
 * 构造时一并实例化它们。
 */
@MediaScope
class MediaBootstrap @Inject constructor(
    private val mediaPrepareService: MediaPrepareService,
    private val playbackStatusService: PlaybackStatusService,
) {
    fun start() {
        Log.d("Player", "[MediaBootstrap] start() — MediaScope services initialized (Prepare + Status)")
    }
}
