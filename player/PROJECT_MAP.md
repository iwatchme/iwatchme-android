# Player 模块 Scope 架构地图

本文档描述 `player/` 模块当前 demo 的 Dagger Subcomponent / `@Scope` 拓扑、各 Scope 内挂载的 Service / Repository / Driver / Bootstrap，以及对应的设计原因。**只描述"现在有什么"**——架构原则与反模式见 [CLAUDE.md](CLAUDE.md)。

> 仓库根：`/Users/iwatchme/android/iwatchme-android`
> 模块根：`player/src/main/java/com/iwatchme/player/`
>
> **维护纪律**：架构变化（增删 Scope / Service / Driver、改通信路径）时必须同步更新本文。本文不写行号——文件路径已足够定位，行号过时风险高。

---

## 1. Scope 拓扑（**这是规范来源，请以此为准**）

```
AppScope（AppComponent）
└── PageScope                       PlayerPageComponent
    ├── BizScope                    UGCBizComponent | OGVBizComponent     ← 二选一
    │   └── EpisodeScope            CurrentEpisodeComponent
    └── MediaScope                  CurrentMediaComponent
```

依据：
- `core/di/Scopes.kt` 定义 4 个 `@Scope` 注解：`PageScope` / `BizScope` / `EpisodeScope` / `MediaScope`。
- `feature/playerpage/di/PlayerPageComponent.kt` 的 `PageSubcomponentsModule` 把 `UGCBizComponent / OGVBizComponent / CurrentMediaComponent` **同时**注册为 PageScope 的子 component。 → **BizScope 与 MediaScope 是 PageScope 的兄弟子节点。**
- `feature/playerpage/di/BizSubcomponentsModule.kt`：`@Module(subcomponents = [CurrentEpisodeComponent::class])`，被 UGC/OGV 两个 BizComponent 都引用。 → **EpisodeScope 是 BizScope 的子节点，**不是 MediaScope 的兄弟。

兄弟关系含义：MediaScope 与 BizScope 互不感知，彼此通信靠 PageScope 的 `MediaScopeDriver` / `PlayerUiStateRepository` 这些上层枢纽。

每个 Subcomponent 用 `@BindsInstance` 接收一个该层的 `CoroutineScope`（qualifier 见 `core/di/Qualifiers.kt`）：`@PageCoroutineScope` / `@BizCoroutineScope` / `@MediaCoroutineScope` / `@EpisodeCoroutineScope`。

---

## 2. PageScope —— 页面级容器

**Component**：`PlayerPageComponent`（`feature/playerpage/di/PlayerPageComponent.kt`）
**Modules**：`PageScopeModule`、`PageSubcomponentsModule`
**生命周期**：`PlayerPageFragment.onCreate` 创建 `MainScope()` 并通过 Factory 建立 component；`onDestroy` 调 `pageScope.cancel()` + `exoPlayerHolder.release()`。

### 2.1 Provider（`PageScopeModule`）

| 提供物 | 文件 | 原因 |
|---|---|---|
| `ExoPlayerHolder` | `PageScopeModule.kt` | ExoPlayer 实例必须页面级单例；切合集 / 切集都不能销毁，否则播放中断。 |
| `PlayerViewBinder` | `PageScopeModule.kt` | 与 Fragment 视图绑定，跟随 Fragment。 |

### 2.2 Service / Driver / Repository（@PageScope，`@Inject` 注入）

| 类型 | 名称 | 文件 | 在此 scope 的原因 |
|---|---|---|---|
| Driver | `BizScopeDriver` | `page/BizScopeDriver.kt` | 状态机：`Idle / Loading / InBusiness / Failure`，按 sealed `DetailData`（UGC/OGV）选 Subcomponent.Factory 创建 BizScope。需要持有跨 BizScope 切换的状态，必须在 PageScope。 |
| Driver | `MediaScopeDriver` | `page/MediaScopeDriver.kt` | 调度器：维护 `currentItemFlow`，`switchTo(item)` 时通过 `collectLatest` 创建新 MediaScope、销毁旧的。EpisodeScope 也从这条流反向驱动，所以 driver 必须在两者共同的祖先 PageScope。 |
| Bootstrap | `PageBootstrap` | `page/PageBootstrap.kt` | 注入并 eager 实例化 `BizScopeDriver / MediaScopeDriver / DetailTitleService / PlayerLoadingService / PlayerErrorService`（这些 service 在 init 块订阅 flow，不被注入就不会建），然后投递首屏 `bvid`。 |
| Service | `DetailTitleService` | `page/DetailTitleService.kt` | 顶部"合集标题"UI 的 ViewModel。订阅 `BizScopeDriver.stateFlow` 渲染文案，点击触发 `switchToNewVideo`。切合集时 BizScope 要重建，但顶部标题是页面级 UI、必须独立于 BizScope 存活。 |
| Service | `PlayerLoadingService` | `page/PlayerLoadingService.kt` | loading 遮罩 ViewModel，只看 `playbackStateFlow == LOADING`。遮罩属于页面级 overlay。 |
| Service | `PlayerErrorService` | `page/PlayerErrorService.kt` | error 遮罩 ViewModel，combine `playbackStateFlow + errorMessageFlow`。同上。 |
| Repository | `PageDetailRepository` | `page/PageDetailRepository.kt` | **无状态 IO**：`suspend fun loadDetail(bvid)` 调 mock，结果由 `BizScopeDriver` 持有。"现在加载到了什么"是状态、不是 repo 职责。 |
| Repository | `PlayerUiStateRepository` | `page/PlayerUiStateRepository.kt` | **跨 scope 状态枢纽**：`playbackStateFlow / errorMessageFlow` 是 StateFlow，`completionEvents` 是 `SharedFlow(replay=0, buffer=1)`。MediaScope 的 service 写、PageScope 的 service 与 EpisodeScope 的 `EpisodeCompletedService` 读。放在 PageScope 是因为它必须比 Media/Episode 都活得久，否则切集会丢事件 / 把陈旧值当新事件重放。 |
| Repository | `CurrentBizComponentRepository` | `page/CurrentBizComponentRepository.kt` | 持有当前激活的 `PlayerBizFacade`。Fragment 通过它感知 BizComponent 的建立与销毁，不依赖具体 UGC/OGV 类型。 |
| Repository | `ScreenStateRepository` | `page/ScreenStateRepository.kt` | 屏幕状态（`PORTRAIT_HALF` / `LANDSCAPE_FULL`）的纯持有者，暴露 `switchToFullscreen / switchToHalfscreen / toggle` 入口。放 PageScope 是因为切合集 / 切集都不该重置横屏状态。 |
| Service | `ScreenStateService` | `page/ScreenStateService.kt` | 屏幕状态的输入聚合 + imperative side-effect：重力感应 → 写 repo；订阅 repo → 调 `activity.requestedOrientation`、注册 `OnBackPressedCallback`、暴露 `fullscreenButtonViewModel`。Fragment **不参与**任何 screen state 订阅，全部由本 service 完成（CLAUDE.md §9.8 / §9.9）。注入 `ComponentActivity`（PageScope `@BindsInstance`）。 |
| Service | `PlayerGestureService` | `page/PlayerGestureService.kt` | 播放器手势输入聚合 + **listener 注册中心**。内部持 `PlayerGestureDetector`（识别双击/单击/长按/垂直滑动/水平滑动，双击/单击/长按走 `PriorityGestureProcessor` 优先级链）+ `BrightnessVolumeController`。**默认 listener 挂 `PRIORITY_LOWEST`**（双击 toggle play/pause / 横滑 seek / 竖滑亮度音量），业务通过 `addOnLongPressListener` 等接口在 `PRIORITY_NORMAL` 注入即可短路覆盖。暴露 `gestureSurfaceUIComponent` + `gestureOverlayViewModel` + `emitOverlayState(state)`（业务推送 indicator）。注入 `ComponentActivity` + `Context` + `ExoPlayerHolder` + `AudioManager`（PageScope @Provides）。 |
| Service | `TripleSpeedService` | `page/TripleSpeedService.kt` | 长按 3x 倍速业务 service。装配模式："init 里 launch + addListener + try/awaitCancellation/finally" 保证 PageScope 死亡时反注册。注入 `PlayerGestureService` 调 `addOnLongPressListener`：onLongPress 时若已在播放则 `player.setPlaybackSpeed(3f)` + 振动 30ms + emit `OverlayState.TripleSpeed(3f)`；onLongPressEnd 恢复 1f + emit Hidden。 |
| Service | `PlaybackProgressService` | `page/PlaybackProgressService.kt` | 底部进度条数据源。**轮询 + Listener 双源**：500ms 轮询 ExoPlayer `currentPosition / bufferedPosition / duration` 写自身 StateFlow；同时挂 `Player.Listener` 监听 `onPositionDiscontinuity` / `onPlaybackStateChanged` 立即推一次。**拖动保护**：`isUserSeeking` 屏蔽轮询写入避免 SeekBar 被抢回；UI 调 `viewModel.onSeekStop(positionMs)` 提交 `player.seekTo` 后释放并立即推送最新位置。放 PageScope 因为 ExoPlayer 也是 PageScope 单例、切集时 player 自动 reset 进度，service 不需要感知 MediaScope 切换。 |

---

## 3. BizScope —— 业务（合集）作用域

**Component**：`UGCBizComponent` 或 `OGVBizComponent`（二选一），都实现 `PlayerBizFacade`
- `feature/playerpage/di/UGCBizComponent.kt`
- `feature/playerpage/di/OGVBizComponent.kt`
- `feature/playerpage/di/PlayerBizFacade.kt`

**Modules**：
- 共享：`BizScopeModule`、`BizSubcomponentsModule`、`VideoListModuleParser`
- 私有：UGC 走 `UGCUploaderBannerParser` + `UGCBizModule`；OGV 走 `OGVSeasonBannerParser` + `OGVBizModule`

**生命周期**：`BizScopeDriver.driveBusinessScope(detail)` 在 `coroutineScope { ... awaitCancellation() }` 内创建；`stateFlow` 用 `collectLatest`，所以切合集 / 失败时旧 BizScope 自动取消。

### 3.1 业务差异如何注入

`UGCBizModule` 和 `OGVBizModule` 各做两件事：
1. `@Provides DetailData`：把 `@BindsInstance` 进来的 `UGCDetail` / `OGVDetail` 暴露成 `DetailData`，通用 service（`VideoListRepository / BizRecyclerViewService`）就能注入抽象类型。
2. `@Binds BizInfoService`：UGC 域 → `UGCInfoService`；OGV 域 → `OGVSeasonService`。Fragment 在 `PlayerBizFacade.bizInfoService()` 上只看到接口。

### 3.2 Service / Driver / Repository（@BizScope）

| 类型 | 名称 | 文件 | 在此 scope 的原因 |
|---|---|---|---|
| Bootstrap | `BizBootstrap` | `biz/BizBootstrap.kt` | eager 注入 `InitialSelectionService / MediaSelectionDispatcher / EpisodeScopeDriver / EpisodeTitleService / BizInfoService`。`PlayerBizFacade.bootstrap()` 由 driver 调 `start()`。 |
| Driver | `EpisodeScopeDriver` | `biz/EpisodeScopeDriver.kt` | **反向驱动**：监听 PageScope 的 `MediaScopeDriver.currentItemFlow`，`distinctUntilChangedBy { it.id }` 之后用 `collectLatest` 建/销 EpisodeScope。放在 BizScope 的原因：EpisodeScope 是 BizScope 的子 component，工厂只能在 BizScope 注入；而且切合集时整片 EpisodeScope 应当跟着 BizScope 一起销毁。 |
| Service | `MediaSelectionDispatcher` | `biz/MediaSelectionDispatcher.kt` | 把 `SelectionRepository` 的"选了哪一集"`combine` 上 `VideoListRepository`，得到 `VideoItem` 再投给 PageScope 的 `MediaScopeDriver.switchTo`。"选集"是业务行为，所以发生地在 BizScope；目标资源在 PageScope，靠这层转接桥。 |
| Service | `InitialSelectionService` | `biz/InitialSelectionService.kt` | BizScope 拉起时挑首项调 `selectionRepository.select(...)`，是切合集后的初始化。BizScope 一重建就要重新选首集，所以是 BizScope 级。 |
| Service | `EpisodeTitleService` | `biz/EpisodeTitleService.kt` | "正在播放：xxx" ViewModel。combine `selectionRepository + videoListRepository` 自己解 id→item，体现"repo 不互相依赖、组合下沉到 service"。 |
| Service | `BizInfoService`（接口） / `UGCInfoService` / `OGVSeasonService` | `biz/BizInfoService.kt`、`biz/UGCInfoService.kt`、`biz/OGVSeasonService.kt` | 业务专属信息条。两份实现各自只能在能拿到 `UGCDetail` / `OGVDetail` 具体类型 binding 的子 component 里被 `@Inject` 构造——编译期就保证不串域。 |
| Service | `UGCUploaderBannerService` | `biz/UGCUploaderBannerService.kt` | UGC 私有：注入 `UGCDetail`，作为列表里的 RunningUIComponent。**仅在 UGCBizComponent** 里有 binding（通过 `UGCUploaderBannerParser`）。 |
| Service | `OGVSeasonBannerService` | `biz/OGVSeasonBannerService.kt` | OGV 私有：同上对应到 OGVBizComponent。 |
| Service | `BizRecyclerViewService` | `biz/BizRecyclerViewService.kt` | 把 `detail.modules` 通过 `BizModuleListMapper`（@IntoMap 多 binding）映射成一组 RunningUIComponent，并用 BizScope 的协程驱动它们的状态。需要 `DetailData`（来自 BizScope 的 BindsInstance）。 |
| Service | `VideoListItemService` | `biz/VideoListItemService.kt` | 列表项工厂：每个 item 一个 RunningUIComponent，闭包持有 per-item state，订阅 `selectionRepository` 渲染选中态。`@Inject` 在 BizScope，每集 item 用 `create(item)`。 |
| Repository | `SelectionRepository` | `biz/SelectionRepository.kt` | 只持有 `selectedItemIdFlow`，纯 id。**刻意不依赖** `VideoListRepository`，组合下沉到 `EpisodeTitleService / MediaSelectionDispatcher`。 |
| Repository | `VideoListRepository` | `biz/VideoListRepository.kt` | 持有 `detail.items`，由 BizScope 的 `DetailData` BindsInstance 喂。无 IO，纯被动数据。 |
| Repository | `CurrentEpisodeComponentRepository` | `biz/CurrentEpisodeComponentRepository.kt` | `EpisodeScopeDriver` 创建 / 销毁 EpisodeScope 时写入，让 BizScope 内其他 service 感知 episode 切换。 |
| Service | `VideoListPanelService` | `biz/VideoListPanelService.kt` | 视频列表面板的可见性 ViewModel——订阅 PageScope 的 `ScreenStateRepository`，全屏时把 `binding.videoList` 隐藏。Fragment 用 `PanelVisibilityUIComponent` 把这个 viewModel 接到 RecyclerView 上。 |

### 3.3 模块映射器（@IntoMap）

- `biz/di/BizModuleMapper.kt`：定义 `BizModuleMapper` 接口（按 `BizModuleType` 多 binding）。
- `biz/di/VideoListModuleParser.kt`：共享 module，注册 `VIDEO_LIST` 类型 → 用 `VideoListItemService` 展开 items。被 UGC/OGV 都引用。
- `biz/di/UGCUploaderBannerParser.kt`：UGC 私有，注册 `UGC_UPLOADER_BANNER`，调 `UGCUploaderBannerService.create()`。
- `biz/di/OGVSeasonBannerParser.kt`：OGV 私有，注册 `OGV_SEASON_BANNER`，调 `OGVSeasonBannerService.create()`。

`BizScopeModule.provideBizModuleListMapper` 从 `Map<BizModuleType, BizModuleMapper>` 拼装最终列表。Multibinding 在 BizScope 收口，不同业务自动得到不同的可见 binding。

---

## 4. MediaScope —— 媒体（单视频）作用域，**与 BizScope 同级**

**Component**：`CurrentMediaComponent`（`feature/playerpage/di/CurrentMediaComponent.kt`）
**Module**：`MediaScopeModule`（当前为空 `object`）
**BindsInstance**：`@MediaCoroutineScope CoroutineScope` + `VideoItem`
**生命周期**：`MediaScopeDriver.init` 内 `_currentItemFlow.collectLatest { item -> coroutineScope { factory.create(this, item); bootstrap.start(); awaitCancellation() } }`。`switchTo` 改 flow → 旧 MediaScope 自动取消，新 MediaScope 起来。

### Service / Repository（@MediaScope）

| 名称 | 文件 | 在此 scope 的原因 |
|---|---|---|
| `MediaBootstrap` | `media/MediaBootstrap.kt` | eager 实例化 `MediaPrepareService` + `PlaybackStatusService`（两者都在 init 中订阅，必须被注入）。 |
| `MediaPrepareService` | `media/MediaPrepareService.kt` | 切到本 item 时先把 `playbackStateFlow` 写成 LOADING（PlayerUiStateRepository 是 PageScope 的，跨 scope 注入），再调 `currentMediaRepository.loadPlaybackInfo()`，`collectLatest` 拿到 `PlaybackInfo` 后给 ExoPlayer `setMediaItem + prepare + playWhenReady=true`。 |
| `PlaybackStatusService` | `media/PlaybackStatusService.kt` | 给 ExoPlayer 加 `Player.Listener`，把 `STATE_BUFFERING/READY/ENDED` 映射成内部 `PlaybackState` 写到 `playerUiStateRepository`；`STATE_ENDED` 时 `notifyCompletion()` 触一次性事件。**只反应 onPlaybackStateChanged 回调，不主动读 `player.playbackState`**——否则新 listener 接上时会把上一集陈旧的 ENDED 当新事件发出。这是 MediaScope 必须 per-集销毁的核心理由。 |
| `MediaItemFactoryService` | `media/MediaItemFactoryService.kt` | `PlaybackInfo` → `MediaItem` 的纯函数工厂，per-集状态（`item`）注入更顺。 |
| `CurrentMediaRepository` | `media/CurrentMediaRepository.kt` | 持有本集 `playbackInfoFlow`，提供 `loadPlaybackInfo()` 自己写 flow。"被动数据源 + 由 service 触发加载"。 |

**为何 MediaScope 不是 BizScope 的子节点而是兄弟**：媒体准备只关心一个 `VideoItem`，不需要合集（`DetailData`、`SelectionRepository`、`VideoListRepository`）等业务上下文；放在 PageScope 之下、与 BizScope 平级，可以让它直接拿到 PageScope 的 `ExoPlayerHolder` 和 `PlayerUiStateRepository`，又跟业务彻底解耦。BizScope ↔ MediaScope 不直接通信，全部走 PageScope 的 `MediaScopeDriver` 和 `PlayerUiStateRepository`。

---

## 5. EpisodeScope —— 集级业务作用域，**BizScope 的子节点**

**Component**：`CurrentEpisodeComponent`（`feature/playerpage/di/CurrentEpisodeComponent.kt`）
**Module**：`EpisodeScopeModule`（当前为空 `object`）
**BindsInstance**：`@EpisodeCoroutineScope CoroutineScope` + `VideoItem`
**生命周期**：`EpisodeScopeDriver`（在 BizScope）监听 PageScope 的 `MediaScopeDriver.currentItemFlow.filterNotNull().distinctUntilChangedBy { it.id }`，用 `collectLatest` 建/销。

### Service / Repository（@EpisodeScope）

| 名称 | 文件 | 在此 scope 的原因 |
|---|---|---|
| `EpisodeBootstrap` | `episode/EpisodeBootstrap.kt` | eager 注入 `EpisodeMetaRepository / EpisodeCompletedService`，`start()` 触发 `loadMeta()`。 |
| `EpisodeMetaRepository` | `episode/EpisodeMetaRepository.kt` | 持有本集 `EpisodeMeta(cid, chapters)`，提供 `loadMeta()` 自己写 flow。集元数据天然 per-集，跨集就重建。 |
| `EpisodeCompletedService` | `episode/EpisodeCompletedService.kt` | **跨 3 层 scope 订阅**：`item`（EpisodeScope）+ `videoListRepository / selectionRepository`（BizScope）+ `playerUiStateRepository.completionEvents`（PageScope）。事件流用 `SharedFlow(replay=0)` —— 否则 `StateFlow` 会把上一集的 COMPLETED 重放给新 EpisodeScope 的订阅者，触发"切下一集 → 新 service 又看见 → 再切"的死循环。 |

**为何 EpisodeScope 与 MediaScope 是不同层级的兄弟概念**（关键澄清）：两者**生命周期都是 per-集**，但层级不同——MediaScope 是 PageScope 直接子节点，EpisodeScope 是 BizScope 子节点。原因：
- MediaScope 关心的是"播放器在播这个 item"，与业务上下文（合集）无关。
- EpisodeScope 关心的是"业务上正在看哪一集"，强依赖 BizScope 的 `SelectionRepository / VideoListRepository`，必须在 BizScope 树内才能拿到 binding。
- 切合集时 BizScope 销毁 → EpisodeScope 跟着销毁；但 MediaScope 不受影响，会被 `MediaScopeDriver` 单独切（也确实需要切，因为换合集后 item 变了）。
- 切集时（同合集内）：BizScope 不动；MediaScope 切；EpisodeScope 也切（被 `currentItemFlow` 反向驱动）。

---

## 6. 关键通信路径（以代码为准）

**用户点选集 → 拉起 MediaScope/EpisodeScope**
1. `VideoListItemUIComponent` 的 click → `SelectionRepository.select(itemId)`（`VideoListItemService` / `SelectionRepository`）
2. BizScope 的 `MediaSelectionDispatcher` combine selection + list → `MediaScopeDriver.switchTo(item)`
3. PageScope 的 `MediaScopeDriver._currentItemFlow.value = item` → `collectLatest` 取消旧 MediaScope，建新 MediaScope
4. BizScope 的 `EpisodeScopeDriver` 也订阅 `currentItemFlow` → `distinctUntilChangedBy { it.id }` 后建新 EpisodeScope

**播放结束 → 自动下集**
1. ExoPlayer `STATE_ENDED` → `PlaybackStatusService` 写 `playerUiStateRepository.updatePlaybackState(COMPLETED)` + `notifyCompletion()`
2. EpisodeScope 的 `EpisodeCompletedService` 收到 `completionEvents` → 算下一集 id → `selectionRepository.select(nextId)`
3. 回到上面"用户点选集"路径

**点击合集标题 → 切合集**
1. `DetailTitleService.handleClick()` → `BizScopeDriver.switchToNewVideo(StartParams(bvid))`
2. `BizScopeDriver._stateFlow = Loading → InBusiness/Failure`，`collectLatest` 取消旧 BizScope（含其下 EpisodeScope），按 sealed `DetailData` 类型创建新的 UGC/OGV BizComponent
3. `CurrentBizComponentRepository` 更新 → Fragment 重新绑定 UI

**全屏切换（按钮 / 重力感应 / 返回键）**
1. **按钮点击**：`FullscreenButtonUIComponent` onClick → `ScreenStateService.fullscreenButtonViewModel.onClick()` → `screenStateRepository.toggle()`
2. **重力感应**：`OrientationEventListener` 触发 → `ScreenStateService.orientationFlow().mapNotNull { 阈值过滤 }.distinctUntilChanged()` → `repo.switchToFullscreen / switchToHalfscreen`
3. **返回键（仅全屏时拦截）**：`OnBackPressedCallback` → `repo.switchToHalfscreen()`
4. 三种入口最终都改 `screenStateRepository.screenStateFlow`
5. **输出端**（service 自己订）：`activity.requestedOrientation = state.orientation`，`backPressCallback.isEnabled = state.isFullscreen`，`fullscreenButtonViewModel.state` 更新
6. **业务消费者**（各 service 自己订 screenStateFlow）：
   - `DetailTitleService` / `EpisodeTitleService` / `UGCInfoService` / `OGVSeasonService` 在自己 `ViewModel.State.visible` 上反映可见性
   - `VideoListPanelService` 暴露 `visibleFlow`，`PanelVisibilityUIComponent` 绑到 `binding.videoList` 控制可见性
7. Fragment 全程**不订阅** `screenStateFlow`，只 bind UIComponents

---

## 7. 文件速查

```
core/
├── di/Scopes.kt                                @Scope 4 个
├── di/Qualifiers.kt                            @Qualifier 4 个 CoroutineScope
└── player/
    ├── ExoPlayerHolder(Impl).kt
    └── PlayerViewBinder(Impl).kt

model/
├── ScreenState.kt                              PORTRAIT_HALF / LANDSCAPE_FULL 枚举

feature/playerpage/
├── di/
│   ├── AppComponent.kt / AppModule.kt
│   ├── PlayerPageComponent.kt                  @PageScope + PageSubcomponentsModule
│   ├── PageScopeModule.kt
│   ├── UGCBizComponent.kt / OGVBizComponent.kt @BizScope，都实现 PlayerBizFacade
│   ├── PlayerBizFacade.kt                      跨业务统一接口
│   ├── BizSubcomponentsModule.kt               注册 CurrentEpisodeComponent
│   ├── BizScopeModule.kt                       通用：BizModuleListMapper
│   ├── UGCBizModule.kt / OGVBizModule.kt       @Binds BizInfoService + DetailData
│   ├── CurrentMediaComponent.kt                @MediaScope（兄弟于 BizScope）
│   ├── MediaScopeModule.kt                     当前空对象
│   ├── CurrentEpisodeComponent.kt              @EpisodeScope（BizScope 子节点）
│   └── EpisodeScopeModule.kt                   当前空对象
├── page/                                       PageScope 资产
│   ├── PageBootstrap.kt
│   ├── BizScopeDriver.kt / MediaScopeDriver.kt
│   ├── DetailTitleService.kt
│   ├── PlayerLoadingService.kt / PlayerErrorService.kt
│   ├── PageDetailRepository.kt
│   ├── PlayerUiStateRepository.kt              completionEvents 是 SharedFlow
│   ├── CurrentBizComponentRepository.kt
│   ├── ScreenStateRepository.kt                屏幕状态 + switchToFullscreen / switchToHalfscreen
│   ├── ScreenStateService.kt                   sensor + activity orientation + back press 全收口
│   ├── PlayerGestureService.kt                 手势输入聚合 + listener 注册中心（默认实现挂 LOWEST）
│   ├── PlayerGestureDetector.kt                手势识别核心（无 DI，方向锁定 + 长按识别）
│   ├── PriorityGestureProcessor.kt             listener 优先级链（HIGH→NORMAL→LOW→LOWEST 短路）
│   ├── BrightnessVolumeController.kt           音量/亮度副作用类（无 DI，含归一化进度计算）
│   ├── TripleSpeedService.kt                   长按 3x 倍速业务 service（注入 PlayerGestureService 注册 LongPress listener）
│   └── PlaybackProgressService.kt              底部进度条数据源（轮询 ExoPlayer position + Listener 双源，含拖动保护）
├── biz/                                        BizScope 资产
│   ├── BizBootstrap.kt
│   ├── EpisodeScopeDriver.kt
│   ├── MediaSelectionDispatcher.kt
│   ├── InitialSelectionService.kt
│   ├── EpisodeTitleService.kt
│   ├── BizInfoService.kt (interface)
│   │   UGCInfoService.kt / OGVSeasonService.kt
│   ├── UGCUploaderBannerService.kt / OGVSeasonBannerService.kt
│   ├── BizRecyclerViewService.kt / VideoListItemService.kt
│   ├── SelectionRepository.kt / VideoListRepository.kt
│   ├── CurrentEpisodeComponentRepository.kt
│   ├── VideoListPanelService.kt                列表面板可见性（订 screenStateFlow）
│   └── di/
│       ├── BizModuleMapper.kt
│       ├── VideoListModuleParser.kt            共享：UGC + OGV
│       ├── UGCUploaderBannerParser.kt          UGC 私有
│       └── OGVSeasonBannerParser.kt            OGV 私有
├── media/                                      MediaScope 资产
│   ├── MediaBootstrap.kt
│   ├── MediaPrepareService.kt
│   ├── PlaybackStatusService.kt
│   ├── MediaItemFactoryService.kt
│   └── CurrentMediaRepository.kt
├── episode/                                    EpisodeScope 资产
│   ├── EpisodeBootstrap.kt
│   ├── EpisodeMetaRepository.kt
│   └── EpisodeCompletedService.kt
└── ui/
    ├── PlayerPageFragment.kt                   PageComponent.create() 入口
    └── PlayerPageActivity.kt
```

---

> 写代码 / 评审时的规则、反模式、检查项见 [CLAUDE.md](CLAUDE.md)。本文不重复。
