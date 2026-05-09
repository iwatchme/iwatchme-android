package com.iwatchme.player.feature.playerpage.page

import com.iwatchme.player.core.di.PageCoroutineScope
import com.iwatchme.player.core.di.PageScope
import com.iwatchme.player.feature.playerpage.uicomponent.PlayerLoadingUIComponent
import com.iwatchme.player.model.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@PageScope
class PlayerLoadingService @Inject constructor(
    @PageCoroutineScope private val scope: CoroutineScope,
    private val playerUiStateRepository: PlayerUiStateRepository,
) {

    private val _stateFlow = MutableStateFlow(PlayerLoadingUIComponent.State(visible = false))

    val viewModel: PlayerLoadingUIComponent.ViewModel = object : PlayerLoadingUIComponent.ViewModel {
        override val state: StateFlow<PlayerLoadingUIComponent.State> = _stateFlow
    }

    init {
        scope.launch {
            playerUiStateRepository.playbackStateFlow.collectLatest { state ->
                _stateFlow.value = PlayerLoadingUIComponent.State(visible = state == PlaybackState.LOADING)
            }
        }
    }
}
