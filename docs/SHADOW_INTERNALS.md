# Shadow 底层技术分析

底层视角剖析 Tencent Shadow 如何把"一份独立 App 的 APK 不重新签名、不安装、不 Hook 系统"加载进宿主进程并完整运行。每节都引用我们工程 `vendor/Shadow/` 与 `host-shadow/`、`plugin-*` 模块里的真实代码。

---

## 1. 问题与思路

**问题**：Android 不允许加载未注册到 `PackageManager` 的 Activity。一个未安装的 APK 里的 `MainActivity` 启动会被 AMS 直接拒绝。

**历史方案**：DroidPlugin / VirtualAPK 走 Hook 路线，反射改 ActivityThread 的 `Instrumentation` 或 AMS Binder Proxy，在 `startActivity` 时把插件 Activity 偷换成宿主里预注册的占坑 Activity，回调时再换回来。Android 9 引入 hidden API 限制 + 每版本 AMS 改字段，让这条路维护成本指数上升。

**Shadow 的根本转向**：不跟系统对抗，**让系统真的启动一个宿主的合法 Activity（壳子），壳子在用户态把生命周期转给伪装成 Activity 的普通对象**。整个机制：

- 编译期：用 ASM/Javassist 把插件代码里所有 `extends android.app.Activity` 改成 `extends com.tencent.shadow.core.runtime.ShadowActivity`（普通 Java 类，**不是真 Activity**）
- 运行时：宿主里有真 Activity 叫 `PluginContainerActivity`，它持有一个 `HostActivityDelegate`（也就是被 Transform 改造过的 ShadowActivity 实例），把每个生命周期回调（`onCreate/onResume/...`）转发给 delegate
- 系统视角：永远只看见 `PluginContainerActivity`（合法注册），完全不知道有"插件"
- 插件视角：自己的代码里 `this` 还是个 Activity，调 `setContentView/finish/getResources` 都被代理类拦截转发到宿主壳子

**结果**：组件启动链路不 Hook AMS/Instrumentation，也不依赖 hidden API。动态 APK 入口仍会使用 `DexClassLoader` + 固定类名反射，这是 Shadow 的公开扩展机制。

---

## 2. 整体架构

我们工程的 4 个 APK + host 进程结构：

```
宿主进程 (com.iwatchme.android)            插件进程 (com.iwatchme.android:plugin)
─────────────────────────────────         ─────────────────────────────────
:app                                       Manifest 声明的 android:process=":plugin"
  ├ MainActivity                           ┌─────────────────────────┐
  ├ PluginUpdateService                    │ IwatchmePluginPPS       │
  ├ dynamic-host.aar                       │  Binder 服务             │
  │  └ DynamicPluginManager                ├─────────────────────────┤
  └ manager.apk (DexClassLoader)           │ Shadow runtime          │
     └ IwatchmePluginManager ── Binder ──→ │  (runtime.apk)          │
                                            ├─────────────────────────┤
宿主 APK 中预注册的壳子类：                  │ IwatchmePluginLoader    │
  PluginDefaultProxy*                      │  (loader.apk)           │
  PluginSingleTaskProxy*      AMS 启动实例 →├─────────────────────────┤
  PluginSingleInstance*                    │ plugin-demo.apk:        │
  （组件实例因 manifest 运行在 :plugin）     │  MemberCenterActivity   │
                                            │  PluginDemoService      │
                                            │  PluginMemberProvider   │
                                            └─────────────────────────┘
```

**关键拆分**：

- `:host-shadow` 随宿主安装：提供更新器、壳子组件和跨进程 Service；壳子类在宿主 APK 里，但 Activity 实例因 manifest 配置运行于 `:plugin`
- `plugin-manager-app` 单独产出 manager.apk，用 `partKey=plugin-manager` 下发；它不进业务插件 zip。`DynamicPluginManager` 在宿主主进程加载它，得到 `IwatchmePluginManager`，再由 manager 通过 Binder 调度 `:plugin`
- 业务插件 zip 只包含 `plugin-loader-app` / `plugin-runtime-app` / `plugin-demo` / `config.json`，由 manager 解包后在 `:plugin` 进程用自定义 ClassLoader 链动态加载
- 宿主 host 进程跟 `:plugin` 进程通过 `IwatchmePluginProcessService` 的 Binder 通信

---

## 3. 一次插件启动的完整时序

直接看我们真机抓到的 logcat（端到端 cache hit 场景，~135ms）：

```
10:33:18.967  host    DynamicPluginManager           enter fromId:1002
10:33:18.969  host    DynamicPluginManager           md5File(manager.apk) → 反射 ManagerFactoryImpl
                                                     ↓ buildManager(context) → IwatchmePluginManager
10:33:18.970  host    IwatchmePluginManager          onStartActivity(...) → executor.execute{...}
10:33:18.972  host    FastPluginManager              installPluginFromZip(zipPath)
                                                     ├─ unzip 到 ShadowPluginManager/UnpackedPlugin/<md5>/
                                                     ├─ oDexPluginLoaderOrRunTime (runtime.apk / loader.apk)
                                                     ├─ oDexPlugin (plugin-demo.apk)
                                                     └─ extractSo (libs/arm64/*)
10:33:19.020  host    BaseDynamicPluginManager       bindPluginProcessService IwatchmePluginPPS
10:33:19.022  Zygote                                 fork → pid=12345 :plugin 进程
10:33:19.045  plugin  IwatchmePluginPPS              onCreate / onBind
10:33:19.046  host    BaseDynamicPluginManager       onServiceConnected → mPpsController 拿到 Binder
10:33:19.046  plugin  IwatchmePluginPPS              setUuidManager (host 反向给一个 Binder 用于查 apk)
10:33:19.047  host    PluginManagerThatUseDynamic    loadRunTime(uuid)  ──Binder──→
10:33:19.048  plugin  IwatchmePluginPPS              loadRuntime: DexClassLoader runtime.apk
                                                       ↳ DynamicRuntime.setUpPluginContext
10:33:19.051  host                                   loadPluginLoader(uuid)  ──Binder──→
10:33:19.052  plugin                                 反射 CoreLoaderFactoryImpl.build()
                                                       ↳ new IwatchmePluginLoader(hostContext)
                                                       ↳ 注册 ComponentManager (Activity/Service 映射)
10:33:19.080  plugin  ShadowPluginLoader             start loadPlugin (LoadPluginBloc)
                                                       ├─ buildClassLoader: PluginClassLoader 链
                                                       ├─ buildPluginManifest: 解析 plugin-demo.apk 的 manifest
                                                       ├─ buildPluginApplicationInfo
                                                       └─ buildPluginPackageManager
10:33:19.090  host    PluginLoader                   callApplicationOnCreate(partKey)
                                                       ↳ plugin Application.onCreate (在 :plugin 跑)
10:33:19.092  host    IwatchmePluginManager          convertActivityIntent(pluginIntent)
                                                       ├─ ComponentManager.onBindContainerActivity
                                                       │  → "com.iwatchme.host.shadow...PluginDefaultProxyActivity0"
                                                       └─ intent.putExtra("CM_CLASS_NAME_KEY", 原插件类名)
                                                          intent.putExtra("LOADER_VERSION_KEY", ...)
                                                          intent.putExtra("PROCESS_ID_KEY", ...)
10:33:19.093  host    PluginLoader                   startActivityInPluginProcess(intent)
                                                       ──Binder──→ :plugin 用插件 context startActivity
10:33:19.094  AMS                                    解析 intent: 目标是 com.iwatchme.android/PluginDefaultProxyActivity0
                                                       这是宿主合法注册的 Activity，启动它
10:33:19.097  plugin  PluginContainerActivity        构造（super 调用之前先建 hostActivityDelegate）
                                                       ↳ DelegateProviderHolder.get("iwatchme-plugin-default")
                                                       ↳ → IwatchmePluginLoader.getHostActivityDelegate
                                                       ↳ → new ShadowActivityDelegate
10:33:19.098  plugin  PluginContainerActivity        onCreate(savedState)
                                                       ↳ hostActivityDelegate.onCreate(savedState)
                                                       ↳ 从 intent 取 CM_CLASS_NAME_KEY
                                                       ↳ pluginClassLoader.loadClass("MemberCenterActivity")
                                                       ↳ newInstance() + bindHost(this)
                                                       ↳ pluginActivity.onCreate(savedState)
10:33:19.099  plugin  MemberCenterActivity           setContentView(...) ──委托──→ host.setContentView
10:33:19.102  AMS                                    Displayed PluginDefaultProxyActivity0 +43ms
```

后面几节按这条时序展开关键机制。

---

## 4. ClassLoader 隔离与白名单

Shadow 内部 ClassLoader 树（来自 `PluginClassLoader.kt:30-43` 注释）：

```
                  BootClassLoader (系统类)
                          │
              ┌───────────┴───────────┐
              │                       │
        PathClassLoader         (Shadow runtime
        (宿主 :app classes)      .apk 的加载点)
              │                       │
              │           PluginClassLoaderA (插件A)
              │                       │
              └─→ specialClassLoader ─┘
                          │
                  CombineClassLoader (代理多个插件)
                     │              │
        PluginClassLoaderB     PluginClassLoaderC
            (插件B)                (插件C)
```

**核心**：`com.tencent.shadow.core.loader.classloaders.PluginClassLoader` 继承 `BaseDexClassLoader`，但 `loadClass` 被重写，**不再走标准双亲委派**：

```kotlin
// vendor/Shadow/.../loader/classloaders/PluginClassLoader.kt:77
override fun loadClass(className: String, resolve: Boolean): Class<*> {
    var clazz: Class<*>? = findLoadedClass(className)
    if (clazz == null) {
        if (specialClassLoader == null) {
            return super.loadClass(className, resolve)  // 双亲委派
        }
        try {
            clazz = findClass(className)                 // 优先从自己 dex 找
        } catch (e: ClassNotFoundException) {
            if (allHostWhiteTrie.contains(className)) {
                clazz = loaderClassLoader.loadClass(className)  // 白名单：从宿主加载
            } else {
                clazz = specialClassLoader.loadClass(className) // 否则去其它插件
            }
        }
    }
    return clazz
}
```

**关键设计**：

- **优先 findClass**：避免插件代码意外用到宿主同名类（如插件自带 OkHttp 4.x、宿主带 OkHttp 5.x，要让插件用自己的）
- **`hostWhiteList`** 是宿主自己声明的白名单类前缀，列在 `plugin-manager-app/.../com/tencent/shadow/dynamic/impl/WhiteList.java`：

  ```java
  public interface WhiteList {
      String[] sWhiteList = new String[]{
          "com.iwatchme.host.shadow",
          "com.iwatchme.android",
      };
  }
  ```

  插件想用宿主的 `HostShadowInitializer.crashGuard` 等单例，前缀必须在这里
- **`specialClassLoader`**：允许多个插件 APK 互相访问（如 plugin-base 提供 lib，plugin-app 用），通过 `CombineClassLoader` 路由

我们工程目前只用单插件（plugin-demo），`hostWhiteList` 只 2 项；多插件场景下白名单设计直接决定隔离粒度。

---

## 5. 编译期字节码替换（Transform）

Shadow Transform 在编译期用 Javassist 改写插件字节码。注册的 Transform 见 `vendor/Shadow/.../transform/TransformManager.kt:25-44`，共 16 个，最核心的 `ActivityTransform.kt` 极简：

```kotlin
class ActivityTransform : SimpleRenameTransform(
    mapOf(
        "android.app.Activity"    to "com.tencent.shadow.core.runtime.ShadowActivity",
        "android.app.NativeActivity" to "com.tencent.shadow.core.runtime.ShadowNativeActivity"
    )
)
```

`SimpleRenameTransform` 干的事：扫所有 class，把 `extends android.app.Activity` 改成 `extends com.tencent.shadow.core.runtime.ShadowActivity`，同时把所有引用 `android.app.Activity` 类型的字段、方法参数、`instanceof` 检查都改写。

我们 `plugin-demo/.../MemberCenterActivity.kt`：

```kotlin
class MemberCenterActivity : Activity() { ... }
```

经 Transform 之后，编译进 `plugin-demo-plugin-debug.apk` 的字节码相当于：

```kotlin
class MemberCenterActivity : ShadowActivity() { ... }
```

`ShadowActivity` 是 **普通 Java 类**（虽然名字像 Activity），不继承 `android.app.Activity`，而是组合 `HostActivityDelegator`（持有真壳子 Activity）。

完整 Transform 列表（每个对应一类需要被代理的系统类）：

| Transform | 改写目标 |
| --- | --- |
| `ApplicationTransform` | `android.app.Application` → `ShadowApplication` |
| `ActivityTransform` | `Activity` → `ShadowActivity` |
| `ServiceTransform` | `android.app.Service` → `ShadowService` |
| `ContentProviderTransform` | `android.content.ContentProvider` → `ShadowContentProvider` |
| `IntentServiceTransform` | `IntentService` → `ShadowIntentService` |
| `FragmentSupportTransform` | androidx Fragment 相关 |
| `WebViewTransform` | `WebView` → 注入插件 Context |
| `LayoutInflaterTransform` | 改 LayoutInflater 让它从插件 Resources 加载布局 |
| `PackageManagerTransform` | `getPackageManager` 返回插件的 PackageManager |
| `KeepHostContextTransform` | 标注哪些类用宿主 Context（白名单驱动） |
| `ReceiverSupportTransform` | BroadcastReceiver 注册路由 |
| `InstrumentationTransform` | 把 Instrumentation 调用路由到 Shadow |
| `AppComponentFactoryTransform` | API 28+ 的 AppComponentFactory |
| `PackageItemInfoTransform` | meta-data 读取 |
| `DialogSupportTransform` | Dialog Context |
| `ActivityOptionsSupportTransform` | ActivityOptions API 替换 |

**这一步纯字节码级**：插件源码不需要改一行，开发者写代码时还是继承 `Activity`、调 `startActivity` —— Transform 把这些调用统一改成走 Shadow 的代理类。

我们 `plugin-demo/build.gradle.kts` 启用 Transform 的方式：

```kotlin
plugins {
    alias(libs.plugins.android.application)       // 1. 必须先
    alias(libs.plugins.tencent.shadow.plugin)     // 2. shadow（kotlin-android 前）
    alias(libs.plugins.kotlin.android)            // 3.
    alias(libs.plugins.kotlin.compose)            // 4.
}
```

`com.tencent.shadow.plugin` 注册的 task `transformClassesWithShadowFor<Variant>` 会在 `compileKotlin` 之后、`dexBuilder` 之前对所有 class 跑一遍 Javassist。

---

## 6. 容器代理三件套：壳子 / Delegator / Delegate

Shadow 用三层抽象解耦"宿主合法 Activity"和"被 Transform 改造的插件类"。

```
┌──────────────────────────────────────┐    ┌──────────────────────────────────┐
│ PluginContainerActivity (真 Activity) │    │ MemberCenterActivity            │
│  在宿主 manifest 注册                  │    │  (插件代码，编译后 extends      │
│  系统调它的 onCreate/onResume/...      │    │   ShadowActivity，是普通类)     │
│                                       │    │                                  │
│ ┌──────────────────────────────────┐ │    │ ┌──────────────────────────────┐ │
│ │ HostActivityDelegator 接口 (实现) │←┼──→│ │ ShadowActivity (PluginActivity)│
│ │ 提供 setContentView/getResources │ │    │ │ 实现 HostActivityDelegate    │ │
│ │ /startActivity 给 Delegate 调    │ │    │ │ 接口，持有 Delegator         │ │
│ └──────────────────────────────────┘ │    │ │ 引用                          │ │
│                                       │    │ └──────────────────────────────┘ │
│ HostActivityDelegate 引用 ──双向──── │←──→│ HostActivityDelegator 引用       │
└──────────────────────────────────────┘    └──────────────────────────────────┘
        (host APK 里)                              (plugin APK 里)
```

**三类角色**：

| 角色 | 类 | 在哪个 APK | 实际身份 |
| --- | --- | --- | --- |
| 壳子 | `PluginContainerActivity` + 我们的 `PluginDefaultProxyActivity0..3` 等 | host APK（在 host-shadow library 里） | 真 Activity，系统启动它 |
| Delegator（系统能力提供方） | `HostActivityDelegator` | runtime.apk + 由壳子实现 | Interface，定义 setContentView/startActivity 等 |
| Delegate（生命周期接收方） | `HostActivityDelegate` + 我们的 `MemberCenterActivity`（经 Transform） | plugin.apk + runtime.apk | Interface，定义 onCreate/onResume/... |

**关键代码**（`vendor/Shadow/.../activity-container/PluginContainerActivity.java:26-71`）：

```java
public class PluginContainerActivity extends GeneratedPluginContainerActivity
        implements HostActivity, HostActivityDelegator {

    HostActivityDelegate hostActivityDelegate;

    public PluginContainerActivity() {
        // 构造期就建立 delegate（onCreate 前）
        DelegateProvider provider = DelegateProviderHolder.getDelegateProvider(getDelegateProviderKey());
        if (provider != null) {
            HostActivityDelegate delegate = provider.getHostActivityDelegate(this.getClass());
            delegate.setDelegator(this);        // 把自己（壳子）传给 delegate
            this.hostActivityDelegate = delegate;
        }
    }

    @Override
    final protected void onCreate(Bundle savedInstanceState) {
        if (isIllegalIntent(savedInstanceState)) {
            finish(); System.exit(0); return;  // 防御：插件已升级，旧 Intent 来的就退出
        }
        if (hostActivityDelegate != null) {
            hostActivityDelegate.onCreate(savedInstanceState);  // 转发给 delegate
        }
    }
    // onResume / onPause / onSaveInstanceState ... 全部同上
}
```

**注意几点**：

1. **`onCreate` 是 `final`** —— 业务壳子（如我们的 `PluginDefaultProxyActivity0`）不能重写，只能改 `getDelegateProviderKey()` 决定走哪套 delegate（我们工程统一返回 `"iwatchme-plugin-default"`）
2. **构造函数里就建 delegate** —— 因为 `super.onCreate()` 之前需要把 `pluginActivity` 接好，否则 `savedInstanceState` 路由不过去
3. **`isIllegalIntent` 防御**：旧 PendingIntent + 插件已升级，Intent extras 会对不上版本号，直接 `System.exit(0)` 退出 :plugin 进程（避免崩溃恢复进入死循环）
4. **`super.hostActivityDelegate`** 是 `GeneratedPluginContainerActivity` 里那份，是 Shadow 代码生成器写的；外层这份是再赋一次给自己

**Delegate 是怎么把生命周期转给插件 Activity 的**：

`HostActivityDelegate` 的标准实现里，`onCreate(savedState)` 干这几件事：

1. 从 `getIntent()` 取出 `CM_CLASS_NAME_KEY` 拿到真实插件类名 `com.iwatchme.plugin.demo.MemberCenterActivity`
2. 用 `pluginClassLoader.loadClass(className).newInstance()` 实例化
3. 实例是个 `ShadowActivity` 子类，调 `pluginActivity.setHostActivityDelegator(this)`（让插件代码反过来能调用壳子的 setContentView 等）
4. 调 `pluginActivity.onCreate(savedState)` —— 插件代码以为自己是 Activity，开始跑业务

`ShadowActivity` 里所有 Activity 方法都委托给 `hostActivityDelegator`，例如：

```java
// PluginActivity.java:106-115
public void setTheme(int resid) {
    hostActivityDelegator.setTheme(resid);     // 转发给壳子
}

public WindowManager getWindowManager() {
    return hostActivityDelegator.getHostActivity().getImplementActivity().getWindowManager();
}
```

**`getDelegateProviderKey` 的作用**：单宿主同时跑多套 Shadow framework 时（场景：同一个 App 装了多个 Loader 版本），通过 key 区分。我们工程统一一套 key `"iwatchme-plugin-default"`，定义在：

- `host-shadow/.../container/PluginDefaultProxyActivity.kt:16,19`：壳子 Activity 返回的 key
- `plugin-loader-app/.../IwatchmePluginLoader.kt:18`：Loader 的 `override val delegateProviderKey`

两边必须**字面相同**，否则壳子构造时 `DelegateProviderHolder.getDelegateProvider(key)` 返回 null，进 `finish() + System.exit(0)`。

---

## 7. 跨进程：Binder 桥

`:plugin` 进程的存在是为了**隔离插件代码崩溃**。但宿主端的 PluginManager 跑在主进程，怎么把"加载插件、启动 Activity"这些命令送过去？走 `IwatchmePluginProcessService`（继承自 Shadow 的 `PluginProcessService`）的 Binder。

```
host 进程                                  :plugin 进程
─────────────────────                      ─────────────────────
IwatchmePluginManager                      IwatchmePluginProcessService
   ↓                                          ↑
bindPluginProcessService("...IwatchmePPS") ──→ AMS 创建 :plugin 进程，启动 Service
   ↓                                          ↓
PpsController (Binder Proxy) ←────────────  PpsBinder (Binder Stub)
   ↓
mPpsController.loadRunTime(uuid)
mPpsController.loadPluginLoader(uuid)        ↓
   ↓                                       new IwatchmePluginLoader(context)
mPpsController.getPluginLoader() ──────→    通过 Binder 返回 BinderPluginLoader
   ↓
mPluginLoader.convertActivityIntent(...)
mPluginLoader.startActivityInPluginProcess(intent)
                       ──Binder──→         pluginContext.startActivity(intent)
                                            （这是 :plugin 进程里的 Context，AMS 看见的
                                              是 pid=:plugin 启的 Activity）
```

**关键文件**：

| 角色 | 路径 | 进程 |
| --- | --- | --- |
| 宿主端 Manager | `plugin-manager-app/.../IwatchmePluginManager.kt` | host 主进程（由 `DynamicPluginManager` 反射加载） |
| 跨进程 Service | `host-shadow/.../IwatchmePluginProcessService.kt` | :plugin |
| Binder 协议 Stub | Shadow 自带 `PluginProcessService.kt` | :plugin |
| Binder Proxy | Shadow 自带 `PpsController.kt` | host 主进程 |

`HostShadowInitializer.init` 双进程都会跑（因为 `MainApplication.onCreate` 在 host 主进程和 :plugin 进程都执行），但分支不同：

```kotlin
// host-shadow/.../HostShadowInitializer.kt:37-65
fun init(application: Application) {
    val isPlugin = isPluginProcess(application)
    LoggerFactory.setILoggerFactory(AndroidLogLoggerFactory)    // 两进程都设
    if (isPlugin) {
        // :plugin 进程：插件崩溃后系统拉起需要 recoveryRuntime 重建 Shadow runtime
        try { DynamicRuntime.recoveryRuntime(application) }
        catch (t: Throwable) { /* 包 try/catch 是因为 Kotlin runCatching 在 :plugin 多 dex 下会 LinkageError */ }
        return
    }
    // host 主进程：只初始化崩溃监控等业务
    crashGuardInternal = PluginCrashGuard()
    ...
}
```

**特别坑**：在 `:plugin` 进程加载的宿主代码（`host-shadow` 的类）不能用 `runCatching {}` 这种 Kotlin 高阶函数，因为 API 37+ 的 `:plugin` 进程隔离 + 多 dex 会导致 `kotlin.jvm.internal.CallableReference.<clinit>` 抛 `IllegalAccessError`。具体原因和修复见 `docs/SHADOW_ANDROID.md` §8.2。

---

## 8. 资源隔离：packageId + 独立 Resources

Android 资源 ID 的 32 位结构：`0xPPTTEEEE`，PP 是 packageId。默认 App 用 0x7f。如果插件也用 0x7f，加载到宿主进程后两套资源的 ID 会冲突。

Shadow 解法（编译期）：在 `plugin-demo/build.gradle.kts:32` 给 aapt 传参，把插件资源 packageId 改成 0x7e：

```kotlin
aaptOptions {
    additionalParameters("--package-id", "0x7e", "--allow-reserved-package-id")
}
```

aapt 会重新分配所有插件资源的 ID 段到 0x7e000000 ~ 0x7effffff，不再与宿主的 0x7f 重叠。

**运行时**：插件有独立的 `Resources` 实例（不是宿主的）。在 `LoadPluginBloc.kt` 里：

```kotlin
// 简化伪码
val pluginResources = Resources(
    AssetManager().apply { addAssetPath(installedApk.apkFilePath) },
    hostResources.displayMetrics,
    hostResources.configuration
)
```

插件代码调 `getResources()` 经过 Transform 后实际调的是 `ShadowActivity.getResources()`，它返回上面这份 plugin-only 的 Resources。`R.layout.activity_member_center` 引用的 ID 是 `0x7e010001` 之类，在 plugin Resources 里能查到，与宿主 0x7f 段毫无交集。

**LayoutInflater 一致性**：插件代码用的 LayoutInflater 也经 `LayoutInflaterTransform` 替换为 `ShadowLayoutInflater`，它拿插件 Resources 解析 XML，避免布局里引用资源时跑去查宿主资源。

---

## 9. ContentProvider 路由

ContentProvider 的难点：authority 必须在 manifest 静态注册，AMS 启动时才能解析；插件 manifest 系统不认。

**Shadow 方案**：宿主**预注册一个壳子 ContentProvider**（`PluginContainerContentProvider`），所有插件 Provider 的 query 都先路由到这个壳子，再由壳子根据 URI 路径分发到插件真正的 Provider。

宿主 manifest 声明（在 `host-shadow/src/main/AndroidManifest.xml:104-108`）：

```xml
<provider
    android:name="com.tencent.shadow.core.runtime.container.PluginContainerContentProvider"
    android:authorities="${applicationId}.shadow.provider.dynamic"
    android:exported="false"
    android:grantUriPermissions="true"
    android:process=":plugin" />
```

插件 manifest 声明（`plugin-demo/src/main/AndroidManifest.xml:12-15`）：

```xml
<provider
    android:name="com.iwatchme.plugin.demo.PluginMemberProvider"
    android:authorities="com.iwatchme.plugin.demo.members"
    android:exported="false" />
```

**调用路径**（我们工程实测）：

```
MemberCenterActivity 调
contentResolver.query(Uri.parse("content://com.iwatchme.plugin.demo.members/members"), ...)
   ↓
ShadowContentResolver (Transform 改写过的 ContentResolver) 拦截
   ↓
通过 ComponentManager.onBindContainerContentProvider 查到对应壳子 authority:
  com.iwatchme.android.shadow.provider.dynamic
   ↓
转写 URI 为 content://com.iwatchme.android.shadow.provider.dynamic/...
但 path 里编码了原 authority "com.iwatchme.plugin.demo.members"
   ↓
AMS 把 query 路由到 PluginContainerContentProvider.query
   ↓
PluginContainerContentProvider 拆出原 authority，从 PluginContentProviderManager 取
PluginMemberProvider 实例（已经在 plugin Application init 阶段构造好）
   ↓
PluginMemberProvider.query → MatrixCursor 返回 3 行
```

我们 `plugin-loader-app/.../IwatchmeComponentManager.kt:24` 提供了 authority 映射：

```kotlin
override fun onBindContainerContentProvider(pluginContentProvider: ComponentName): ContainerProviderInfo {
    return ContainerProviderInfo(
        "com.tencent.shadow.core.runtime.container.PluginContainerContentProvider",
        "${context.packageName}.shadow.provider.dynamic",
    )
}
```

任意多少个插件 ContentProvider 都映射到同一个壳子 authority；Shadow 内部根据 URI path 分发。

---

## 10. Service / BroadcastReceiver

**Service 路径**（验证过，详见 `plugin-demo/.../PluginDemoService.kt`）：

跟 ContentProvider 不同，**Service 不需要预注册壳子**。`com.tencent.shadow.core.loader.managers.ComponentManager.startService(context, intent)` 拦截：

```kotlin
// vendor/Shadow/.../ComponentManager.kt:101-114
override fun startService(context: ShadowContext, service: Intent): Pair<Boolean, ComponentName?> {
    if (service.isPluginComponent()) {
        val component = mPluginServiceManager!!.startPluginService(service)
        if (component != null) return Pair(true, component)
    }
    return Pair(false, service.component)
}
```

`PluginServiceManager` 自己维护一个 Service 实例池（不走 AMS），用 Handler 模拟生命周期（`onCreate / onStartCommand / onBind / onDestroy`），跑在 `:plugin` 进程的主线程。**插件 Service 不是真 Service**，但插件代码看不出来。

实测：我们的 `PluginDemoService` 用 `kotlinx.coroutines.delay()` 跑 5 个 tick，全程在 `DefaultDispatcher-worker-N` 线程，PID 跟壳子 Activity 一致（都在 :plugin 进程）。

**BroadcastReceiver**：动态注册的（`registerReceiver`）走 `ShadowContext` 拦截路由；静态注册的（manifest 声明）通过 `ReceiverSupportTransform` 改写。框架在 `ShadowPluginLoader.loadPlugin` 完成时遍历插件 manifest 里的 `<receiver>` 调 `Context.registerReceiver` 动态注册。

---

## 11. 动态化：Manager 也是插件

Shadow 最特别的设计：**Manager / Loader / Runtime 都不随宿主业务代码固定下来**，而是独立 APK 动态加载。Manager 由宿主主进程中的 `DynamicPluginManager` 用 `DexClassLoader` 反射加载；Loader / Runtime 再通过 PPS 被加载到 `:plugin` 进程。这样框架实现本身也能热更。

我们工程实测：

```bash
$ adb shell ls /data/data/com.iwatchme.android/files/ShadowPluginManager/
UnpackedPlugin/
  iwatchme-dynamic-manager/
    02fe45997db8408b62e0e906e57ff2b8/    # 第一个 zip 的 UUID
      demo-1.zip/
        plugin-loader-app-debug.apk      # ← Shadow Loader
        plugin-runtime-app-debug.apk     # ← Shadow Runtime
        plugin-demo-plugin-debug.apk     # ← 业务插件
        config.json
    3d148f3c1d25603f10cf6f491a75b266/    # 第二个 zip
      demo-2.zip/...
```

**反射加载链**（在 `vendor/Shadow/.../DynamicPluginManager.java:67-87`）：

```java
private void updateManagerImpl(Context context) {
    File latestManagerImplApk = mUpdater.getLatest();       // App 私有缓存里的 plugin-manager-1.zip（内容是 manager.apk）
    String md5 = md5File(latestManagerImplApk);
    if (!TextUtils.equals(mCurrentImplMd5, md5)) {
        ManagerImplLoader implLoader = new ManagerImplLoader(context, latestManagerImplApk);
        PluginManagerImpl newImpl = implLoader.load();      // 反射 ManagerFactoryImpl.newInstance()
        newImpl.onCreate(state);
        mManagerImpl = newImpl;
        mCurrentImplMd5 = md5;
    }
}
```

`ManagerImplLoader.load()` 内部反射的固定类名是 **`com.tencent.shadow.dynamic.impl.ManagerFactoryImpl`** —— 这是 Shadow 框架硬编码的契约。我们工程在 `plugin-manager-app/src/main/java/com/tencent/shadow/dynamic/impl/ManagerFactoryImpl.java` 必须放在这个完整包路径下：

```java
package com.tencent.shadow.dynamic.impl;   // ← 固定包名
public final class ManagerFactoryImpl implements ManagerFactory {
    @Override
    public PluginManagerImpl buildManager(Context context) {
        return new IwatchmePluginManager(context);          // ← 业务自己的 Manager
    }
}
```

同理 `CoreLoaderFactoryImpl`（loader.apk 里的反射入口）。

**热更**：把 manager.apk 升一个版本 → 用 `partKey=plugin-manager` 上传后端 → 客户端 `PluginUpdateService` 拉到新的本地文件 → 下次 `DynamicPluginManager.enter()` 时 md5 不匹配 → 创建新 `ManagerImpl`，旧的 `onDestroy`。整个过程不需要重启宿主。

当前后端保存 manager 的方式和业务 zip 是同一个通用上传接口：`POST /api/plugins/{partKey}/upload`。因此 manager 的服务端文件名也会长得像 `plugin-manager-1-1779459820979.zip`，但文件内容就是 `plugin-manager-app-debug.apk`；客户端下载后缓存为 `files/shadow_plugins/plugin-manager-1.zip`，再传给 `FixedFilePluginManagerUpdater`。

---

## 12. 限制与设计取舍

| 取舍 | 选择 | 代价 |
| --- | --- | --- |
| Hook vs 代理 | 代理 | 编译期 Transform 增加构建复杂度；插件无法用反射调一些底层 API（`ActivityThread.currentApplication()` 会拿到宿主而非插件） |
| 同进程 vs 多进程 | `:plugin` 独立进程 | 跨进程 Binder 多一跳延迟（~50ms）；插件无法直接访问宿主单例（要走 Binder 序列化） |
| 启动壳子数量 | 静态预注册 4/2/1（standard/singleTask/singleInstance） | 上限受预声明数量限制；标准用法上限 ≈ 4 个 standard 同时存活 |
| 资源 packageId | 编译期固定 0x7e | 多插件同时加载需要分配不同 ID（0x7e / 0x7d / 0x7c…），≥4 个插件时复杂度上升 |
| Activity 继承 | `Activity`（非 ComponentActivity） | Compose 等依赖 ComponentActivity 的库需要手动 set `ViewTreeLifecycleOwner` 等（见 `plugin-demo/.../ShadowComposeProbe.kt`） |
| Manager 反射入口固定类名 | 写死 `com.tencent.shadow.dynamic.impl.ManagerFactoryImpl` | 业务侧没法用自己包名，必须保留固定路径作为 reflection target |

**Shadow 不擅长的场景**：

- 需要 root 权限或 Native Hook 的插件（Shadow 不提供这些）
- 插件之间互相 import 类、共享单例（要走 specialClassLoader 路由，多 dex 间不能简单 lambda）
- 插件需要 Activity 是 ComponentActivity 才能跑的库（Jetpack Activity Result API、原生 Compose `setContent`）

**Shadow 擅长**：

- 业务模块化拆分，独立开发独立发版，不用等宿主发版
- 框架本身需要热更（Manager / Loader / Runtime 都能换）
- 严格遵守 Google policy（零 hidden API、零反射隐蔽 API，过审无障碍）
- 国内市场（不依赖 Google Play Dynamic Feature）

---

## 13. 参考代码索引

我们工程里能直接读的关键代码：

| 主题 | 文件 |
| --- | --- |
| 壳子 Activity 基类 | `host-shadow/.../container/PluginDefaultProxyActivity.kt` |
| 跨进程 PluginProcessService | `host-shadow/.../IwatchmePluginProcessService.kt` |
| Manager 反射入口 | `plugin-manager-app/.../com/tencent/shadow/dynamic/impl/ManagerFactoryImpl.java` |
| Manager 实现 | `plugin-manager-app/.../IwatchmePluginManager.kt` |
| 安装 + odex + so | `plugin-manager-app/.../FastPluginManager.kt` |
| Loader 反射入口 | `plugin-loader-app/.../com/tencent/shadow/dynamic/loader/impl/CoreLoaderFactoryImpl.java` |
| Loader 实现 | `plugin-loader-app/.../IwatchmePluginLoader.kt` |
| 组件映射（Activity ↔ 壳子 / Provider authority） | `plugin-loader-app/.../IwatchmeComponentManager.kt` |
| 业务插件 Activity | `plugin-demo/.../MemberCenterActivity.kt`（经 Transform 后 extends ShadowActivity） |
| 业务插件 Service | `plugin-demo/.../PluginDemoService.kt` |
| 业务插件 Provider | `plugin-demo/.../PluginMemberProvider.kt` |
| Compose 在插件里跑通的探针 | `plugin-demo/.../ShadowComposeProbe.kt` |

Shadow 自身的源码在 `vendor/Shadow/projects/sdk/`：

| 主题 | 文件 |
| --- | --- |
| Transform 总注册 | `core/transform/.../TransformManager.kt` |
| Activity 字节码替换 | `core/transform/.../specific/ActivityTransform.kt` |
| 壳子 Activity 实现 | `core/activity-container/.../PluginContainerActivity.java` |
| 插件代理基类 | `core/runtime/.../ShadowActivity.java` / `PluginActivity.java` |
| Plugin ClassLoader | `core/loader/.../classloaders/PluginClassLoader.kt` |
| 插件加载主流程 | `core/loader/.../blocs/LoadPluginBloc.kt` |
| 跨进程 DynamicPluginManager | `dynamic/host/.../DynamicPluginManager.java` |
| Service 路由 | `core/loader/.../managers/ComponentManager.kt` |

---

实战层面的接入手册和踩坑记见 `docs/SHADOW_ANDROID.md`；后端契约见 `docs/SHADOW_BACKEND.md`（在 iwatchme-springboot）。
