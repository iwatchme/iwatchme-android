package com.fy.kotlindemo.video

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.LifecycleOwner
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.iwatchme.jetpackstarter.video.Controls
import com.iwatchme.jetpackstarter.video.PlayStatus
import com.iwatchme.jetpackstarter.video.Playback
import com.iwatchme.jetpackstarter.video.VideoEvent
import com.iwatchme.jetpackstarter.video.VideoState


@Composable
fun VideoPlayer(
    modifier: Modifier = Modifier,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    videoState: VideoState,
    handleEvent: (event: VideoEvent) -> Unit

) {
    val context = LocalContext.current
    val exoplayer = remember {
        val mediaItem =
            MediaItem.fromUri("https://klxxcdn.oss-cn-hangzhou.aliyuncs.com/histudy/hrm/media/bg3.mp4")
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(mediaItem)
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    super.onPlaybackStateChanged(state)
                    if (state == ExoPlayer.STATE_READY) {
                        handleEvent(VideoEvent.VideoLoaded)
                    } else if (state == ExoPlayer.EVENT_PLAYER_ERROR) {
                        handleEvent(VideoEvent.VideoError)
                    }
                }
            })
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        var controlsVisible by remember {
            mutableStateOf(true)
        }

        val alphaAnimation by animateFloatAsState(
            targetValue = if (controlsVisible) 0.75f else 0f,
            animationSpec = if (controlsVisible) {
                tween(delayMillis = 0)
            } else {
                tween(delayMillis = 750)
            }
        )

        Playback(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    controlsVisible = !controlsVisible
                },
            lifecycleOwner = lifecycleOwner,
            exoPlayer = exoplayer,
            status = videoState.playStatus,
            context = context
        )


        Controls(
            playStatus = videoState.playStatus,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .alpha(alphaAnimation)
        ) {
            handleEvent(VideoEvent.ToggleStatus)
            if (videoState.playStatus != PlayStatus.PLAYING) {
                controlsVisible = false
            }
        }
    }


}