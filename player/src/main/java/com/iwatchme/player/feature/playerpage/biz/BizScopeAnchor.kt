package com.iwatchme.player.feature.playerpage.biz

import android.util.Log
import com.iwatchme.player.core.di.BizScope
import javax.inject.Inject

@BizScope
class BizScopeAnchor @Inject constructor(
    private val initialSelectionService: InitialSelectionService,
    private val episodeScopeDriver: EpisodeScopeDriver,
) {
    fun start() {
        Log.d("Player", "[BizScopeAnchor] start() — BizScope services initialized (InitialSelection + EpisodeScopeDriver)")
    }
}
