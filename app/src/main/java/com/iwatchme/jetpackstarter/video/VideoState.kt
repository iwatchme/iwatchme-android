package com.iwatchme.jetpackstarter.video

data class VideoState(
   val playStatus: PlayStatus = PlayStatus.LOADING

)


enum class PlayStatus {
    PLAYING, PAUSE, LOADING, IDEL, ERROR
}