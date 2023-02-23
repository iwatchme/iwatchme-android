package com.iwatchme.jetpackstarter.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun Controls(
    modifier: Modifier = Modifier,
    playStatus: PlayStatus,
    togglePlayingState: ()-> Unit
) {
    Box(modifier = modifier.background(MaterialTheme.colors.surface),
        contentAlignment = androidx.compose.ui.Alignment.Center
        ) {

        IconButton(
            onClick = {
                togglePlayingState()
            },
            enabled = playStatus != PlayStatus.LOADING
        ) {
            val icon = if (playStatus == PlayStatus.PLAYING) {
                Icons.Default.Pause
            } else {
                Icons.Default.PlayArrow
            }
            val description = if (playStatus == PlayStatus.PLAYING) {
                "pause"
            } else {
                "play"
            }
            Icon(
               imageVector = icon,
                contentDescription = description
            )
        }
    }
}