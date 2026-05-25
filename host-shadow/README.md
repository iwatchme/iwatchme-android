# host-shadow

宿主接入腾讯 Shadow 插件化框架的胶水层。

## 模块定位

- 提供 **网络更新 + 下载 + md5 校验** —— 与后端 `/api/plugins/*` 对接（与 Shadow 内部 API 解耦）
- 提供 **三道生产防线** —— 崩溃计数 / 自动回滚 / 服务端强降级（同样与 Shadow 解耦）
- 提供 **Shadow 集成胶水**（TODO）—— 初始化、Manager Binder 调用、Activity 启动等，依赖 vendor/Shadow 已发到本地 Maven 的 `com.tencent.shadow.dynamic:dynamic-host`

## 文件清单

| 文件 | 与 Shadow 耦合 | 说明 |
| --- | --- | --- |
| `PluginUpdateApi.kt` | ❌ 解耦 | Retrofit 接口定义 |
| `PluginUpdateClient.kt` | ❌ 解耦 | Retrofit 构造，kotlinx.serialization |
| `PluginDownloader.kt` | ❌ 解耦 | OkHttp 流式下载 + md5 |
| `PluginUpdateService.kt` | ❌ 解耦 | 编排：latest → download → 返回本地 zip |
| `safety/PluginCrashGuard.kt` | ❌ 解耦 | 崩溃计数 + 回滚阈值判定 |
| `safety/PluginVersionRegistry.kt` | ❌ 解耦 | SharedPreferences 持久化"当前/稳定"版本号 |
| `safety/PluginDegradeManager.kt` | ❌ 解耦 | 强降级 / 本地失败兜底决策 |
| `HostShadowInitializer.kt`（TODO） | ✅ 耦合 | Shadow Manager 加载、Binder 绑定 |
| `ShadowPluginLauncher.kt`（TODO） | ✅ 耦合 | startPluginActivity / startService 包装 |

## 接入步骤

1. 跑 `tools/setup.sh`，会自动 `tools/shadow-publish.sh` 把 `vendor/Shadow` 发到 `~/.m2/repository`
2. `gradle sync` 后 `:host-shadow` 即可解析 `com.tencent.shadow.*:local` 依赖
3. 业务调用入口（计划）：
   ```kotlin
   val service = PluginUpdateService(context, PluginUpdateClient.create(BASE_URL))
   val downloaded = service.checkAndDownload("demo", deviceId).getOrThrow()
   ShadowPluginLauncher.start(context, downloaded.file, intent)
   ```
