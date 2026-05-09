# Player 模块架构规则

> 本文件**只针对 `player/` 模块下 `feature/playerpage` 功能**——一个演示分层架构的播放器 demo。
> 其它模块（cocos-shell、render-engine 等）有各自的体系，不适用本文件的规则。
>
> 写代码前先扫一遍这里。**架构纯度优先于性价比**——本 demo 存在的价值就是演示一套
> "Scope 分层 + Repo/Service/UIComponent/Fragment 四层职责 + Driver 状态机 + DI 多业务分发"。
> 任何"图省事的 shortcut"都是反向收益。
>
> **配套文档**：[PROJECT_MAP.md](PROJECT_MAP.md) 描述当前各 Scope 下的 Service / Repository / Driver
> 清单和通信路径——开始改代码前先读它定位现状。**修改架构时必须同步更新 PROJECT_MAP.md**
> （增删 Scope / Service / Driver、改通信路径都要更新；只改实现细节不需要）。本 CLAUDE.md 只讲规则，
> PROJECT_MAP.md 只讲现状，不重复。

---

## 1. 四个 Scope 各自的职责

```
PageScope            ← 整个详情页生命周期，跨 biz/episode/media 切换都不变
   │
   ├── BizScope            ← 一个合集 / 一种业务（UGC vs OGV）的生命周期；切详情或切业务时整层重建
   │     │
   │     └── EpisodeScope  ← 一集的生命周期；切集时整层重建
   │
   └── MediaScope          ← 一次媒体播放的生命周期；切清晰度/切集时整层重建
                              **MediaScope 与 BizScope 并列在 PageScope 下，不嵌套在 EpisodeScope 里**
```

**判断一个状态/服务该在哪个 scope 的标准："切到下一个 X 时，这块状态需不需要重置？"**

| 状态需要在哪个边界重置 | 放哪个 scope |
|---|---|
| 整页都不重置（用户切横屏后切集仍保持横屏） | PageScope |
| 切合集/切业务时重置（UGC 选集状态、OGV 季度元数据） | BizScope |
| 切集时重置（结束页判定、付费门、互动数据） | EpisodeScope |
| 切播放时重置（playbackInfo、当前 ExoPlayer 状态监听） | MediaScope |

---

## 2. 四层职责（Repository / Service / UIComponent / Fragment）

**职责必须严格分离。** Fragment 写一行 `repo.flow.collect` 就是越权。

### 2.1 Repository

- **职责**：纯数据/IO。要么是接口封装（无状态，只暴露 `suspend fun loadXxx()`），要么是单一状态持有（一个 MutableStateFlow + 写入方法）。
- **绝不**：在 init 里 `combine` / `collect` 其他 repo 的 flow。订阅别 repo 的 flow 是 Service 的职责。
- **可以**：构造期注入别的 repo 当"写时取值"——比如某 service 在写操作里现读一次 `xxxRepository.someValue()` 拿一个调用参数。但**不允许 init 里订阅**。
- **状态机不归 Repo**：状态机（`Idle/Loading/InBusiness/Failure`）放在 Driver 里。Repo 退化成无状态 IO（参考 `PageDetailRepository.loadDetail(bvid): Result<DetailData>`）。

### 2.2 Service

- **职责**：业务逻辑层。包含：
  - 订阅 1~N 个 Repo 的 flow，组合/派生
  - 暴露 `viewModel: XxxUIComponent.ViewModel` 给 UI 层
  - 处理 imperative 副作用（`exoPlayer.prepare()`、`activity.requestedOrientation = ...`）
  - 翻译事件（用户点击 → 调 Repo 写入 / 调 Driver 切换）
- **必须**：所有 `flow.collect` 都发生在 Service 层。
- **生命周期**：用对应 scope 的 `@XxxCoroutineScope` 注入 CoroutineScope，确保 service 死亡时所有协程被取消。

### 2.3 UIComponent

- **职责**：把 ViewModel 状态绑到具体 View。
- 接口：`createViewEntry(context, parent)` 建 view，`bindToView(viewEntry)` 绑数据。
- **必须**：`bindToView` 里只 collect Service 暴露的 `viewModel.state`，不直接 collect Repo。
- 列表项的 UIComponent 还要实现 `viewReusingKey` / `contentEqualityKey` / `identityEqualityKey` 给 DiffUtil 用。

### 2.4 Fragment

- **职责**：装配。仅做：
  1. inflate layout
  2. 构造 UIComponent，调 `bindToView` 把 Service.viewModel 接到 binding 里的 view
  3. lifecycle 管理（onDestroyView 时 cancel bindJob）
  4. 调 `pageBootstrap.start()`
- **绝不**：
  - `repo.xxxFlow.collect { ... }` ❌
  - `if (state.isFullscreen) binding.x.isVisible = ...` ❌
  - 任何业务判断 ❌
- 横屏隐藏列表这种事都要走"业务 service 订 screenStateFlow → 改自己 ViewModel.visible → UIComponent 跟着更新"。

---

## 3. Driver 模式（PageScope 的状态机）

**Driver = 状态机 + 业务编排器**。

形状：
```kotlin
@PageScope
class XxxScopeDriver @Inject constructor(
    @PageCoroutineScope private val pageScope: CoroutineScope,
    private val xxxRepository: XxxRepository,         // 无状态 IO
    private val factoryA: ABizComponent.Factory,
    private val factoryB: BBizComponent.Factory,      // 多业务时多个 factory
    ...
) {
    private val _stateFlow = MutableStateFlow<State>(State.Idle)
    val stateFlow: StateFlow<State> = _stateFlow.asStateFlow()

    init {
        pageScope.launch {
            _stateFlow.collectLatest { state -> when (state) {
                is State.InBusiness -> driveScope(state.detail)
                ...
            } }
        }
    }

    fun switchToNewVideo(startParams: StartParams) {       // ← 业务语义入口
        _stateFlow.value = State.Loading(startParams)
        runningJob = pageScope.launch {
            val result = xxxRepository.loadDetail(startParams.bvid)
            _stateFlow.value = result.fold(
                onSuccess = { State.InBusiness(it) },
                onFailure = { State.Failure(startParams, it) },
            )
        }
    }

    sealed interface State { ... }                         // ← Idle/Loading/InBusiness/Failure 显式
    class StartParams(val bvid: String)                    // ← 业务参数集中在这里
}
```

- **状态机的 State 是 sealed interface**，编译器保证 when 穷尽。
- **业务入口是 `switchToXxx(StartParams)`**，参数集中传入而不是散到各方法。
- **`collectLatest { state -> driveScope(...) }`**：`coroutineScope { ... awaitCancellation() }` 模式，靠 collectLatest 自动取消老 scope。

---

## 4. Bootstrap 模式（强制 eager 实例化）

`@XxxScope class XxxBootstrap @Inject constructor(...)` 把所有"必须在 scope 创建时就跑 init 的"
service / driver 列在构造函数里。Dagger 拿到 Bootstrap 时才会构造它们。

```kotlin
@PageScope
class PageBootstrap @Inject constructor(
    private val bizScopeDriver: BizScopeDriver,
    @Suppress("unused") private val mediaScopeDriver: MediaScopeDriver,
    private val detailTitleService: DetailTitleService,
    ...
) {
    fun start() { bizScopeDriver.switchToNewVideo(...) }
}
```

**新增"init 里订阅 flow"的 service 时，必须挂进对应 Bootstrap 构造函数**——否则 Dagger 不实例化它，订阅永远不发生。`@Suppress("unused")` 标注那些只为 DI 触发存在的字段。

---

## 5. State 还是 Event ？—— 数据建模铁律

| 数据载体 | 语义 | 适合 |
|---|---|---|
| `MutableStateFlow<T>` | 当前状态，新订阅者立即收到 current value（重放） | loading 显隐、错误消息、当前 item、当前屏幕方向 |
| `MutableSharedFlow<T>(replay=0, extraBufferCapacity=1)` | 一次性事件，新订阅者**不会**收到历史事件 | "媒体刚播完"、"用户点击重试"、"错误 toast 触发" |

**绝对不要用 StateFlow 表达离散事件**。本项目踩过两次坑：
1. `playbackStateFlow` 用 `COMPLETED` 状态触发"自动播下一集"——新 EpisodeScope 起来时立刻拿到陈旧的 `COMPLETED`，死循环切下一集
2. `ExoPlayer.playbackState` 在新 listener 接上时被 `trySend(player.playbackState)` 主动读一次——同样把上一集的 `STATE_ENDED` 当成新事件

修法都是"改成 SharedFlow 事件流"或"去掉主动读取"。

**判断标准**：写代码前问自己——
- 新订阅者订上去**应该**看到"当前状态"吗？是 → StateFlow
- 还是只关心"未来发生的事"？是 → SharedFlow / Channel

---

## 6. 订阅时陷阱（Subscribe-Time Pitfalls）

### 6.1 StateFlow 重放陷阱

`stateFlow.collectLatest { ... }` 订阅瞬间立即拿到 `value`。如果 value 是上一次操作留下的"陈旧值"，订阅方就被错误触发。

修法：
- 优先：把数据建模成 SharedFlow event（见 §5）
- 兜底：`.drop(1)` 跳过初始重放
- 兜底：scan 出"上一个值 → 当前值"做 transition 过滤

### 6.2 ExoPlayer / 系统 API 的"热属性"

ExoPlayer.playbackState、Activity.requestedOrientation 等是**热属性**——读取永远拿到当前值，跟"刚刚发生了变化"无法区分。

**禁止在 listener attach 时手动 `trySend(currentValue)`** 把 hot property 当成"初始事件"发射。只对真实回调反应。

### 6.3 跨 scope flow 的孤儿订阅

一个 service 在 EpisodeScope 里订阅 PageScope 的 flow。EpisodeScope 死掉时它的 CoroutineScope 被取消，订阅也跟着死——这是**正确**行为，不是 bug。但如果 service 把订阅 launch 在了错误的 scope 上（比如 `GlobalScope`），就会孤儿。

**永远用 `@XxxCoroutineScope` 注入 CoroutineScope**，不要用 `GlobalScope` / `MainScope()`。

---

## 7. DI 分发模式

### 7.1 Sealed 类型业务分发

`DetailData` 是 sealed interface，子类 `UGCDetail` / `OGVDetail`。
`BizScopeDriver.driveBusinessScope` 里 `when (detail)` 分发到对应业务的 Subcomponent.Factory。
**编译期穷尽性**：加新 biz 时编译器逼你加 when 分支。

### 7.2 共享 / 私有 module 池

`@Module(includes = [SharedParserA, SharedParserB])` 把共享 parser 包进 biz subcomponent 的 modules 列表；私有 parser 直接挂在该 biz 的 modules 里。
两边的 Subcomponent 各自生成独立的 `Map<Key, Mapper>`，互不干扰。

### 7.3 接口 + 多 @Binds 实现

跨业务的统一抽象：
```kotlin
interface BizInfoService { ... }
@Binds @BizScope abstract fun bind(impl: UGCInfoService): BizInfoService    // UGCBizModule
@Binds @BizScope abstract fun bind(impl: OGVSeasonService): BizInfoService  // OGVBizModule
```
公共代码（`BizBootstrap`）只注入 `BizInfoService`，不知道是哪个具体实现。

### 7.4 BindsInstance 注入运行时值

`Activity` / `DetailData(具体子类型)` / `VideoItem` 这种构造期才知道的实例，通过 `@BindsInstance` 注入到 Subcomponent.Factory.create 入参。
具体类型（UGCDetail）的 BindsInstance 配合一个 `@Provides fun bindAsAbstract(ugc: UGCDetail): DetailData = ugc`，让公共 service 也能注入抽象类型。

### 7.5 Provider / Lazy 破循环

子 scope service 想注入父 scope 的 Driver，但 Driver 又注入了子 scope 的 Factory → 构造期循环 → Dagger 报错。
解法：用 `Provider<Driver>` 或 `Lazy<Driver>`，把构造时机延后到 `.get()` 调用。

---

## 8. 选集 / 切集 / 切业务三个动词的差别

| 动作 | 触发方 | 调用什么 | 重建什么 scope |
|---|---|---|---|
| 切集（episode） | 列表点击、连播、相关推荐卡 | `selectionRepository.select(id)` → MediaSelectionDispatcher → `mediaScopeDriver.switchTo(item)` | EpisodeScope + MediaScope |
| 切合集（detail） | 顶部标题点击 | `bizScopeDriver.switchToNewVideo(StartParams(bvid))` | BizScope（→ 间接 Episode/Media） |
| 切业务（biz type） | 同上（如果新合集是另一种 biz） | 同上 | BizScope 用另一个 Subcomponent.Factory 重建 |

**EpisodeScope 由 mediaFlow 反向驱动**——`MediaSelectionDispatcher` 写 mediaScopeDriver，`EpisodeScopeDriver` 收 `mediaScopeDriver.currentItemFlow` 起 EpisodeScope。**不要让 EpisodeScopeDriver 直接订 selectionRepository**——这会让"播放器要求切集"（互动视频、自动连播）这种播放器域来源的切集走不通。

---

## 9. 反模式清单（已踩坑或差点踩）

每条都对应本项目历史上的一次错误。**新代码出现这些情形时停下来重新想**。

1. ❌ Fragment 里 `pageDetailRepository.detailFlow.collect { ... }` —— 跨层。
   ✅ DetailTitleService 订阅，暴露 `viewModel: DetailTitleUIComponent.ViewModel`，Fragment 只 bind。

2. ❌ Repo 在 init 里 combine 其他 repo 的 flow 写自己 state —— 形成隐式 fusion repo。
   ✅ 这个 combine 移到 Service 里。

3. ❌ "load + 持有 flow" 都在 Repo 里 (`PageDetailRepository.load() { _flow.value = data }`) —— 状态混在 IO 里。
   ✅ Repo 退化成 `suspend fun loadDetail(...): Result<DetailData>`，状态搬到 BizScopeDriver。

4. ❌ 用 StateFlow 表达"刚刚播完"这类离散事件。
   ✅ `MutableSharedFlow<Unit>(replay=0, extraBufferCapacity=1)` + `tryEmit(Unit)`。

5. ❌ `player.addListener(listener); trySend(player.playbackState)` —— 主动读热属性当事件发射。
   ✅ 只对真实 onXxxChanged 回调反应。

6. ❌ MediaScope 嵌在 EpisodeScope 里 —— 切集时把整个 media 也连带销毁，无法做"音轨切换不重起 episode"。
   ✅ MediaScope 和 BizScope 都直接挂 PageScope，并列。

7. ❌ EpisodeScope 由 selectionRepository 直接驱动 —— 播放器域的"被迫切集"无法触达 EpisodeScope。
   ✅ EpisodeScope 由 `mediaScopeDriver.currentItemFlow` 反向驱动；selection 通过 dispatcher 翻译成 media 切换。

8. ❌ Activity orientation 在 Fragment 里 collect screenState 后调 setRequestedOrientation。
   ✅ `ScreenStateService` 注入 Activity（`@BindsInstance`），自己订 screenStateFlow 改 activity。Fragment 不参与。

9. ❌ "横屏隐藏 BizInfo" 在 Fragment 里 `binding.bizInfo.isVisible = !state.isFullscreen`。
   ✅ `BizInfoService` 自己订 screenStateFlow 改自己 ViewModel.State.visible，UIComponent 绑过去自动隐藏。

10. ❌ 业务无关代码用 `if (detail.businessType == UGC) ... else ...` 写分支。
    ✅ 用 sealed `DetailData` + `when (detail) is UGCDetail -> ... is OGVDetail -> ...`，让编译器穷尽检查。

11. ❌ `OrientationEventListener` 接上后，让 listener 第一次 `onOrientationChanged` 回调进流——这是 hot
    property 的"当前姿势"快照而不是"刚刚旋转"事件。下游会把它当成新事件触发 switchTo*，导致用户竖直拿
    手机点全屏被 sensor 立刻抢回去（与 §6.2 ExoPlayer.playbackState 同型）。
    ✅ 在 listener 内部用 `var firstReading = true` 吃掉首次回调，flow 只发真正旋转触发的角度。

12. ❌ 播放器 Activity 在 manifest 里没声明 `android:configChanges` —— 横竖屏切换时系统会销毁并重建
    Activity，整个 PageScope 跟着死掉重生（ExoPlayer 重新 prepare、Bootstrap.start() 再跑一遍）。
    用户看到的是"切横屏 = 视频从头开始"。
    ✅ Activity 声明 `android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|uiMode"`，
    系统改派 `onConfigurationChanged` 回调，Activity 实例和 Fragment 实例都不销毁，PageScope 存活。
    `ScreenStateService` 写 `activity.requestedOrientation` 仍然驱动方向切换，但不再触发重建。

---

## 10. 写代码前的 5 步检查

每次新增/修改代码前，对自己提以下问题：

1. **状态归属**：这块状态切到下一个什么时应该重置？→ 决定它住哪个 Scope。
2. **层级归属**：是 IO（Repo）/ 业务逻辑（Service）/ UI 渲染（UIComponent）/ 装配（Fragment）？
3. **数据形状**：当前状态 → StateFlow；离散事件 → SharedFlow。订阅瞬间应该收到哪个？
4. **订阅陷阱**：subscribe 时的初始重放/热属性读取会不会触发误判？是否需要 `.drop(1)` / 改成 SharedFlow？
5. **业务无关性**：能不能写得不关心 UGC/OGV 区别？如果写了 if biz_type 分支，是不是该改 sealed when 或 @Binds 多实现？

---

## 11. 文件命名约定

| 类型 | 后缀 | 例子 |
|---|---|---|
| Repository | `XxxRepository` | `PageDetailRepository`, `SelectionRepository` |
| Service | `XxxService` | `DetailTitleService`, `EpisodeCompletedService` |
| Driver | `XxxDriver` | `BizScopeDriver`, `MediaScopeDriver` |
| Bootstrap | `XxxBootstrap` | `PageBootstrap`, `BizBootstrap` |
| Subcomponent | `XxxComponent` | `UGCBizComponent`, `CurrentMediaComponent` |
| Subcomponent.Factory 入口 module | `XxxBizModule` | `UGCBizModule`, `OGVBizModule` |
| @IntoMap 解析 module | `XxxModuleParser` / `XxxParser` | `VideoListModuleParser`, `UGCUploaderBannerParser` |
| UIComponent | `XxxUIComponent` | `BizInfoUIComponent`, `EpisodeTitleUIComponent` |
| Subcomponent 公共门面接口 | `XxxFacade` | `PlayerBizFacade` |

---

## 12. 不在本文件覆盖范围

下列内容跟本架构关系不大，不在本文件管：

- `cocos-shell/`、`render-engine/`、`startupRuntime/`、`ai-sdk/` 等其它模块
- `app/` 主壳的导航、推送、分享等
- `player/` 模块外的 Compose / fragment / activity 设计

---

**最重要的一句**：当性价比和架构纯度冲突时，本 demo 选架构纯度。这是它存在的意义。
