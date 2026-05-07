package com.iwatchme.player.feature.playerpage.biz

import android.util.Log
import com.iwatchme.player.core.di.BizScope
import com.iwatchme.player.feature.playerpage.di.CurrentEpisodeComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * 桥接 EpisodeScope 到 BizScope 之外（Fragment）的入口。
 * EpisodeScopeDriver 创建 CurrentEpisodeComponent 后写入这里。
 */
@BizScope
class EpisodeComponentBridge @Inject constructor() {

    private val _episodeComponentFlow = MutableStateFlow<CurrentEpisodeComponent?>(null)
    val episodeComponentFlow: StateFlow<CurrentEpisodeComponent?> = _episodeComponentFlow

    fun update(component: CurrentEpisodeComponent?) {
        Log.d(
            "Player",
            "[EpisodeComponentBridge] EpisodeComponent updated: ${if (component != null) "available" else "null"}",
        )
        _episodeComponentFlow.value = component
    }
}
