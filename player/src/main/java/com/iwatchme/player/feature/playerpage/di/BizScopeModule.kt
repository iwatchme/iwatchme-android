package com.iwatchme.player.feature.playerpage.di

import com.iwatchme.player.core.ui.RunningUIComponent
import com.iwatchme.player.feature.playerpage.biz.di.BizModuleListMapper
import com.iwatchme.player.feature.playerpage.biz.di.BizModuleMapper
import com.iwatchme.player.model.BizModuleType
import dagger.Module
import dagger.Provides

@Module
object BizScopeModule {

    @Provides
    fun provideBizModuleListMapper(
        mappers: Map<BizModuleType, @JvmSuppressWildcards BizModuleMapper>,
    ): BizModuleListMapper = BizModuleListMapper { modules ->
        val result = mutableListOf<RunningUIComponent>()
        modules.forEach { module ->
            val scope = object : BizModuleMapper.Scope {
                override val module = module
                override fun emit(component: RunningUIComponent) {
                    result += component
                }
            }
            mappers[module.type]?.apply { scope.map() }
        }
        result
    }
}
