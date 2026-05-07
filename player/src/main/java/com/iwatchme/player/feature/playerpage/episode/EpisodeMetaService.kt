package com.iwatchme.player.feature.playerpage.episode

import android.util.Log
import com.iwatchme.player.core.di.EpisodeCoroutineScope
import com.iwatchme.player.core.di.EpisodeScope
import com.iwatchme.player.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 演示 EpisodeScope 内的初始化逻辑。生产代码里这一层做的是章节信息、互动视频节点、
 * 多语言音轨清单等"集级别"元数据加载。
 */
@EpisodeScope
class EpisodeMetaService @Inject constructor(
    @EpisodeCoroutineScope scope: CoroutineScope,
    private val item: VideoItem,
) {
    init {
        scope.launch {
            Log.d("Player", "[EpisodeMetaService] Loading chapter metadata for cid=${item.cid}, title=${item.title}")
            delay(150)
            Log.d("Player", "[EpisodeMetaService] Chapter metadata loaded for cid=${item.cid}")
        }
    }
}
