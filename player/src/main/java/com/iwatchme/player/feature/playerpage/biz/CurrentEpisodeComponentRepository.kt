package com.iwatchme.player.feature.playerpage.biz

import android.util.Log
import com.iwatchme.player.core.di.BizScope
import com.iwatchme.player.feature.playerpage.di.CurrentEpisodeComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * 当前激活的 EpisodeComponent 引用。EpisodeScopeDriver 创建 / 销毁 EpisodeScope 时写入。
 *
 * Repo 作为 service 间通信桥梁的典型用法 —— Driver（BizScope 维度）把当前 EpisodeComponent
 * 投递到 BizScope 的 Repo 里，让 BizScope 内的其他 Service 能感知 episode 切换。
 */
@BizScope
class CurrentEpisodeComponentRepository @Inject constructor() {

    private val _componentFlow = MutableStateFlow<CurrentEpisodeComponent?>(null)
    val componentFlow: StateFlow<CurrentEpisodeComponent?> = _componentFlow

    fun update(component: CurrentEpisodeComponent?) {
        Log.d(
            "Player",
            "[CurrentEpisodeComponentRepo] EpisodeComponent updated: ${if (component != null) "available" else "null"}",
        )
        _componentFlow.value = component
    }
}
