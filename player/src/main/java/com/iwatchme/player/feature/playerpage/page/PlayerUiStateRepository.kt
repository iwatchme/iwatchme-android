package com.iwatchme.player.feature.playerpage.page

import android.util.Log
import com.iwatchme.player.core.di.PageScope
import com.iwatchme.player.model.PlaybackState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@PageScope
class PlayerUiStateRepository @Inject constructor() {

    private val _playbackStateFlow = MutableStateFlow(PlaybackState.IDLE)
    val playbackStateFlow: StateFlow<PlaybackState> = _playbackStateFlow

    private val _errorMessageFlow = MutableStateFlow<String?>(null)
    val errorMessageFlow: StateFlow<String?> = _errorMessageFlow

    /**
     * "本次播放完整结束"的一次性事件流。
     *
     * 用 SharedFlow 而不是 StateFlow，是为了让新订阅者**不会收到历史事件**——避免上一集
     * 播完留下的 COMPLETED 状态在新 EpisodeScope 起来时被当成"刚发生的事"重放、形成
     * "切下一集 → 新 service 又看见 → 再切"的死循环。replay = 0 + 1 个 buffer slot 让
     * tryEmit 永远不丢事件，又不会重放给后来者。
     */
    private val _completionEvents = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    val completionEvents: SharedFlow<Unit> = _completionEvents

    fun updatePlaybackState(state: PlaybackState) {
        Log.d("Player", "[PlayerUiStateRepo] Playback state: ${_playbackStateFlow.value} -> $state")
        _playbackStateFlow.value = state
    }

    fun updateError(message: String?) {
        _errorMessageFlow.value = message
    }

    fun notifyCompletion() {
        Log.d("Player", "[PlayerUiStateRepo] notifyCompletion()")
        _completionEvents.tryEmit(Unit)
    }
}
