# Startup Optimization Report

## Summary
这份文档记录当前仓库里冷启动优化实验场的实现结果，包含：
- 启动前后的对比数据
- 当前已完成的代码改造
- 已验证的构建与测试结果
- 后续需要真机执行的测量步骤

## 1. 启动性能优化
### (a) 启动前的情况
- 模型：legacy eager startup，所有 eager 初始化都在单一主线程链路上串行完成。
- Provider 初始化：110ms
- Critical ready：429ms
- Full ready：429ms
- 主线程最长连续阻塞：429ms
- 特征：
  - 没有 critical/full 分层
  - 没有 microbatch
  - 没有 idle/on-demand 拆分
  - 启动初始化集中压在首帧前主线程路径

### (b) 启动后的情况
- 模型：DAG startup runtime，支持 `BLOCKING / NON_BLOCKING / IDLE / ON_DEMAND`。
- Provider 初始化：8ms
- Critical ready：54ms
- Full ready：174ms
- 主线程最长连续阻塞：6ms
- Main-thread batches：2
- 特征：
  - Splash 只等待 blocking 任务
  - `reportFullyDrawn()` 延后到 full report ready
  - idle/on-demand 任务从首帧关键路径拆出
  - 启动过程可输出完整 report 和关键路径信息

## 2. 数据对比
| 指标 | 启动前 | 启动后 | 改善 |
| --- | ---: | ---: | ---: |
| Provider 初始化 | 110ms | 8ms | -102ms |
| Critical ready | 429ms | 54ms | -375ms |
| Full ready | 429ms | 174ms | -255ms |
| 主线程最长连续阻塞 | 429ms | 6ms | -423ms |

## 3. 已完成的代码改造
### 3.1 启动框架
- 重做了 `startupRuntime`，新增 DAG 调度、critical/full 等待、idle/on-demand 分层、microbatch、任务报告。
- 关键入口：`startupRuntime/src/main/java/com/iwatchme/startupruntime/`
- 关键实现：`StartupSession.kt`

### 3.2 App 启动链路
- `JetpackApplication` 启动时接入新的 startup runtime。
- `MainActivity` 接入 SplashScreen，并延后 `reportFullyDrawn()`。
- 增加 `legacy / optimized` 冷启动模式切换，模式值持久化到本地；切换后手动关闭并重新冷启动即可生效。
- 首页改为 startup dashboard，直接展示：
  - current run 指标
  - before vs after 对比
  - task timeline
  - cache-then-network 演示
  - idle/on-demand 状态

### 3.3 Provider 与可观测性
- 增加 `StartupInitializerBridge`，模拟并记录 App Startup provider 初始化。
- 增加关键启动日志：
  - `StartupLab`：模式切换、provider bridge、legacy 任务执行、optimized task body
  - `StartupRuntime`：调度、await、main batch、任务开始/完成、依赖解锁
- 启动过程中会记录：
  - provider bridge 耗时
  - critical/full/idle ready
  - key path
  - 每个任务的 stage / dispatcher / duration

### 3.4 Benchmark / Baseline Profile
- 新增 `benchmark` 模块，用于 Macrobenchmark 冷启动测试。
- 新增 `baselineprofile` 模块，用于 Baseline Profile 生成。
- 已补齐 `profileinstaller`、`androidx.baselineprofile`、`benchmark-macro-junit4` 等构建配置。

## 4. 关键文件
- `app/src/main/java/com/iwatchme/jetpackstarter/startup/JetpackStartupManager.kt`
- `app/src/main/java/com/iwatchme/jetpackstarter/startup/StartupComparisonSimulator.kt`
- `app/src/main/java/com/iwatchme/jetpackstarter/startup/StartupInitializerBridge.kt`
- `app/src/main/java/com/iwatchme/jetpackstarter/home/HomeScreen.kt`
- `startupRuntime/src/main/java/com/iwatchme/startupruntime/StartupSession.kt`
- `benchmark/src/main/java/com/iwatchme/jetpackstarter/benchmark/ColdStartupBenchmark.kt`
- `baselineprofile/src/main/java/com/iwatchme/jetpackstarter/baselineprofile/BaselineProfileGenerator.kt`

## 5. 数据边界说明
- 文档中的 before/after 数字来自 `StartupComparisonSimulator`，属于稳定可复现的模拟对比数据。
- App 运行时 dashboard 展示的是当前真实运行过程的 startup report。
- 真实设备上的 TTID / TTFD 仍需要连接设备后执行 benchmark 才能拿到。

## 6. 如何观察优化前后
1. 打开首页，先看 `Current mode` 和 `Next cold start`。
2. 点击 `Use Legacy Next Launch` 或 `Use Optimized Next Launch`。
3. 手动杀掉 App，再重新冷启动。
4. 回到首页看两处：
   - `Current Run`：本次真实启动的 provider、critical/full/idle、key path
   - `Task Timeline`：每个任务的开始时间、线程、batch、依赖
5. 打开 Logcat，过滤 `StartupLab` 或 `StartupRuntime`：
   - `Legacy` 应该主要表现为同一主线程上的连续 start/finish
   - `Optimized` 应该表现为 IO/CPU 并发启动，主线程任务带 batch 编号

## 7. 已完成验证
已通过：
- `./gradlew :app:testDebugUnitTest :startupRuntime:testDebugUnitTest`
- `./gradlew :benchmark:assembleDebug :baselineprofile:assembleBenchmarkRelease :baselineprofile:assembleNonMinifiedRelease`

说明：
- 直接运行 `:baselineprofile:assemble` 会触发 connected-device 路径。
- 当前为了验证模块本身可编译，使用了更窄的 assemble 任务。
- 为接入 `androidx.profileinstaller:1.4.1`，项目相关模块已升级到 `compileSdk 34`，未修改 `targetSdk`。

## 8. 当前遗留事项
- 真实设备 benchmark 尚未执行。
- Baseline Profile 真实生成与安装验证尚未执行。
- Gradle 运行过程中生成了 `customGradlePlugin/.kotlin/` 目录，是否忽略或清理可后续决定。

## 9. 后续建议
1. 连接设备后执行 Macrobenchmark，补真实 TTID/TTFD 数据。
2. 生成并安装 Baseline Profile，验证 `CompilationMode.None` 与 `CompilationMode.Partial` 的真实差异。
3. 如需正式归档，可继续把 dashboard 的 current-run report 导出成文件。
