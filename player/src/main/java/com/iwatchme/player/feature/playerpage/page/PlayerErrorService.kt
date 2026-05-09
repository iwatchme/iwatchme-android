package com.iwatchme.player.feature.playerpage.page

import com.iwatchme.player.core.di.PageCoroutineScope
import com.iwatchme.player.core.di.PageScope
import com.iwatchme.player.feature.playerpage.uicomponent.PlayerErrorUIComponent
import com.iwatchme.player.model.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@PageScope
class PlayerErrorService @Inject constructor(
    @PageCoroutineScope private val scope: CoroutineScope,
    private val playerUiStateRepository: PlayerUiStateRepository,
) {

    private val _stateFlow = MutableStateFlow(
        PlayerErrorUIComponent.State(visible = false, message = ""),
    )

    val viewModel: PlayerErrorUIComponent.ViewModel = object : PlayerErrorUIComponent.ViewModel {
        override val state: StateFlow<PlayerErrorUIComponent.State> = _stateFlow
    }

    init {
        scope.launch {
            combine(
                playerUiStateRepository.playbackStateFlow,
                playerUiStateRepository.errorMessageFlow,
            ) { state, error -> state to error }
                .collectLatest { (state, error) ->
                    _stateFlow.value = PlayerErrorUIComponent.State(
                        visible = state == PlaybackState.ERROR,
                        message = error ?: "播放出错",
                    )
                }
        }
    }
}
