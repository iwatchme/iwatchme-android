package com.iwatchme.player.feature.playerpage.page

import android.util.Log
import com.iwatchme.player.core.di.PageScope
import com.iwatchme.player.feature.playerpage.mock.MockData
import com.iwatchme.player.model.DetailData
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * 详情数据的 IO 封装：无状态，只暴露 suspend 加载入口。把"现在加载到了什么"放在调用方
 * （[BizScopeDriver]）的 stateFlow 里持有，是 theseus 那边 ViewRepository / BusinessScopeDriver
 * 的对应关系。
 */
@PageScope
class PageDetailRepository @Inject constructor() {

    suspend fun loadDetail(bvid: String): Result<DetailData> = runCatching {
        Log.d("Player", "[PageDetailRepository] loadDetail bvid=$bvid")
        delay(500)
        MockData.findByBvid(bvid) ?: error("Detail not found for bvid=$bvid")
    }
}
