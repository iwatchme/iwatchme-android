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

/**
 * singleTask launchMode 演示页。
 *
 * 插件 manifest 声明 launchMode="singleTask"，loader 侧将该类映射到宿主
 * PluginSingleTaskProxyActivity0。连续启动本 Activity 时，应复用同一个壳子实例并触发 onNewIntent。
 */
class SingleTaskProbeActivity : Activity() {

    private val instanceId = System.identityHashCode(this)
    private var createCount = 0
    private var newIntentCount = 0
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createCount++

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(40, 80, 40, 40)
        }

        layout.addView(TextView(this).apply {
            text = "singleTask Plugin Activity"
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        })

        statusText = TextView(this).apply {
            textSize = 14f
            setPadding(0, 0, 0, 32)
        }
        layout.addView(statusText)

        layout.addView(button("再次启动自己，验证 onNewIntent") {
            val intent = Intent().setClassName(packageName, SingleTaskProbeActivity::class.java.name)
                .putExtra(EXTRA_SOURCE, "SingleTaskProbeActivity")
                .putExtra(EXTRA_LAUNCH_INDEX, System.currentTimeMillis())
            startActivity(intent)
        })

        layout.addView(button("关闭 singleTask 页面") {
            finish()
        })

        setContentView(layout, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        renderState("onCreate", intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        newIntentCount++
        setIntent(intent)
        renderState("onNewIntent", intent)
    }

    private fun renderState(event: String, intent: Intent?) {
        val source = intent?.getStringExtra(EXTRA_SOURCE).orEmpty()
        val launchIndex = intent?.getLongExtra(EXTRA_LAUNCH_INDEX, 0L) ?: 0L
        statusText.text = buildString {
            appendLine("event=$event")
            appendLine("instanceId=$instanceId")
            appendLine("onCreate count=$createCount")
            appendLine("onNewIntent count=$newIntentCount")
            appendLine("source=$source")
            appendLine("launchIndex=$launchIndex")
            appendLine("pid=${android.os.Process.myPid()}")
        }
    }

    private fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = 8 }
    }

    companion object {
        const val EXTRA_SOURCE = "singleTaskProbe.source"
        const val EXTRA_LAUNCH_INDEX = "singleTaskProbe.launchIndex"
    }
}
