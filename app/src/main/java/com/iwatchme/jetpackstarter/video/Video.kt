package com.iwatchme.jetpackstarter.video

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fy.kotlindemo.video.VideoPlayer


@Composable
fun Video(
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
) {
    val viewModel: VideoViewModel = viewModel()

    VideoPlayer(
        videoState = viewModel.uiState.collectAsState().value,
        handleEvent = viewModel::handleEvent,
        lifecycleOwner = lifecycleOwner,
        modifier = Modifier.fillMaxSize()
    )

}