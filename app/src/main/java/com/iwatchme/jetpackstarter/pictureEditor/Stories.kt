package com.iwatchme.jetpackstarter.pictureEditor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.asStateFlow

@Composable
fun Stories() {
    val viewModel = viewModel<EditViewModel>()
    MaterialTheme {
        StoriesEditor(
            modifier =  Modifier.fillMaxSize(),
            state = viewModel.uiState.collectAsState().value,
            handleEvent = viewModel::handleEvent
        )

    }

}