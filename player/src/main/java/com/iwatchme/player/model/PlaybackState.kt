package com.iwatchme.player.model

enum class PlaybackState {
    IDLE,
    LOADING,
    READY,
    /** 播放器报告 ENDED：当前 media 完整播完，需要触发"播下一集"等业务逻辑。 */
    COMPLETED,
    ERROR,
}
