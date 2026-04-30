package com.iwatchme.player.feature.playerpage.page

import android.util.Log
import com.iwatchme.player.core.di.PageScope
import com.iwatchme.player.model.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@PageScope
class PlayerUiStateRepository @Inject constructor() {

    private val _playbackStateFlow = MutableStateFlow(PlaybackState.IDLE)
    val playbackStateFlow: StateFlow<PlaybackState> = _playbackStateFlow

    private val _errorMessageFlow = MutableStateFlow<String?>(null)
    val errorMessageFlow: StateFlow<String?> = _errorMessageFlow

    fun updatePlaybackState(state: PlaybackState) {
        Log.d("Player", "[PlayerUiStateRepo] Playback state: ${_playbackStateFlow.value} -> $state")
        _playbackStateFlow.value = state
    }

    fun updateError(message: String?) {
        _errorMessageFlow.value = message
    }
}
