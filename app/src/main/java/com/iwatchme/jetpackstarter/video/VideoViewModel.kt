package com.iwatchme.jetpackstarter.video

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class VideoViewModel : ViewModel() {

    val uiState = MutableStateFlow(VideoState())


    fun handleEvent(videoEvent: VideoEvent) {
        when (videoEvent) {
            VideoEvent.VideoLoaded -> {
                uiState.value = uiState.value.copy(playStatus = PlayStatus.IDEL)
            }

            VideoEvent.VideoError -> {
                uiState.value = uiState.value.copy(playStatus = PlayStatus.ERROR)
            }

            VideoEvent.ToggleStatus -> {
                togglePlayerStatus()
            }
        }
    }

    private fun togglePlayerStatus() {
        val playerStatus = uiState.value.playStatus
        val newPlayerStatus = if (
            playerStatus != PlayStatus.PLAYING
        ) {
            PlayStatus.PLAYING
        } else {
            PlayStatus.PAUSE
        }

        uiState.value = uiState.value.copy(playStatus = newPlayerStatus)
    }
}


sealed class VideoEvent {
    object ToggleStatus : VideoEvent()
    object VideoLoaded : VideoEvent()
    object VideoError : VideoEvent()
}