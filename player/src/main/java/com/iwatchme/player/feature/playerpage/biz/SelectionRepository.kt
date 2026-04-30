package com.iwatchme.player.feature.playerpage.biz

import android.util.Log
import com.iwatchme.player.core.di.BizScope
import com.iwatchme.player.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@BizScope
class SelectionRepository @Inject constructor(
    private val videoListRepository: VideoListRepository,
) {
    private val _selectedItemIdFlow = MutableStateFlow<String?>(null)
    val selectedItemIdFlow: StateFlow<String?> = _selectedItemIdFlow

    val selectedItemFlow: StateFlow<VideoItem?>
        get() = object : StateFlow<VideoItem?> {
            override val replayCache: List<VideoItem?>
                get() = listOf(value)
            override val value: VideoItem?
                get() {
                    val id = _selectedItemIdFlow.value ?: return null
                    return videoListRepository.itemsFlow.value.find { it.id == id }
                }

            override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<VideoItem?>): Nothing {
                _selectedItemIdFlow.collect { id ->
                    val item = if (id != null) {
                        videoListRepository.itemsFlow.value.find { it.id == id }
                    } else null
                    collector.emit(item)
                }
            }
        }

    val selectedItemId: String? get() = _selectedItemIdFlow.value

    fun select(itemId: String) {
        Log.d("Player", "[SelectionRepository] Selection changed: ${_selectedItemIdFlow.value} -> $itemId")
        _selectedItemIdFlow.value = itemId
    }
}
