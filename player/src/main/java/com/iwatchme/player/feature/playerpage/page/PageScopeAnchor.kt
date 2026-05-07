package com.iwatchme.player.feature.playerpage.page

import android.util.Log
import com.iwatchme.player.core.di.PageScope
import javax.inject.Inject

@PageScope
class PageScopeAnchor @Inject constructor(
    private val pageDetailRepository: PageDetailRepository,
    private val bizScopeDriver: BizScopeDriver,
) {
    fun start() {
        Log.d("Player", "[PageScopeAnchor] start() — triggering detail load (BizScopeDriver active)")
        pageDetailRepository.load()
    }
}
