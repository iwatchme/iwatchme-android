# Render Engine 开发计划

## Phase 1 — 单视频播放 (已完成)

| 步骤 | 内容 | 状态 |
|------|------|------|
| 模块脚手架 | render-engine 模块 + CMake + JNI 验证 | 已完成 |
| EGL + 渲染线程 | 手动 EGL 上下文，C++ std::thread | 已完成 |
| Shader 工具 | ShaderProgram + FullscreenQuad | 已完成 |
| Demuxer | FFmpeg 解封装 + AVCC→Annex B BSF | 已完成 |
| HwDecoder + SurfaceTexture | AMediaCodec Surface 模式 + OES 纹理 | 已完成 |
| 渲染树节点 | RenderNode / SourceNode / OutputNode / FxNode(stub) / BlendNode(stub) | 已完成 |
| 播放循环 | PTS 帧节奏 + play/pause/seek + EOF 回调 | 已完成 |
| 快速 Seek | 两阶段 seek（拖动关键帧预览 + 松手精确定位） | 已完成 |
| OnFrameAvailableListener | SurfaceTexture 回调 + JNI_OnLoad 类缓存 | 已完成 |
| Kotlin API + Demo | RenderEngine.kt / RenderEngineView / DemoActivity | 已完成 |

---

## Phase 2 — 音频 + 多片段 + 多轨道

### Step 1: 音频播放 + 音画同步 (当前)

**目标**：视频播放时同步输出音频，以音频时钟驱动视频帧节奏。

**新增文件**：
- `decode/AudioDecoder.h/.cpp` — FFmpeg 软解码 + swresample 重采样为 PCM
- `audio/AudioOutput.h/.cpp` — AAudio 输出（blocking write 模式）

**修改文件**：
- `Demuxer` — 增加音频流支持（readAudioPacket、音频参数查询）
- `RenderEngine` — 音频线程、音频时钟、音画同步逻辑

**音画同步策略**：
- 音频为主时钟（人耳对音频不连续比眼睛对画面不连续更敏感）
- 音频线程持续解码 + 写入 AudioTrack，追踪当前播放 PTS
- 视频线程读取音频时钟，调整帧渲染时机：
  - 视频帧 PTS > 音频时钟 → 等待
  - 视频帧 PTS < 音频时钟 → 立即渲染（不丢帧）

### Step 2: 多片段时间线

**目标**：支持多个视频片段顺序拼接播放。

- 时间线管理器（Timeline）：`[clip1: 0-5s] [clip2: 5-12s]`
- 根据全局播放位置确定当前片段，切换 Demuxer/Decoder
- 片段间的 gapless 切换
- 涉及 seek 跨片段的处理

### Step 3: 多轨道

**目标**：支持主轨 + 画中画 / 字幕叠加层。

- 每条轨道独立的 SourceNode + 解码管线
- BlendNode 实现多轨道合成
- 各轨道独立的时间偏移和裁剪

---

## Phase 3 — 特效 + 导出

### Step 1: FxNode 特效实现

- 颜色调整（亮度 / 对比度 / 饱和度） — GLSL uniform
- LUT 滤镜 — 3D 纹理查表
- 模糊 / 锐化 — 多 pass + FBOPair
- 转场效果 — 两个输入纹理 + 混合系数

### Step 2: BlendNode 混合模式

- Alpha 混合、叠加、正片叠底
- 画中画：位置/大小/圆角

### Step 3: 视频导出

- EglCore 切换到编码器 Surface：`makeCurrent(encoderSurface)`
- MediaCodec 编码器 + MediaMuxer 输出 MP4
- `setPresentationTime()` 设置每帧时间戳
- 渲染树同时服务预览和导出，只需切换输出目标
- 音频 passthrough 或重编码

---

## 架构原则

1. **手动 EGL** — 不用 GLSurfaceView，支持运行时切换输出 Surface
2. **FFmpeg 只做解封装** — 视频硬解码走 AMediaCodec Surface 模式（零拷贝）
3. **音频软解码** — FFmpeg avcodec + swresample → PCM → AAudio
4. **渲染树** — 后序遍历执行，每个节点返回纹理 ID，天然支持多轨道和特效链
5. **音频主时钟** — A/V 同步以音频为准，视频向音频对齐
