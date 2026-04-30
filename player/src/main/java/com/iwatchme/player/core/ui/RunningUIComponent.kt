package com.iwatchme.player.core.ui

import kotlinx.coroutines.awaitCancellation

class RunningUIComponent(
    val uiComponent: UIComponent<*>,
    val listSpanSize: Int = 1,
    private val stateDriver: (suspend () -> Unit)? = null,
) {
    suspend fun runUntilCancellation(): Nothing {
        stateDriver?.invoke()
        awaitCancellation()
    }
}
