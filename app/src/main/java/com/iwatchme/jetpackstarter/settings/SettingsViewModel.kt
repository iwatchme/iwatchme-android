package com.iwatchme.jetpackstarter.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class SettingsViewModel : ViewModel() {

    val state = MutableStateFlow(SettingState())


    fun toggleNotifications() {
        state.value = state.value.copy(notificationEnabled = !state.value.notificationEnabled)
    }

    fun toggleHintSetting() {
        state.value = state.value.copy(
            hintsEnabled = !state.value.hintsEnabled
        )
    }


    fun selectMaketOption(option: MarketOptions) {
        state.value = state.value.copy(
            marketOptions = option
        )
    }

    fun selectThemeOption(theme: Theme) {
        state.value = state.value.copy(
            theme = theme
        )
    }


}


data class SettingState(
    val notificationEnabled: Boolean = false, val hintsEnabled: Boolean = false,
    val marketOptions: MarketOptions = MarketOptions.ALLOWED,
    val theme: Theme = Theme.SYSTEM
)