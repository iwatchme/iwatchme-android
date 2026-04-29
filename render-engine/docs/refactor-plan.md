# Render Engine 架构拆分实施方案

## 背景

当前 `render-engine` 已经具备这些能力：

- 单视频播放
- 音频输出与音画同步
- 多片段时间线
- 双轨道叠加（primary + overlay）

能力已经开始叠加，但代码的组织方式还停留在“单引擎类集中管理”的阶段。当前最明显的问题是：

- `engine/RenderEngine.cpp` 体量过大，已经同时承担 API、线程、状态机、A/V 同步、片段切换、Seek、GL 渲染树组装、多轨切换
- 主轨和 overlay 轨的状态字段几乎复制了一套，后续增加第 3 条轨道会进一步膨胀
- 音频线程与视频线程协调、时间线切换、同步算法都混在一起，阅读和排查问题的成本很高
- 资源生命周期分散在多个分支里，失败路径和回滚路径不够清晰

这个文档的目标不是“重写 Render Engine”，而是给出一套 **低风险、可分阶段落地** 的拆分方案。

---

## 当前主要问题

### 1. `RenderEngine` 是 God Object

当前 `RenderEngine` 同时做了这些事：

- 对外 API facade
- JNI 侧可见的引擎对象
- render thread 生命周期
- audio thread 生命周期
- timeline/source 切换
- seek / seekFast / EOF / loop
- A/V sync 算法
- 主轨与 overlay 轨的 demux/decode/render 状态
- GL render tree 构建

这会导致两个直接问题：

- 阅读时必须同时装载过多上下文
- 修改一个点时，很容易误伤别的功能

### 2. “轨道”没有抽象

主轨和 overlay 轨都需要维护这些状态：

- `Timeline`
- `activeClipIndex`
- `DecoderConfig`
- `Demuxer`
- `HwDecoder`
- `SurfaceTextureHelper`
- `SourceNode`
- `skipUntilPtsUs`
- `pending packet`
- `eof`

现在这些字段散落在 `RenderEngine` 中，overlay 逻辑本质上是“复制一套主轨逻辑”。这对后续扩展到多轨、字幕轨、图片轨都不友好。

### 3. 同步逻辑和播放状态机耦合过深

当前 render loop 内同时做：

- packet pump
- output dequeue
- trim / clip 边界判断
- frame timer 调度
- drop policy
- seek / source change 状态重置

同步策略本身是一个独立关注点，应该可以单独阅读和测试。

### 4. 渲染树组装和播放控制耦合

现在这些逻辑都在 `RenderEngine` 中：

- `buildRenderTree`
- `initOverlayPipeline`
- `releaseOverlayPipeline`
- `setOverlayAlpha`

但它们的职责其实是“如何组织节点图”，不是“如何播放时间线”。

### 5. 资源所有权不够清楚

当前代码里还有比较多的裸指针和散落的 `init/release/join/flush`：

- `SourceNode*`
- `OutputNode*`
- `BlendNode*`
- `AVPacket*`

这不一定马上出 bug，但会让失败路径、异常路径和后续重构变得脆弱。

---

## 必须先明确的设计约束

这些约束必须在正式拆分前写清楚，否则中途会出现结构性返工。

### 1. `Demuxer` 的最终归属必须先确定

当前实现里，音频线程和视频线程共享主轨 `Demuxer`，依赖外部互斥锁和内部缓存队列交错读取。

这在现阶段能工作，但对于拆分方案来说有两个问题：

- `AudioPipeline` 很难拥有一个稳定且清晰的 `Demuxer` 依赖接口
- 如果先抽 `AudioPipeline`，再把 `Demuxer` 移入 `VideoTrackPipeline`，会产生一次结构性二次改动

本方案的**推荐终态**是：

- `Demuxer` 归 `VideoTrackPipeline` 所有
- 音频线程不直接调用 `av_read_frame()`
- 单轨的 packet pump 由轨道侧统一完成
- 音频 packet 通过队列转发给 `AudioPipeline`

也就是说，推荐结构是：

```cpp
VideoTrackPipeline -> Demuxer
VideoTrackPipeline -> audio packet queue -> AudioPipeline
```

而不是：

```cpp
VideoTrackPipeline -> Demuxer
AudioPipeline -> Demuxer
```

如果为了降低第一阶段改动量，短期内仍要让 `AudioPipeline` 读取共享数据源，那么必须明确：

- 这是过渡设计，不是目标终态
- `AudioPipeline` 只持有非拥有引用
- 外部同步策略必须显式写清楚

### 2. 片段切换时只能有一个 A/V 协调者

重构后会同时存在：

- `VideoTrackPipeline` 负责视频 clip 切换
- `AudioPipeline` 负责音频 flush / seek / restart

这两边不能各自独立决定切换时机，否则一定会出现状态错位。

本方案明确规定：

- **`PlaybackSession` 是唯一的 A/V 切换协调者**

在 `PlaybackSession` 尚未完全抽出之前，过渡期也必须遵守同一原则：

- 渲染主循环所在模块负责发起 transition
- 它统一调用：
  - 视频轨切换
  - 音频 flush/restart
  - sync reset
  - generation bump

### 3. 线程亲和性必须写进接口设计

拆分后最容易出问题的不是业务逻辑，而是“某个方法到底允许在哪个线程调用”。

因此所有新模块的接口都需要显式标注线程要求：

- 任意线程可调用
- 仅渲染线程可调用
- 仅 GL 线程可调用
- 仅音频线程可调用

尤其是 `VideoTrackPipeline`：

- 任意线程：
  - `isEof()`
  - `mapSourcePtsToTimelineUs()`
  - `isFrameBeforeTrim()`
  - `isFrameAfterTrim()`
- 仅 GL 线程：
  - `init()`
  - `release()`
  - `consumeRenderedFrame()`
- 仅渲染线程：
  - `pumpPackets()`
  - `dequeueFrame()`
  - `releaseFrame()`
  - `switchToClip()`

### 4. `DecoderPool` 未来会进入，所以边界要先留好

本次拆分不引入 `DecoderPool`，但 `VideoTrackPipeline` 的 API 不应把“内部永远独占一个 `HwDecoder`”写死成调用方依赖。

要遵守的原则是：

- 调用方只依赖轨道动作：
  - `pumpPackets`
  - `dequeueFrame`
  - `releaseFrame`
  - `switchToClip`
- 不依赖 decoder 的具体持有方式

这样未来替换为 `DecoderPool` 借用模式时，只改 `VideoTrackPipeline` 内部实现，不改调用方。

---

## 重构目标

这次拆分的核心目标是：

1. 保持现有行为不变，优先提升可读性和职责边界
2. 将“轨道、音频、同步、渲染树、线程协调”拆成独立模块
3. 让主轨和 overlay 轨复用同一套轨道实现
4. 为后续多轨扩展、特效链、导出提供更稳定的代码基础

非目标：

- 不在这次拆分里引入全新的播放模型
- 不重做 JNI / Kotlin API 形态
- 不把同步算法整体替换成另一套实现
- 不立即支持任意条轨道的完全泛化

---

## 目标架构

目标是把现有引擎拆成 5 层。

### 1. `RenderEngine`：对外 Facade

职责：

- 对 Kotlin/JNI 暴露稳定 API
- 持有一个 `PlaybackSession`
- 生命周期入口和释放入口

保留的方法：

- `setSurface`
- `setVideoSource`
- `setTimeline`
- `setMultiTrackTimeline`
- `play / pause / seek / seekFast`
- `getPosition / getDuration / getVideoWidth / getVideoHeight`

这个类不再直接管理：

- 轨道解码器
- 音频线程
- 同步状态
- render loop

### 2. `PlaybackSession`：播放状态机和线程协调器

职责：

- render thread 生命周期
- audio thread 生命周期协调
- 命令状态处理：source change / seek / pause / play / EOF
- 持有主轨、overlay 轨、音频管线、同步控制器
- 统一调度每一帧的播放流程

这是新的“核心协调器”，替代当前 `RenderEngine.cpp` 里绝大部分主流程代码。

### 3. `VideoTrackPipeline`：单轨道视频播放单元

职责：

- 持有单条视频轨道相关的全部状态
- timeline 解析
- clip 切换
- demux / decode / dequeue / render output
- trimIn / trimOut / EOF / pending packet 处理

主轨和 overlay 轨都应该复用这个类。

### 4. `AudioPipeline`：音频播放单元

职责：

- 持有 `AudioDecoder + AudioOutput`
- 管理音频线程
- 管理 seek / flush / restart
- 对外提供 `getAudioClockUs()`

目标是把音频相关逻辑彻底从视频 render loop 中抽离。

### 5. `VideoSyncController`：视频同步策略

职责：

- `frameTimerUs`
- `lastFramePtsUs`
- `consecutiveDrops`
- `reset()`
- `onTimelineReset()`
- `computeTargetDelay()`
- `shouldRender()`

目标是让音画同步算法成为可单独阅读和演进的模块。

---

## 目标目录结构

建议将 C++ 目录逐步收敛为：

```text
render-engine/src/main/cpp/
├── audio/
│   └── AudioOutput.*
├── common/
│   └── log.h
├── core/
│   ├── RenderNode.h
│   ├── SourceNode.*
│   ├── OutputNode.*
│   ├── BlendNode.*
│   └── FxNode.h
├── decode/
│   ├── Demuxer.*
│   ├── HwDecoder.*
│   ├── AudioDecoder.*
│   └── SurfaceTextureHelper.*
├── engine/
│   ├── RenderEngine.*            # Facade
│   ├── PlaybackSession.*         # 线程 + 状态机
│   ├── Timeline.*
│   ├── TimelineValidator.*       # probe / trim 校验
│   └── DecoderConfig.*           # 从 Timeline 中拆出
├── pipeline/
│   ├── VideoTrackPipeline.*      # 单轨视频管线
│   └── AudioPipeline.*           # 音频管线
├── render/
│   ├── RenderGraphBuilder.*      # 渲染树组装
│   └── SurfaceRenderer.*         # output/blend 节点执行辅助
├── sync/
│   └── VideoSyncController.*
├── gl/
│   ├── EglCore.*
│   ├── ShaderProgram.*
│   ├── FullscreenQuad.*
│   └── FBOPair.*
└── render_engine_jni.cpp
```

说明：

- `decode/`、`audio/`、`gl/`、`core/` 这些底层能力层先尽量保留
- 先新建 `pipeline/`、`sync/`、`render/`，而不是一口气搬所有旧文件

---

## 核心对象设计

### `DecoderConfig`

建议从 `Timeline.h` 中拆出，放到 `engine/DecoderConfig.h/.cpp`。

职责：

- 从 `AVCodecParameters` 提取复用判定指纹
- 封装 `codecId / width / height / extradata`

原因：

- `Timeline` 应只负责时间线和 clip 映射
- `DecoderConfig` 属于“解码器复用策略”，不是时间线模型

### `VideoTrackPipeline`

建议持有这些成员：

```cpp
class VideoTrackPipeline {
public:
    bool configureTimeline(Timeline timeline);
    // 仅 GL 线程
    bool init(JNIEnv* env);
    // 仅 GL 线程
    void release(JNIEnv* env);

    // 仅渲染线程
    bool switchToClip(int clipIndex, int64_t sourcePositionUs, JNIEnv* env);
    // 仅渲染线程
    bool seekInCurrentClip(int64_t sourcePositionUs);

    // 仅渲染线程
    bool pumpPackets();
    // 仅渲染线程
    bool pumpAvailablePackets();
    // 仅渲染线程
    bool dequeueFrame(HwDecoder::DecodedFrame& frame, int64_t timeoutUs);
    // 仅渲染线程
    void releaseFrame(int32_t bufferIndex, bool render);

    // 仅 GL 线程
    bool consumeRenderedFrame(JNIEnv* env, int64_t timeoutMs);

    // 任意线程只读
    int64_t mapSourcePtsToTimelineUs(int64_t sourcePtsUs) const;
    // 任意线程只读
    bool isFrameBeforeTrim(int64_t sourcePtsUs) const;
    // 任意线程只读
    bool isFrameAfterTrim(int64_t sourcePtsUs) const;
    // 任意线程只读
    bool isEof() const;

private:
    Timeline timeline_;
    int activeClipIndex_ = -1;
    DecoderConfig activeDecoderConfig_;
    int64_t skipUntilPtsUs_ = 0;

    Demuxer demuxer_;
    HwDecoder decoder_;
    SurfaceTextureHelper stHelper_;
    std::unique_ptr<SourceNode> sourceNode_;

    AVPacket* pendingPkt_ = nullptr;
    bool hasPendingPkt_ = false;
    bool eof_ = false;
};
```

这样主轨和 overlay 轨可以统一成：

```cpp
std::unique_ptr<VideoTrackPipeline> primaryTrack_;
std::unique_ptr<VideoTrackPipeline> overlayTrack_;
```

而不是在 `RenderEngine` 里复制两套字段。

### `AudioPipeline`

建议职责：

```cpp
class AudioPipeline {
public:
    void release();

    void start();
    void stopAndJoin();

    void enqueuePacket(AVPacket* packet);
    void notifySeek(int64_t positionUs, uint32_t generation);
    void flush();
    int64_t getAudioClockUs() const;
    bool hasAudio() const;
};
```

关键约束：

- `AudioPipeline` 不拥有 `Demuxer`
- 推荐终态是消费音频 packet 队列，而不是直接拉取 `Demuxer`
- 如果中间阶段保留共享数据源读取，也必须明确为过渡设计

后续如果要把“线程 stop/join/restart”优化为“线程常驻 + generation 失效”，也能在这个类内部完成，而不会影响上层。

### `VideoSyncController`

建议它不直接感知 `Demuxer`、`Timeline`、`SurfaceTexture`，只关心时间参数：

```cpp
struct SyncDecision {
    int64_t delayUs = 0;
    bool shouldRender = true;
};

class VideoSyncController {
public:
    void reset();
    void onTimelineReset(int64_t framePtsUs, int64_t audioClockUs, int64_t wallNowUs);
    SyncDecision decide(int64_t framePtsUs,
                        int64_t nominalFrameDurationUs,
                        int64_t audioClockUs,
                        int64_t wallNowUs);
};
```

目标是把当前 render loop 里那大段 A/V sync 逻辑变成：

```cpp
auto decision = syncController_.decide(...);
```

---

## `RenderEngine.cpp` 的建议拆分边界

按当前代码形态，建议这样拆。

### 留在 `RenderEngine`

- 构造 / 析构
- facade API
- callback 设置

### 迁到 `PlaybackSession`

- `renderThreadFunc`
- `startRenderThread`
- `stopRenderThread`
- `beginTimelineTransition`
- `endTimelineTransition`
- source change / seek / seekFast / eof 主流程

### 迁到 `AudioPipeline`

- `audioThreadFunc`
- `getAudioClockUs`
- `audioSeekTargetUs_`
- `audioRunning_`
- `hasAudio_`

### 迁到 `VideoTrackPipeline`

- `switchToClip`
- pending packet / eof / decoder config 状态
- 单轨 clip 切换逻辑
- `skipUntilPtsUs_`
- `Demuxer/HwDecoder/SurfaceTextureHelper/SourceNode`

### 迁到 `RenderGraphBuilder`

- `buildRenderTree`
- overlay 启用时 `BlendNode` 的连接方式
- surface 尺寸变化时的 graph rebuild 策略

### 迁到 `VideoSyncController`

- `frameTimerUs`
- `lastFramePtsUs`
- `consecutiveDrops`
- `compute_target_delay` 风格逻辑
- “late frame” 判断逻辑

---

## 内存与资源管理建议

这次拆分时，建议顺手统一资源所有权。

### 1. 裸指针改 `std::unique_ptr`

优先级最高的对象：

- `SourceNode`
- `OutputNode`
- `BlendNode`
- `PlaybackSession`
- `VideoTrackPipeline`
- `AudioPipeline`

这样可以把大量手工 `delete` 变为确定性的 RAII。

### 2. `AVPacket*` 做小封装

现在 `pendingPkt_` 的管理比较容易出错。建议加一个很薄的 RAII：

```cpp
class PacketHolder {
public:
    PacketHolder();
    ~PacketHolder();
    PacketHolder(PacketHolder&&) noexcept;
    PacketHolder& operator=(PacketHolder&&) noexcept;
    PacketHolder(const PacketHolder&) = delete;
    PacketHolder& operator=(const PacketHolder&) = delete;
    AVPacket* get();
    void unref();
};
```

目标不是大改 FFmpeg 用法，而是避免散落的 `av_packet_alloc/free/unref`。

语义要求：

- 析构函数调用 `av_packet_free()`
- `unref()` 调用 `av_packet_unref()`

### 3. “先构建新状态，再提交切换”

片段切换和 overlay 初始化尽量遵守这个顺序：

1. probe / open / init 新状态
2. 全部成功后再替换活动状态
3. 失败时保持旧状态不变，或者明确进入 error state

不要继续扩大“先 release 旧状态，再尝试构建新状态”的路径。

---

## 分阶段实施方案

这次拆分建议分 6 个阶段完成，每一阶段都要可编译、可回归。

### Phase 0：补文档和边界约束

目标：

- 明确当前模块职责
- 标出待迁移函数和成员
- 冻结关键行为基线

动作：

- 更新 `architecture.md`
- 增加本拆分方案文档
- 记录现有关键日志和回归用例

验证：

- 当前 `:render-engine:assembleDebug` 可通过
- 单视频 / timeline / overlay / seek 行为有可对照基线

### Phase 1：抽纯数据与纯算法

目标：

- 不改行为，只抽离不依赖线程和资源的代码

新增文件：

- `engine/DecoderConfig.h/.cpp`
- `sync/VideoSyncController.h/.cpp`
- `engine/TimelineValidator.h/.cpp`

动作：

- 从 `Timeline.h` 移出 `DecoderConfig`
- 将 `RenderEngine.cpp` 里的同步状态和计算逻辑迁到 `VideoSyncController`
- 将 `setTimeline()` / `setMultiTrackTimeline()` 中的 probe / trim 校验迁到 `TimelineValidator`

注意：

- `TimelineValidator` 只负责校验和 probe 结果输出
- `Timeline` / `overlayTimeline` 的最终设置仍由调用方提交

收益：

- `RenderEngine.cpp` 第一轮瘦身
- 同步策略可独立阅读

验证：

- 单视频播放回归
- A/V sync 日志与当前量级一致
- timeline probe 行为不变

### Phase 2：抽单轨 `VideoTrackPipeline`

目标：

- 先把主轨从 `RenderEngine` 中独立出去
- 先确定 `Demuxer` 的归属，避免后续 `AudioPipeline` 二次改动

新增文件：

- `pipeline/VideoTrackPipeline.h/.cpp`

迁移内容：

- `Timeline`
- `activeClipIndex`
- `DecoderConfig`
- `Demuxer`
- `HwDecoder`
- `SurfaceTextureHelper`
- `SourceNode`
- `skipUntilPtsUs`
- `pendingPkt / hasPendingPkt / eof`
- `switchToClip`
- packet pump / frame dequeue / frame release
- 音频 packet 转发边界

接口目标：

- `pumpPackets()`
- `pumpAvailablePackets()`
- `dequeueFrame()`
- `releaseFrame()`
- `consumeRenderedFrame()`
- `switchToClip()`
- `seekInCurrentClip()`
- `drainAudioPacketsTo(AudioPipeline&)`

Phase 2 过渡期补充：

- 这一阶段 `AudioPipeline` 还不存在
- 但主轨 `Demuxer` 已经迁入 `VideoTrackPipeline`
- 因此必须提供一个**临时音频包访问接口**，供仍留在会话层或 `RenderEngine` 中的旧音频线程消费

建议过渡接口：

- `bool popAudioPacket(AVPacket* outPacket)`
- 或 `bool dequeueAudioPacket(PacketHolder& outPacket)`

过渡期推荐流程：

1. `VideoTrackPipeline::pumpPackets()` 从 `Demuxer` 统一读取 packet
2. 视频 packet 走现有 video 路径
3. 音频 packet 进入轨道内部音频队列
4. 旧音频线程通过 `popAudioPacket()` 从轨道取音频包
5. Phase 3 引入 `AudioPipeline` 后，再把这条路径替换为 `enqueuePacket()` 推模式

约束：

- Phase 2 的 `popAudioPacket()` 是过渡接口，不是最终设计
- 最终目标仍然是队列转发给 `AudioPipeline`

约束：

- 这一阶段开始，clip transition 的发起者必须是会话协调层
- `VideoTrackPipeline` 不直接决定完整音频切换，只负责本轨状态切换

验证：

- 单轨 timeline 播放正常
- trimIn / trimOut 正常
- clip 切换正常
- seek / seekFast 正常
- 音频播放仍正常
- 旧音频线程可以通过轨道临时接口持续拿到音频包

### Phase 3：抽 `AudioPipeline`

目标：

- 把音频线程和音频时钟独立出去
- 直接建立在“`Demuxer` 已属于 `VideoTrackPipeline`”的前提上

新增文件：

- `pipeline/AudioPipeline.h/.cpp`

迁移内容：

- `audioThreadFunc`
- `AudioDecoder`
- `AudioOutput`
- `audioRunning_`
- `audioSeekTargetUs_`
- `getAudioClockUs()`
- 音频线程 `start / stop / flush / seek`
- 音频 packet 队列消费

上层协调器只通过接口调用：

- `audioPipeline_.start()`
- `audioPipeline_.stopAndJoin()`
- `audioPipeline_.notifySeek(...)`
- `audioPipeline_.flush()`
- `audioPipeline_.enqueuePacket(...)`
- `audioPipeline_.getAudioClockUs()`

约束：

- 音频切换只能由会话协调层触发
- `AudioPipeline` 不自行决定 clip 边界时机

验证：

- 单视频音频播放正常
- pause / resume 正常
- seek / seekFast 不串旧音频
- timeline clip 切换时音视频协调正常
- `AVSYNC audio-*` 日志行为不变

### Phase 4：overlay 轨复用 `VideoTrackPipeline`

目标：

- 删除 `RenderEngine` 中 overlay 专用的一整套重复状态

动作：

- 用第二个 `VideoTrackPipeline` 实例替代：
  - `overlayDemuxer_`
  - `overlayHwDecoder_`
  - `overlayStHelper_`
  - `overlaySourceNode_`
  - `overlayTimeline_`
  - `overlayActiveClipIndex_`
  - `overlayDecoderConfig_`
  - `overlaySkipUntilPtsUs_`
  - `overlayPkt_`
  - `overlayHasPendingPkt_`
  - `overlayEof_`

保留在 session / renderer 层的只应是：

- `hasOverlay`
- `overlayAlpha`
- `overlayTrack`

验证：

- primary + overlay 双轨播放正常
- overlay 切 clip 正常
- `BlendNode` 输出正常
- 不影响主轨 A/V sync

### Phase 5：抽 `RenderGraphBuilder`

目标：

- 把“图如何组装”从“播放如何推进”里拆出去

新增文件：

- `render/RenderGraphBuilder.h/.cpp`

职责：

- 创建 `OutputNode`
- 需要 overlay 时创建 `BlendNode`
- 连接 primary / overlay 的 `SourceNode`
- 更新 `overlayAlpha`

这样 `PlaybackSession` 不再直接 new / delete 节点图，只负责拿到一个 render graph 并执行。

验证：

- 单轨图与双轨图都能正确构建
- surface 尺寸变化时可重建
- alpha 修改即时生效

### Phase 6：抽 `PlaybackSession`，RenderEngine 变薄

目标：

- 最终把“大主循环”彻底移出 `RenderEngine`

新增文件：

- `engine/PlaybackSession.h/.cpp`

职责：

- render thread
- session 状态机
- source change / seek / eof / loop
- 调用 `VideoTrackPipeline + AudioPipeline + VideoSyncController + RenderGraphBuilder`

最终 `RenderEngine` 应该变成：

- 轻量 facade
- 不再持有底层资源字段
- 不再有千行级别实现

注意：

- `PlaybackSession.cpp` 不能只是新的 God Object
- 如果这一阶段完成后它仍明显膨胀，建议继续拆出：
  - `SessionCommandHandler`
  - `SessionTransitionCoordinator`
  - `SessionLoopController`

验证：

- `RenderEngine.cpp` 控制在 200 到 300 行左右
- 所有现有功能回归通过

---

## 每个阶段的验收标准

每一阶段都建议至少做这 6 类回归：

1. 单文件播放
2. pause / resume
3. seek / seekFast
4. timeline 多片段顺序播放
5. overlay 双轨播放
6. A/V sync 诊断日志是否仍合理

额外建议每阶段都跑：

```bash
./gradlew :render-engine:assembleDebug
```

如果 demo 在 `app` 中直接依赖，还要补：

```bash
./gradlew :app:assembleDebug
```

---

## 实施优先级建议

如果只做最有价值的第一轮拆分，我建议顺序是：

1. `VideoSyncController`
2. `VideoTrackPipeline`
3. `AudioPipeline`

原因：

- 先抽同步和单轨，先把主流程边界稳定下来
- 先确定 `Demuxer` 归属，再抽音频，避免 `AudioPipeline` 二次改动
- 风险相对可控，收益最大

如果这一轮完成，后面再做：

4. overlay 轨复用
5. `RenderGraphBuilder`
6. `PlaybackSession`

---

## 预期结果

完成这轮拆分后，理想状态是：

- `RenderEngine`：对外 facade，薄
- `PlaybackSession`：状态机和线程协调
- `VideoTrackPipeline`：单轨复用单元
- `AudioPipeline`：音频线程和音频时钟
- `VideoSyncController`：同步算法
- `RenderGraphBuilder`：节点图组装

最终收益：

- 阅读路径更清楚
- 失败路径更容易梳理
- 多轨扩展不会继续复制字段
- 同步策略和播放策略可以独立演进
- 后续做导出、特效链、更多轨道时，代码基础更稳
