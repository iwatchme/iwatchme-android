package com.iwatchme.host.shadow

import android.util.Log
import com.tencent.shadow.dynamic.host.PluginProcessService

/**
 * 跑在宿主独立 `:plugin` 进程里的 Shadow PluginProcessService。
 * 负责把宿主进程的 Manager 请求转给插件进程的 Loader 执行（Binder 跨进程）。
 *
 * 注意：默认空实现已经够用，扩展点是日志/埋点。
 */
class IwatchmePluginProcessService : PluginProcessService() {
    init {
        Log.d(TAG, "IwatchmePluginProcessService created")
    }

    companion object {
        private const val TAG = "IwatchmePluginPPS"
    }
}
