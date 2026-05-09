package com.iwatchme.player.feature.playerpage.biz

import android.util.Log
import com.iwatchme.player.core.di.BizScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * 当前选中的视频条目 id。
 *
 * 设计原则：repo 是小粒度被动数据源，**不依赖其他 repo**。这里只持有 id；
 * 需要"由 id 解析出 VideoItem"的组合逻辑应当在 Service 里完成（Service 可以同时持有
 * SelectionRepository 和 VideoListRepository 做 join）。
 */
@BizScope
class SelectionRepository @Inject constructor() {

    private val _selectedItemIdFlow = MutableStateFlow<String?>(null)
    val selectedItemIdFlow: StateFlow<String?> = _selectedItemIdFlow

    val selectedItemId: String? get() = _selectedItemIdFlow.value

    fun select(itemId: String) {
        Log.d("Player", "[SelectionRepository] Selection changed: ${_selectedItemIdFlow.value} -> $itemId")
        _selectedItemIdFlow.value = itemId
    }
}
