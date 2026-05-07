package com.iwatchme.player.feature.playerpage.page

import android.util.Log
import com.iwatchme.player.core.di.PageCoroutineScope
import com.iwatchme.player.core.di.PageScope
import com.iwatchme.player.feature.playerpage.mock.MockData
import com.iwatchme.player.model.DetailData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@PageScope
class PageDetailRepository @Inject constructor(
    @PageCoroutineScope private val scope: CoroutineScope,
) {
    private val _detailFlow = MutableStateFlow<DetailData?>(null)
    val detailFlow: StateFlow<DetailData?> = _detailFlow

    fun load() {
        Log.d("Player", "[PageDetailRepository] load() triggered, requesting initial detail data...")
        scope.launch {
            delay(500)
            val detail = MockData.initialDetail
            Log.d("Player", "[PageDetailRepository] Detail loaded: bvid=${detail.bvid}, title=${detail.title}, items=${detail.items.size}")
            _detailFlow.value = detail
        }
    }

    fun cycleNextDetail() {
        val current = _detailFlow.value
        scope.launch {
            val next = if (current != null) MockData.nextDetailAfter(current) else MockData.initialDetail
            Log.d("Player", "[PageDetailRepository] cycleNextDetail: ${current?.title ?: "null"} -> ${next.title}")
            delay(200)
            _detailFlow.value = next
        }
    }
}
