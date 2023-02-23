package com.iwatchme.jetpackstarter.video

import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.ui.StyledPlayerView

@Composable
fun Playback(
    modifier: Modifier = Modifier,
    status: PlayStatus = PlayStatus.PAUSE,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    exoPlayer: ExoPlayer,
    context: Context
) {
    val currentPlayStatus by rememberUpdatedState(status)

    LaunchedEffect(key1 = exoPlayer, block = {
        exoPlayer.prepare()
    })

    DisposableEffect(key1 = lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (currentPlayStatus == PlayStatus.PLAYING) {
                if (event == Lifecycle.Event.ON_RESUME) {
                    exoPlayer.play()
                } else if (event == Lifecycle.Event.ON_PAUSE) {
                    exoPlayer.pause()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)

        }
    }

    DisposableEffect(
        AndroidView(
            modifier = modifier,
            factory = {
            StyledPlayerView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
                player = exoPlayer
                hideController()
                useController = false
            }
        }, update = {
            when (status) {
                PlayStatus.PLAYING -> {
                    it.player?.play()
                }
                PlayStatus.PAUSE -> {
                    it.player?.pause()
                }

                else -> {}
            }

        })
    ) {
        onDispose {
            exoPlayer.release()
        }

    }
}