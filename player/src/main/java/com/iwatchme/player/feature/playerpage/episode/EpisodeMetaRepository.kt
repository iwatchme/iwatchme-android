package com.iwatchme.player.feature.playerpage.episode

import android.util.Log
import com.iwatchme.player.core.di.EpisodeCoroutineScope
import com.iwatchme.player.core.di.EpisodeScope
import com.iwatchme.player.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EpisodeMeta(
    val cid: Long,
    val chapters: List<String>,
)

/**
 * EpisodeScope 内的"集级别"元数据 repo（章节信息、互动节点、多语言音轨清单等）。
 *
 * 设计原则：repo 只负责存储 + 暴露 flow + 提供 loadXxx() 写自己 flow，被动数据源；
 * 触发加载由 Service / Anchor 调用，不在构造期自动跑业务。
 */
@EpisodeScope
class EpisodeMetaRepository @Inject constructor(
    @EpisodeCoroutineScope private val scope: CoroutineScope,
    private val item: VideoItem,
) {

    private val _metaFlow = MutableStateFlow<EpisodeMeta?>(null)
    val metaFlow: StateFlow<EpisodeMeta?> = _metaFlow

    fun loadMeta() {
        scope.launch {
            Log.d("Player", "[EpisodeMetaRepo] Loading chapter metadata for cid=${item.cid}, title=${item.title}")
            delay(150)
            val meta = EpisodeMeta(
                cid = item.cid,
                chapters = listOf("intro", "main", "outro"),
            )
            Log.d("Player", "[EpisodeMetaRepo] Chapter metadata loaded for cid=${item.cid}: ${meta.chapters}")
            _metaFlow.value = meta
        }
    }
}
