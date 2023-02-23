package com.iwatchme.jetpackstarter.emailbox

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class InboxViewModel : ViewModel() {

    val uiState = MutableStateFlow(InboxState())


    private fun loadContent() {
        uiState.value = uiState.value.copy(
            status = InboxStatus.LOADING
        )

        uiState.value = uiState.value.copy(
            status = InboxStatus.SUCCESS,
            emails = EmailFactory.makeEmailList()
        )
    }


    private fun DeleteEmail(id: String) {
        uiState.value = uiState.value.copy(
            emails = uiState.value.emails.filter { it.id != id }
        )
    }


    fun handleEvent(event: InboxEvent) {
        when (event) {
            is InboxEvent.RefreshContent -> loadContent()

            is InboxEvent.DeleteContent->  DeleteEmail(event.id)
        }
    }
}