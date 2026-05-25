package com.iwatchme.android.demo.shadowplugin

import android.app.Activity
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iwatchme.host.shadow.FixedFilePluginManagerUpdater
import com.iwatchme.host.shadow.HostShadowInitializer
import com.iwatchme.host.shadow.PluginUpdateClient
import com.iwatchme.host.shadow.PluginUpdateService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shadow 插件接入完整 Demo：
 *  ① 后端拉 manager apk → ② 拉插件 zip → ③ HostShadowInitializer 加载 PluginManager
 *  → ④ PluginManager.enter(...) 启动壳子 Activity，承载真实插件 [com.iwatchme.plugin.demo.MemberCenterActivity]
 */
@Composable
fun ShadowPluginDemoScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("等待操作") }
    val deviceId = remember { "demo-device-${System.currentTimeMillis() % 10000}" }
    val service = remember {
        PluginUpdateService(context, PluginUpdateClient.create(BASE_URL))
    }

    fun line(s: String) { status += "\n$s" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Shadow 插件 Demo", style = MaterialTheme.typography.h6)
        Text("后端：$BASE_URL", style = MaterialTheme.typography.caption)
        Text("partKeys：$PART_KEY_PLUGIN / $PART_KEY_MANAGER  deviceId：$deviceId", style = MaterialTheme.typography.caption)

        Card(modifier = Modifier.fillMaxWidth().padding(PaddingValues(2.dp))) {
            Text(
                text = status,
                style = MaterialTheme.typography.body2,
                modifier = Modifier.padding(12.dp),
            )
        }

        Button(
            onClick = {
                scope.launch {
                    val t0 = System.currentTimeMillis()
                    status = "① 拉 manager 元数据 + 下载 apk ..."
                    val managerResult = service.checkAndDownload(PART_KEY_MANAGER, deviceId)
                    val manager = managerResult.getOrElse {
                        status = "manager 下载失败 ❌ ${it.javaClass.simpleName}: ${it.message}"
                        return@launch
                    }
                    val t1 = System.currentTimeMillis()
                    line("①  manager.apk fromCache=${manager.fromCache}  耗时 ${t1 - t0}ms")

                    line("② 拉 plugin 元数据 + 下载 zip ...")
                    val pluginResult = service.checkAndDownload(PART_KEY_PLUGIN, deviceId)
                    val plugin = pluginResult.getOrElse {
                        status += "\nplugin 下载失败 ❌ ${it.javaClass.simpleName}: ${it.message}"
                        return@launch
                    }
                    val t2 = System.currentTimeMillis()
                    line("②  plugin.zip ${plugin.file.length()}B fromCache=${plugin.fromCache}  耗时 ${t2 - t1}ms")

                    runCatching {
                        val pluginManager = withContext(Dispatchers.IO) {
                            HostShadowInitializer.loadPluginManager(
                                FixedFilePluginManagerUpdater(manager.file)
                            )
                        }
                        val t3 = System.currentTimeMillis()
                        line("③  PluginManager loaded（md5 + 反射）  耗时 ${t3 - t2}ms")

                        val args = Bundle().apply {
                            putString("pluginZipPath", plugin.file.absolutePath)
                            putString("KEY_PLUGIN_PART_KEY", "iwatchme-plugin-main")
                            putString("KEY_ACTIVITY_CLASSNAME", "com.iwatchme.plugin.demo.MemberCenterActivity")
                        }
                        // FROM_ID_START_ACTIVITY = 1002（与 :plugin-manager-app/IwatchmePluginConstants 对齐）
                        pluginManager.enter(context as Activity, 1002L, args, null)
                        val t4 = System.currentTimeMillis()
                        line("④  enter() 已发起（installPlugin/bindService/start shell）  耗时 ${t4 - t3}ms")
                        line("📊 总耗时 ${t4 - t0}ms")
                    }.onFailure {
                        status += "\n加载/启动失败 ❌ ${it.javaClass.simpleName}: ${it.message}"
                        HostShadowInitializer.crashGuard.reportCrash(PART_KEY_PLUGIN, it, "enter")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("一键：下载 + 加载 + 打开 MemberCenterActivity")
        }

        Button(
            onClick = {
                scope.launch {
                    status = "仅做下载链路验证（不打开 Activity）"
                    service.checkAndDownload(PART_KEY_PLUGIN, deviceId).fold(
                        onSuccess = { line("plugin.zip ok md5=${it.release.md5.take(8)}… fromCache=${it.fromCache}") },
                        onFailure = { line("失败 ❌ ${it.message}") },
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("仅验证：检查更新 + 下载 plugin.zip")
        }
    }
}

private const val BASE_URL = "http://10.0.2.2:8081/"
private const val PART_KEY_PLUGIN = "demo"
private const val PART_KEY_MANAGER = "plugin-manager"
