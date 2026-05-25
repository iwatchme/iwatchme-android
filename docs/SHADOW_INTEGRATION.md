# Tencent Shadow 接入历程笔记

> 📘 **接入正式手册见**：
> - 移动端：[`docs/SHADOW_ANDROID.md`](./SHADOW_ANDROID.md)
> - 后端：[`iwatchme-springboot/docs/SHADOW_BACKEND.md`](../../Desktop/Spring/spring/iwatchme-springboot/docs/SHADOW_BACKEND.md)
>
> 本文档保留作为接入过程中**逐步发现的坑与决策过程**的日志，可读性弱于上述两份手册，但记录了每个改动的来龙去脉。

## 现状

| 层 | 状态 | 位置 |
| --- | --- | --- |
| 后端版本服务 | ✅ 5 接口 curl 端到端验证 | iwatchme-springboot/.../shadow/ |
| Shadow submodule | ✅ 已 clone（master） | vendor/Shadow |
| Shadow mavenLocal 发布 | ✅ tools/shadow-publish.sh，19 个 artifact 已发到 ~/.m2 | ~/.m2/repository/com/tencent/shadow/ |
| host-shadow 库 module | ✅ 接入层 + 三道防线 + 壳子 Activity 子类，编译过 | host-shadow/ |
| :app 接入 | ✅ Manifest 壳子声明 + MainApplication 初始化 + Demo 入口，manifest 合并过 | app/ |
| plugin-manager-app | ✅ IwatchmePluginManager + FastPluginManager + framework 固定入口，产出 APK | plugin-manager-app/ |
| plugin-loader-app | ✅ IwatchmePluginLoader + ComponentManager 映射，产出 APK | plugin-loader-app/ |
| plugin-runtime-app | ✅ 产出 APK（Pattern B：proxy Activity 实际在 host-shadow） | plugin-runtime-app/ |
| plugin-demo | ✅ MemberCenterActivity + Shadow Transform，产出 plugin variant APK | plugin-demo/ |
| 插件 zip 打包 | ✅ `packageDebugPlugin` 产出 iwatchme-plugin-debug.zip（含 4 个文件 + config.json） | build/iwatchme-plugin/ |

## 首次接入步骤（换机 / fresh clone）

```bash
# 1. 初始化 submodule（含 vendor/Shadow）
./tools/setup.sh
# 上面这步会自动调 ./tools/shadow-publish.sh 把 Shadow SDK 发到 ~/.m2/repository

# 2. 起后端（需要 JDK 21 + MySQL）
cd ../iwatchme-springboot
./mvnw spring-boot:run

# 3. sync + 跑 Android
cd -
./gradlew :app:assembleDebug
./gradlew :app:installDebug
# 进 App → "Shadow Plugin Update" demo → 点「检查更新 + 下载 + md5 校验」
```

## 端到端验证（Demo 链路）

需要先在后端创建一条 `plugin_release` 记录并放对应 zip：

```bash
# 准备一个空 zip 测试（真插件流程见下方"打 plugin-demo"）
echo "stub" > stub.txt && zip demo-stub.zip stub.txt && rm stub.txt

# 上传到后端
curl -X POST \
  -H "X-Admin-Token: dev-change-me" \
  -F "file=@demo-stub.zip" \
  -F 'meta={"versionName":"1.0.0","versionCode":1,"rolloutPercent":100,"releaseNotes":"stub"}' \
  http://localhost:8081/api/plugins/demo/upload

# 验证 latest
curl 'http://localhost:8081/api/plugins/demo/latest?deviceId=test-1'
```

Android Demo 屏幕点「检查更新 + 下载 + md5 校验」应展示成功。

## 剩余工作（推荐分别推进）

### Task A：plugin-runtime-app —— PluginRuntimeContainer 适配

参考 `vendor/Shadow/projects/sample/source/sample-plugin/sample-runtime/`，主要文件：

- `SampleComponentManager.java`（包名要对齐 `com.iwatchme.host.shadow.container.PluginDefaultProxyActivity` 等 4 类壳子的 `getDelegateProviderKey()` 返回值 `"iwatchme-plugin-default"`）
- `SampleHostActivityDelegator` / `SamplePluginActivity` 等

### Task B：plugin-loader-app —— PluginLoaderImpl + ComponentMapping

参考 `vendor/Shadow/projects/sample/source/sample-plugin/sample-loader/`。核心是配置「插件 Activity ↔ 宿主壳子」映射，例如：

```kotlin
ComponentMapping.Builder()
    .addActivity("com.iwatchme.plugin.demo.MemberCenterActivity",
                 "com.iwatchme.host.shadow.container.PluginDefaultProxyActivity0")
    .build()
```

### Task C：plugin-manager-app —— PluginManagerImpl

参考 `vendor/Shadow/projects/sample/source/sample-manager/`。要点：

- 继承 `BasePluginManager`
- 实现 zip 解压 / partKey 校验 / oat 优化
- 用 `installPluginFromZip()` 把后端下载的 zip 喂给框架

### Task D：plugin-demo —— Shadow Transform

```kotlin
// plugin-demo/build.gradle.kts
plugins {
    id("iwatchme.android.application")
    id("com.tencent.shadow.plugin")  // 需要 Shadow gradle-plugin 已发到 mavenLocal
}

android {
    defaultConfig {
        aaptOptions {
            additionalParameters("--package-id", "0x7e", "--allow-reserved-package-id")
        }
    }
}

shadow {
    transform {
        useDefaultConfig()
    }
    packagePlugin { /* ... 参考 sample-plugin/build.gradle */ }
}
```

写一个最简插件 Activity 测试 transform 是否成功。

### Task E：HostShadowInitializer.loadPluginManager 真实接线

`host-shadow/.../HostShadowInitializer.kt` 当前用反射创建 `DynamicPluginManager`——在 mavenLocal 解析成功后改成直接 import：

```kotlin
import com.tencent.shadow.dynamic.host.DynamicPluginManager
import com.tencent.shadow.dynamic.host.PluginManagerUpdater
// ...
return DynamicPluginManager(updater)
```

并在 `ShadowPluginDemoScreen` 里把下载好的 manager.apk 喂给它，调 `pluginManager.enter(...)` 启动插件。

## 已知坑 / 经验

- **Shadow 内部 build_gradle_version = 7.4.2** 与我们项目的 AGP 8.12.0 不兼容，因此走 mavenLocal 发布而非 includeBuild
- **JDK 必须用 17 发布 Shadow**（Gradle 7.5 不支持 JDK 21）
- **wrapper 默认指向 `mirrors.tencent.com`**（已退役域名），脚本会把它改成 `mirrors.cloud.tencent.com` 国内可用
- **submodule 会因 wrapper URL 改动出现一行 dirty**——这是预期行为
- **Shadow lint 较严**，Activity 类必须用 `@SuppressLint("Registered")`，否则被报"未在本模块 manifest 注册"
- **Shadow runtime 强制依赖 Android 13 API**：`GeneratedHostActivityDelegator` / `GeneratedPluginContainerActivity` 引用 `android.window.OnBackInvokedDispatcher`（API 33），导致 `PluginDefaultProxyActivity` 在 **API < 33** 的设备上类加载阶段 LinkageError。真机/模拟器验证必须 API 33+。
- **Shadow Transform 插件顺序敏感**：`com.android.application` 先 → `com.tencent.shadow.plugin` → `org.jetbrains.kotlin.android`。`iwatchme.android.application` convention plugin 会隐式带入 kotlin-android 破坏顺序，所以 `plugin-demo` module 不走 convention，手写 build.gradle.kts。
- **DynamicPluginManager 构造时 getLatest() 必须返回非空 file**，否则抛 IllegalArgumentException。所以 manager.apk 必须先下载到本地再 loadPluginManager()。
- **`host-shadow` 里所有会被 :plugin 进程加载的 Kotlin 代码不能用 `runCatching` / 高阶函数**。Kotlin 编译这些会生成 `kotlin.jvm.internal.CallableReference`，在 API 37+ 多 dex + :plugin 进程隔离下其 `<clinit>` 抛 `IllegalAccessError`（CallableReference 无法访问自己的 inner class `$NoReceiver`），让整个 :plugin 进程秒崩，次生导致 host 端 `bindService` 永远 10s 超时。修法：plain `try/catch` 替代 `runCatching`，避免 lambda。受影响文件：`HostShadowInitializer`、`AndroidLogLoggerFactory.formatSafely`。
- **Shadow LoggerFactory 是全局静态**：必须在主进程 AND :plugin 进程都调 `setILoggerFactory`，否则 :plugin 里 `DynamicRuntime.<clinit>` 调 `getLogger` 时抛 `RuntimeException("没有找到 ILoggerFactory 实现")`。
- **`HostShadowInitializer.loadPluginManager` 每次都释放旧 DynamicPluginManager**：避免上次插件崩溃后留下的 ServiceConnection 残留导致下一次 bindService 拿不到 onServiceConnected。
- **壳子 Activity 必须设独立 `taskAffinity`，否则插件崩溃会清掉整个 task（含宿主 MainActivity）**。即使 `android:process=":plugin"` 隔离了进程，但 task 默认按 applicationId 归集——`:plugin` 进程的 Activity 跟 MainActivity 在同一 task 25。ActivityManager 的 `finishTopCrash` 一杀全杀，用户被弹"keeps stopping"对话框然后跳桌面。修法：所有 `PluginXxxProxyActivity*` 在 `:app/AndroidManifest.xml` 里加 `android:taskAffinity=":plugin.task" android:excludeFromRecents="true"`，让插件 task 与宿主 task 物理隔离。

## 性能优化

- **App 启动时 `MessageQueue.IdleHandler` 预下载**：`MainApplication.onCreate` 主进程分支调用 `PluginPreloader.schedule()`，IdleHandler 空闲时后台下载 `plugin-manager` + `demo` 两个 partKey。冷启后 ~8 秒内完成（实测 manager 282ms + plugin 546ms），用户进 demo 屏点「一键」时直接 `fromCache=true`。把"首次冷启 5–10s"压到 ~1.5s（剩余成本是 Shadow installPlugin + 起 :plugin 进程，绕不开）。
- **`HostShadowInitializer.loadPluginManager` 按 manager.apk 路径缓存复用**：同 path 直接返回 cached `DynamicPluginManager`，省一次 `md5File()` IO + `ManagerFactoryImpl.newInstance()` 反射构造。即使 :plugin 崩过，`mPpsController` 会被 `onServiceDisconnected` 置空，下次 enter() 自动 rebind，不会拿到陈旧 binder。manager.apk 升级到新 versionCode 时（文件路径变化）才释放旧实例并重建。
