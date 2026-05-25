package com.iwatchme.plugin.manager

/**
 * 与 [com.iwatchme.plugin.loader.IwatchmePluginConstants] 保持同步。
 * 跨 APK 共享通过常量字面值复制实现（manager/loader/host 不能直接互相依赖）。
 */
object IwatchmePluginConstants {
    const val KEY_PLUGIN_ZIP_PATH = "pluginZipPath"
    const val KEY_ACTIVITY_CLASSNAME = "KEY_ACTIVITY_CLASSNAME"
    const val KEY_PLUGIN_PART_KEY = "KEY_PLUGIN_PART_KEY"
    const val KEY_EXTRAS = "KEY_EXTRAS"

    /** 业务插件 partKey。一份 zip 里可以装多个 part；本期 demo 只用 main。*/
    const val PART_KEY_PLUGIN_MAIN = "iwatchme-plugin-main"

    const val FROM_ID_NOOP = 1000
    const val FROM_ID_START_ACTIVITY = 1002
    const val FROM_ID_CLOSE = 1003
}
