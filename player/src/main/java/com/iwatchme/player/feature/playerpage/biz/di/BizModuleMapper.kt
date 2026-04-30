package com.iwatchme.player.feature.playerpage.biz.di

import com.iwatchme.player.core.ui.RunningUIComponent
import com.iwatchme.player.model.BizModule
import com.iwatchme.player.model.BizModuleType
import dagger.MapKey

@MapKey
annotation class BizModuleTypeKey(val type: BizModuleType)

fun interface BizModuleMapper {
    fun Scope.map()

    interface Scope {
        val module: BizModule
        fun emit(component: RunningUIComponent)
    }
}

fun interface BizModuleListMapper {
    fun map(modules: List<BizModule>): List<RunningUIComponent>
}
