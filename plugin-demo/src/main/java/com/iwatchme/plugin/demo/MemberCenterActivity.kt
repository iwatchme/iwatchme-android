package com.iwatchme.plugin.demo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shadow 插件示例 Activity，演示插件 Activity / Service / Provider / coroutine / launchMode 映射能力。
 *  ① 普通 Activity UI（Shadow Transform 把 `extends Activity` 改成 `extends PluginActivity`）
 *  ② Service：[PluginDemoService] 跑在 :plugin 进程，用 kotlinx-coroutines 做周期工作
 *  ③ ContentProvider：[PluginMemberProvider] 返回 stub 数据
 *  ④ Coroutines：本 Activity 里直接 CoroutineScope.launch 验证插件代码用协程无碍
 *  ⑤ launchMode：通过 [SingleTaskProbeActivity] 验证插件 Activity 映射到 singleTask 壳子
 *
 *  Compose UI 可用性见 [ShadowComposeProbe]（按钮 5 触发，故意放在独立类避免编译期就把 Compose
 *  打进 plugin APK——只有真按按钮才会触发 ComposeView 类加载）。
 */
class MemberCenterActivity : Activity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var statusText: TextView
    private var coroutineJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(40, 80, 40, 40)
        }

        layout.addView(TextView(this).apply {
            text = "Shadow Plugin Full Demo v5.0.0"
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        })

        statusText = TextView(this).apply {
            text = "等待操作\nPID=${android.os.Process.myPid()}（这是 :plugin 进程）"
            textSize = 14f
            setPadding(0, 0, 0, 32)
        }
        layout.addView(statusText)

        layout.addView(button("① 启动插件 Service（含 coroutine tick）") {
            val intent = Intent().setClassName(packageName, PluginDemoService::class.java.name)
            startService(intent)
            statusText.text = "已发 startService；看 logcat tag=PluginDemoService 5 秒内有 5 条 tick"
        })

        layout.addView(button("② 查询插件 ContentProvider") {
            scope.launch {
                val rows = withContext(Dispatchers.IO) {
                    contentResolver.query(PluginMemberProvider.CONTENT_URI, null, null, null, null)
                        ?.use { cursor ->
                            buildList<String> {
                                while (cursor.moveToNext()) {
                                    add("${cursor.getInt(0)}=${cursor.getString(1)}/${cursor.getString(2)}")
                                }
                            }
                        }
                }
                statusText.text = "Provider 返回 ${rows?.size} 条：\n${rows?.joinToString("\n")}"
            }
        })

        layout.addView(button("③ 插件内 Coroutine 直接跑（这里）") {
            coroutineJob?.cancel()
            coroutineJob = scope.launch {
                repeat(3) { i ->
                    statusText.text = "Coroutine tick ${i + 1}/3  thread=${Thread.currentThread().name}"
                    delay(700)
                }
                statusText.text = "Coroutine 完成（demo Activity 中）✓"
            }
        })

        layout.addView(button("④ 试 Compose UI（ComposeView 接管）") {
            try {
                ShadowComposeProbe.swapToCompose(this)
            } catch (t: Throwable) {
                statusText.text = "Compose 失败 ❌ ${t.javaClass.simpleName}: ${t.message}"
            }
        })

        layout.addView(button("⑤ 打开 singleTask 插件 Activity") {
            val intent = Intent().setClassName(packageName, SingleTaskProbeActivity::class.java.name)
                .putExtra(SingleTaskProbeActivity.EXTRA_SOURCE, "MemberCenterActivity")
                .putExtra(SingleTaskProbeActivity.EXTRA_LAUNCH_INDEX, System.currentTimeMillis())
            startActivity(intent)
            statusText.text = "已发起 singleTask Activity；返回后可再次打开验证复用"
        })

        layout.addView(button("⑥ 触发崩溃（验证宿主不挂）") {
            throw RuntimeException("Plugin crash test")
        })

        setContentView(layout, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = 8 }
    }
}
