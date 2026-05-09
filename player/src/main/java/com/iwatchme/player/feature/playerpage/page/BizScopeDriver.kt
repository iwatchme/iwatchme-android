package com.iwatchme.player.feature.playerpage.page

import android.util.Log
import com.iwatchme.player.core.di.PageCoroutineScope
import com.iwatchme.player.core.di.PageScope
import com.iwatchme.player.feature.playerpage.di.OGVBizComponent
import com.iwatchme.player.feature.playerpage.di.PlayerBizFacade
import com.iwatchme.player.feature.playerpage.di.UGCBizComponent
import com.iwatchme.player.model.DetailData
import com.iwatchme.player.model.OGVDetail
import com.iwatchme.player.model.UGCDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BizScopeDriver 是 PageScope 的状态机 + 业务编排器。
 *
 * 在多业务版本里，它**注入多份 Subcomponent.Factory**（UGC / OGV），按 sealed [DetailData] 的运行时
 * 类型 when 分发到对应业务的 BizScope，例如：
 *
 *     when (detail) {
 *         is UGCDetail -> ugcBizComponentFactory.create(...)
 *         is OGVDetail -> ogvBizComponentFactory.create(...)
 *     }
 *
 * 拿到的是统一的 [PlayerBizFacade]，写到 [CurrentBizComponentRepository] 里，Fragment 从 facade
 * 上取通用能力，业务专属差异留在 BizScope 内部。
 */
@PageScope
class BizScopeDriver @Inject constructor(
    @PageCoroutineScope private val pageScope: CoroutineScope,
    private val pageDetailRepository: PageDetailRepository,
    private val ugcBizComponentFactory: UGCBizComponent.Factory,
    private val ogvBizComponentFactory: OGVBizComponent.Factory,
    private val currentBizComponentRepository: CurrentBizComponentRepository,
) {
    private val _stateFlow = MutableStateFlow<State>(State.Idle)
    val stateFlow: StateFlow<State> = _stateFlow.asStateFlow()

    private var runningJob: Job? = null

    init {
        pageScope.launch {
            _stateFlow.collectLatest { state ->
                when (state) {
                    is State.InBusiness -> driveBusinessScope(state.detail)
                    State.Idle, is State.Loading, is State.Failure -> {
                        // 这些状态下不需要 BizScope；老的 BizScope 已经被 collectLatest 取消了。
                    }
                }
            }
        }
    }

    fun switchToNewVideo(startParams: StartParams) {
        Log.d("Player", "[BizScopeDriver] switchToNewVideo bvid=${startParams.bvid}")
        _stateFlow.value = State.Loading(startParams)
        runningJob?.cancel()
        runningJob = pageScope.launch {
            val result = pageDetailRepository.loadDetail(startParams.bvid)
            _stateFlow.value = result.fold(
                onSuccess = { State.InBusiness(it) },
                onFailure = { error ->
                    Log.w("Player", "[BizScopeDriver] load failed: ${error.message}")
                    State.Failure(startParams, error)
                },
            )
        }
    }

    private suspend fun driveBusinessScope(detail: DetailData) {
        Log.d(
            "Player",
            "[BizScopeDriver] >>> BizScope CREATING for biz=${detail.businessType} bvid=${detail.bvid}",
        )
        coroutineScope {
            val component: PlayerBizFacade = when (detail) {
                is UGCDetail -> ugcBizComponentFactory.create(scope = this, detail = detail)
                is OGVDetail -> ogvBizComponentFactory.create(scope = this, detail = detail)
            }
            currentBizComponentRepository.update(component)
            component.bootstrap().start()
            Log.d("Player", "[BizScopeDriver] BizScope (${component.javaClass.simpleName}) started, awaiting cancellation...")
            try {
                awaitCancellation()
            } finally {
                currentBizComponentRepository.update(null)
                Log.d("Player", "[BizScopeDriver] <<< BizScope DESTROYED for bvid=${detail.bvid}")
            }
        }
    }

    sealed interface State {
        object Idle : State
        class Loading(val startParams: StartParams) : State
        class InBusiness(val detail: DetailData) : State
        class Failure(val startParams: StartParams, val error: Throwable) : State
    }

    class StartParams(val bvid: String)
}
