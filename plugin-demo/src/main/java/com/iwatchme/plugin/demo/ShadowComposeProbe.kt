package com.iwatchme.plugin.demo

import android.app.Activity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Compose UI 在 Shadow 插件中可行性探针。
 *
 * 问题：Shadow `PluginActivity` 直接继承 [Activity]（不是 ComponentActivity），ComposeView
 * 会在 onMeasure 阶段 `IllegalStateException: ViewTreeLifecycleOwner not found`。
 *
 * 解：手动给容器 View 设三件套 owner —— Lifecycle / ViewModelStore / SavedStateRegistry。
 * 用最简实现（一个手动驱动到 RESUMED 的 LifecycleRegistry）就够 demo 验证；生产里应该把
 * lifecycle 跟 Activity 真实生命周期联动（在 PluginActivity.onResume/onPause/onDestroy 推进状态）。
 */
object ShadowComposeProbe {

    fun swapToCompose(activity: Activity) {
        val owner = StubComposeOwner()
        owner.bindToActivityLifecycle(activity)

        val container = FrameLayout(activity)
        container.setViewTreeLifecycleOwner(owner)
        container.setViewTreeViewModelStoreOwner(owner)
        container.setViewTreeSavedStateRegistryOwner(owner)

        val composeView = ComposeView(activity).apply {
            setContent { PluginComposeContent() }
        }
        container.addView(composeView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        activity.setContentView(container)
    }
}

/**
 * 最简的 LifecycleOwner / ViewModelStoreOwner / SavedStateRegistryOwner 三合一。
 * SavedStateRegistryController.performRestore(null) 之后必须把 lifecycle 推到 CREATED 才能 setContent，
 * 推到 STARTED+ 才能完成首次 measure & layout。
 */
private class StubComposeOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val viewModelStoreInstance = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = viewModelStoreInstance
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun bindToActivityLifecycle(activity: Activity) {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        // 生产应当 hook activity.onPause → STARTED, onStop → CREATED, onDestroy → DESTROYED
        // 这里 demo 简化：固定 RESUMED；插件 Activity finish 时 GC 回收。
    }
}

@androidx.compose.runtime.Composable
private fun PluginComposeContent() {
    var count by remember { mutableStateOf(0) }
    Column(Modifier.padding(32.dp)) {
        Text("✅ Compose 在 Shadow 插件内可用")
        Text(text = "PID=${android.os.Process.myPid()}（:plugin 进程）")
        Text(text = "状态计数 = $count")
        Button(onClick = { count++ }) { Text("点我 +1（测 recomposition）") }
    }
}
