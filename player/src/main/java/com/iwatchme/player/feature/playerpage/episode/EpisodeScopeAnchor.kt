package com.iwatchme.player.feature.playerpage.episode

import android.util.Log
import com.iwatchme.player.core.di.EpisodeScope
import javax.inject.Inject

@EpisodeScope
class EpisodeScopeAnchor @Inject constructor(
    private val currentEpisodeRepository: CurrentEpisodeRepository,
    private val episodeMetaService: EpisodeMetaService,
    private val mediaRequestRepository: MediaRequestRepository,
    private val mediaScopeDriver: MediaScopeDriver,
) {
    fun start() {
        Log.d(
            "Player",
            "[EpisodeScopeAnchor] start() — EpisodeScope services initialized for item=${currentEpisodeRepository.currentItem.title}",
        )
    }
}
