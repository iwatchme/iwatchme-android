package com.iwatchme.host.shadow.safety

/**
 * 三道防线之三：当插件不可用（本地多次崩溃 + 回滚也救不了）时，降级到备用方案。
 * 通常是跳 H5；也可以是返回空 UI 提示。
 */
class PluginDegradeManager(
    private val crashGuard: PluginCrashGuard,
) {

    private val configByPart = mutableMapOf<String, DegradeConfig>()

    fun setConfig(partKey: String, config: DegradeConfig) {
        configByPart[partKey] = config
    }

    fun decide(partKey: String, serverForce: Boolean): Decision {
        val config = configByPart[partKey] ?: return Decision.LoadPlugin
        if (!config.enabled) return Decision.LoadPlugin
        if (serverForce) return Decision.Force(config.fallbackUrl)
        if (crashGuard.shouldRollback(partKey)) return Decision.LocalFailureFallback(config.fallbackUrl)
        return Decision.LoadPlugin
    }

    data class DegradeConfig(
        val fallbackUrl: String,
        val enabled: Boolean = true,
    )

    sealed interface Decision {
        data object LoadPlugin : Decision
        data class Force(val fallbackUrl: String) : Decision
        data class LocalFailureFallback(val fallbackUrl: String) : Decision
    }
}
