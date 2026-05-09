package com.iwatchme.player.feature.playerpage.page

import android.util.Log
import com.iwatchme.player.core.di.PageCoroutineScope
import com.iwatchme.player.core.di.PageScope
import com.iwatchme.player.feature.playerpage.di.CurrentMediaComponent
import com.iwatchme.player.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PageScope 级别的 MediaScope 调度器。与 [BizScopeDriver] 并列：
 *
 *  - BizScopeDriver 由 switchToNewVideo 驱动，构造 BizScope；
 *  - MediaScopeDriver 由 [switchTo] 投递的请求驱动，构造 MediaScope。
 *
 * BizScope 内部对"选集"的处理（[com.iwatchme.player.feature.playerpage.biz.MediaSelectionDispatcher]）
 * 会通过 [switchTo] 把当前要播放的 item 推过来；EpisodeScope 也是从 [currentItemFlow] 驱动的，
 * 因此 episode 切换的真相来自播放器域，而不是 UI selection。
 */
@PageScope
class MediaScopeDriver @Inject constructor(
    @PageCoroutineScope private val pageScope: CoroutineScope,
    private val mediaComponentFactory: CurrentMediaComponent.Factory,
) {
    private val _currentItemFlow = MutableStateFlow<VideoItem?>(null)
    val currentItemFlow: StateFlow<VideoItem?> = _currentItemFlow

    init {
        pageScope.launch {
            _currentItemFlow.collectLatest { item ->
                if (item == null) return@collectLatest
                Log.d("Player", "[MediaScopeDriver] >>> MediaScope CREATING for item=${item.title}")
                coroutineScope {
                    val component = mediaComponentFactory.create(scope = this, item = item)
                    component.bootstrap().start()
                    Log.d("Player", "[MediaScopeDriver] MediaScope started, awaiting cancellation...")
                    try {
                        awaitCancellation()
                    } finally {
                        Log.d("Player", "[MediaScopeDriver] <<< MediaScope DESTROYED for item=${item.title}")
                    }
                }
            }
        }
    }

    fun switchTo(item: VideoItem) {
        Log.d("Player", "[MediaScopeDriver] switchTo: ${item.title}")
        _currentItemFlow.value = item
    }
}
