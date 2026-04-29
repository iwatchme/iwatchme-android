# Render Engine 架构文档

## 概述

Render Engine 是一个基于 C++ 的 Android 视频渲染引擎模块，目标是构建类似 B 站蒙太奇（Montage）的视频编辑/编码引擎。当前处于 Phase 1 阶段，已实现单视频文件的导入和播放。

核心特性：
- FFmpeg 解封装 + NDK AMediaCodec 硬解码（Surface 模式，零拷贝）
- 手动 EGL 上下文管理（不依赖 GLSurfaceView）
- 渲染树（Render Tree）节点层次结构
- PTS 精确帧节奏控制
- 专用 C++ 渲染线程

---

## 模块结构

```
render-engine/
├── build.gradle.kts          # Android Library, NDK 27.2.12479018, CMake 3.22.1
├── CMakeLists.txt             # 链接 FFmpeg + NDK 系统库
├── src/main/
│   ├── AndroidManifest.xml
│   ├── cpp/
│   │   ├── render_engine_jni.cpp        # JNI 入口
│   │   ├── common/
│   │   │   └── log.h                    # Android 日志宏
│   │   ├── gl/
│   │   │   ├── EglCore.h/.cpp           # EGL display/context/surface 管理
│   │   │   ├── ShaderProgram.h/.cpp     # GLSL shader 编译/链接
│   │   │   ├── FullscreenQuad.h/.cpp    # 全屏四边形 (两个三角形)
│   │   │   └── FBOPair.h/.cpp           # FBO ping-pong (Phase 2+ 使用)
│   │   ├── decode/
│   │   │   ├── Demuxer.h/.cpp           # FFmpeg 解封装 + AVCC→Annex B 转换
│   │   │   ├── HwDecoder.h/.cpp         # NDK AMediaCodec 硬解码
│   │   │   └── SurfaceTextureHelper.h/.cpp  # SurfaceTexture JNI 桥接
│   │   ├── core/
│   │   │   ├── RenderNode.h             # 渲染树基类
│   │   │   ├── SourceNode.h/.cpp        # 叶节点: OES→2D 纹理转换
│   │   │   ├── OutputNode.h/.cpp        # 根节点: 输出到屏幕
│   │   │   ├── FxNode.h                 # 特效节点 (Phase 1 stub)
│   │   │   └── BlendNode.h              # 混合节点 (Phase 1 stub)
│   │   └── engine/
│   │       └── RenderEngine.h/.cpp      # 主引擎: 渲染线程 + 播放控制
│   └── kotlin/com/iwatchme/renderengine/
│       ├── RenderEngine.kt              # Kotlin API (native 方法封装)
│       └── RenderEngineView.kt          # SurfaceView 子类 (生命周期管理)
```

---

## 视频渲染管线 (完整数据流)

一帧视频从文件到屏幕的完整路径：

```
┌─────────────────────────────────────────────────────────────────────┐
│  MP4 文件                                                          │
│    │                                                               │
│    ▼                                                               │
│  Demuxer (FFmpeg avformat)                                         │
│    │  av_read_frame() → 取出压缩 packet                            │
│    │  h264_mp4toannexb BSF → AVCC 格式转 Annex B 格式              │
│    ▼                                                               │
│  HwDecoder (NDK AMediaCodec, Surface 模式)                         │
│    │  queuePacket() → 送入压缩数据                                  │
│    │  dequeueAndRender() → 解码 + releaseOutputBuffer(render=true)  │
│    ▼                                                               │
│  SurfaceTexture (Java 对象, 通过 JNI 桥接)                          │
│    │  updateTexImage() → 最新帧锁存到 OES 纹理                      │
│    │  getTransformMatrix() → 4x4 纹理坐标变换矩阵                   │
│    ▼                                                               │
│  GL_TEXTURE_EXTERNAL_OES (OES 纹理, 绑定到 SurfaceTexture)          │
│    │                                                               │
│    ▼                                                               │
│  ╔═══════════════════════════════════════════╗                      │
│  ║  渲染树 (Render Tree)                      ║                      │
│  ║                                           ║                      │
│  ║  OutputNode (根节点)                       ║                      │
│  ║    │  glBindFramebuffer(0) → 屏幕          ║                      │
│  ║    │  passthrough shader 采样 2D 纹理       ║                      │
│  ║    │                                      ║                      │
│  ║    └── SourceNode (叶节点)                 ║                      │
│  ║         │  glBindFramebuffer(fbo_)         ║                      │
│  ║         │  OES shader 采样 OES 纹理         ║                      │
│  ║         │  应用 uTexMatrix 变换              ║                      │
│  ║         └── 输出 GL_TEXTURE_2D (outputTex_) ║                      │
│  ╚═══════════════════════════════════════════╝                      │
│    │                                                               │
│    ▼                                                               │
│  eglSwapBuffers() → 画面显示到 SurfaceView                         │
└─────────────────────────────────────────────────────────────────────┘
```

### 关键步骤详解

#### 1. 解封装 (Demuxer)

`Demuxer` 使用 FFmpeg 的 `avformat` 库打开 MP4 文件，逐帧读取压缩视频 packet。

**AVCC → Annex B 转换**：MP4 容器中 H.264 数据采用 AVCC 格式（length-prefixed），而 Android MediaCodec 需要 Annex B 格式（start-code prefixed: `0x00000001`）。Demuxer 内部通过 FFmpeg 的 `h264_mp4toannexb`（或 `hevc_mp4toannexb`）bitstream filter 自动完成转换，包括 CSD（codec-specific data）的格式也一并转换。

#### 2. 硬解码 (HwDecoder)

`HwDecoder` 封装 NDK `AMediaCodec`，运行在 **Surface 模式**下：
- 解码后的帧直接输出到 SurfaceTexture 的 ANativeWindow，**零拷贝**
- `queuePacket()` 使用 50ms 超时轮询输入 buffer，接受成功返回 true，codec 满则返回 false
- `dequeueAndRender()` 轮询输出 buffer，有帧时 `releaseOutputBuffer(render=true)` 将帧送到 Surface
- CSD 配置：H.264 需要将 SPS 和 PPS 分别设置为 `csd-0` 和 `csd-1`

**为什么不用 FFmpeg 的 h264_mediacodec 解码器**：FFmpeg 的 MediaCodec wrapper 走 buffer 模式，无法拿到 OES 纹理，失去零拷贝优势。

#### 3. SurfaceTexture 桥接 (SurfaceTextureHelper)

`SurfaceTextureHelper` 通过 JNI 创建和管理 Java 层的 `SurfaceTexture` 对象：
1. C++ 层创建 OES 纹理 (`glGenTextures`)
2. 通过 JNI 创建 Java `SurfaceTexture(texId)` → `Surface` → `ANativeWindow`
3. ANativeWindow 传给 AMediaCodec 作为输出目标
4. 解码完成后调用 `SurfaceTexture.updateTexImage()` 将最新帧锁存到 OES 纹理
5. `getTransformMatrix()` 获取 4x4 变换矩阵，用于校正纹理坐标（不同设备/编码器的纹理方向可能不同）

#### 4. 渲染树 (Render Tree)

见下方独立章节。

#### 5. 帧节奏控制 (Frame Pacing)

采用 **PTS 锚定法**而非固定 sleep：
- 第一帧解码时，记录 `anchorPtsUs`（视频时间戳）和 `anchorWallUs`（系统时钟）
- 后续每帧计算：`sleepUs = (framePtsUs - anchorPtsUs) - (nowUs - anchorWallUs)`
- `sleepUs > 0`：帧提前了，sleep 等待
- `sleepUs < 0`：帧延迟了，立即渲染（不丢帧）
- Seek / Pause-Resume / 重新加载时重置锚点

---

## 渲染树 (Render Tree)

### 设计理念

渲染树是整个引擎的核心架构抽象。它将"如何处理视频帧"建模为一棵 **GPU 处理节点树**，每个节点接收输入纹理、通过 shader 处理、输出结果纹理。

```cpp
class RenderNode {
public:
    virtual GLuint execute(int64_t timelinePositionUs) = 0;
    std::vector<RenderNode*> inputs;   // 子节点
    int outputWidth, outputHeight;
};
```

核心接口：`execute(pos)` — 在给定时间线位置执行渲染，返回输出纹理 ID。父节点调用子节点的 `execute()` 获取输入纹理，形成 **后序遍历** 的递归执行模式。

### 当前节点 (Phase 1)

| 节点 | 角色 | 行为 |
|------|------|------|
| `SourceNode` | 叶节点 | OES 纹理 → FBO + OES shader → 标准 2D 纹理 |
| `OutputNode` | 根节点 | 接收 2D 纹理 → passthrough shader → 渲染到屏幕 (framebuffer 0) |
| `FxNode` | 特效节点 | Phase 1 直通 stub，透传子节点纹理 |
| `BlendNode` | 混合节点 | Phase 1 直通 stub，透传第一个子节点纹理 |

当前树结构（最简形态）：

```
OutputNode
  └── SourceNode
```

### 为什么 SourceNode 必须做 OES → 2D 转换

这是整个渲染树能工作的前提条件：

1. **OES 纹理 (`GL_TEXTURE_EXTERNAL_OES`) 不能挂载到 FBO**。这是 OpenGL ES 规范限制。
2. 渲染树中的中间节点（FxNode、BlendNode）需要将处理结果写入 FBO 再传给下游。
3. 因此必须在树的入口处（SourceNode）将 OES 纹理"翻拍"为标准 `GL_TEXTURE_2D`。

转换过程：
```
OES 纹理 → [FBO + OES Shader] → GL_TEXTURE_2D
                                     │
                                     ▼ (可以挂载到任何 FBO)
                              后续节点自由处理
```

### Phase 2/3 目标树结构

```
OutputNode (→ 屏幕 或 编码器 Surface)
  └── BlendNode (alpha 混合多轨道)
        ├── FxNode[色彩滤镜] → SourceNode[主视频]
        ├── FxNode[模糊] → SourceNode[画中画视频]
        └── SourceNode[字幕/贴纸图层]

每个 SourceNode 各自对应一个独立的解码管线 (Demuxer + HwDecoder + SurfaceTexture)
```

---

## 线程模型

```
┌────────────────────┐     ┌─────────────────────────────────────┐
│   Main Thread      │     │   Render Thread (C++ std::thread)   │
│   (UI / JNI)       │     │                                     │
│                    │     │   1. AttachCurrentThread (JNIEnv)    │
│  setSurface() ────────▶  │   2. EGL init                       │
│  setVideoSource() ────▶  │   3. 主循环:                         │
│  play() ──────────────▶  │      - 检查 surface/video/seek 变更  │
│  pause() ─────────────▶  │      - 解封装 → 解码 → 渲染树        │
│  seek() ──────────────▶  │      - PTS 帧节奏控制                │
│                    │     │      - eglSwapBuffers                │
│                    │     │   4. cleanup + DetachCurrentThread   │
└────────────────────┘     └─────────────────────────────────────┘

通信方式:
- std::atomic<bool>    : windowChanged_, videoSourceChanged_, playing_, running_
- std::atomic<int64_t> : seekTargetUs_, currentPositionUs_
- std::mutex + condition_variable : cmdMutex_ / cmdCond_ (唤醒空闲线程)
```

### 为什么手动管理 EGL 而非 GLSurfaceView

- GLSurfaceView 绑死一个 Surface，无法在运行时切换输出目标
- 后续 Phase 需要切换到编码器 Surface (`makeCurrent(encoderSurface)`) 实现视频导出
- `EglCore` 已预留 `setPresentationTime()` 接口用于编码时的精确时间戳设置

---

## 已解决的关键问题

### 1. Surface 生命周期竞态

**问题**：文件选择器打开 → Activity 暂停 → Surface 销毁 → 文件选择器返回 → `setVideoSource()` 被调用但此时 EGL surface 不存在 → flag 被消费但管线未初始化。

**解决**：`videoSourceChanged_` 只在 `eglSurface_ != EGL_NO_SURFACE` 时才消费。Surface 重建后检查到 flag 仍为 true，重新初始化管线。

### 2. AVCC / Annex B 格式不匹配

**问题**：MP4 中的 H.264 packet 是 AVCC 格式（length-prefixed），直接送给 MediaCodec 导致解码异常。

**解决**：在 Demuxer 中添加 `h264_mp4toannexb` bitstream filter，所有 packet 和 CSD 统一转为 Annex B 格式。H.264 的 CSD 需要将 SPS 和 PPS 拆分为 `csd-0` 和 `csd-1`。

### 3. 花屏 (Packet 丢失)

**问题**：当 `queuePacket()` 因 codec 输入 buffer 满而失败时，仍然调用了 `av_packet_unref()` 释放了 packet，导致 H.264 参考帧丢失，后续帧无法正确解码。

**解决**：引入 persistent pending packet 机制——`hasPendingPkt` 标志。packet 只有在成功送入 codec 后才释放；否则保留到下一轮迭代重试。

### 4. 播放速度不正确

**问题**：一次性 drain 所有已解码帧并立即渲染，导致快速播放。

**解决**：改为每轮迭代只 dequeue 一帧，使用 PTS 锚定法控制帧间间隔。

### 5. 模拟器兼容性

**现象**：Emulator 的 `c2.goldfish.h264.decoder` 在 Surface 模式下死锁。

**结论**：这是模拟器 codec 实现的 bug，非代码问题。切换到真机测试正常。

---

## 外部依赖

| 库 | 来源 | 用途 |
|----|------|------|
| FFmpeg (avformat, avcodec, avutil, swresample, swscale) | 预编译 (arm64-v8a) | 视频解封装 |
| NDK log | 系统库 | Android logcat |
| NDK android | 系统库 | ANativeWindow API |
| NDK EGL | 系统库 | EGL 上下文管理 |
| NDK GLESv3 | 系统库 | OpenGL ES 3.0 |
| NDK mediandk | 系统库 | AMediaCodec / AMediaFormat |

无第三方 Gradle 依赖。

---

## Phase 1 已知限制

- Seek 精度：只到关键帧（不做逐帧向前解码）
- 无音频播放
- FxNode / BlendNode 是直通 stub，未实现实际特效
- 不支持多片段 / 多轨道
- SurfaceTexture 的 `OnFrameAvailableListener` 未连接（当前依赖 dequeue 超时机制）
- 不支持视频导出（EGL 架构已预留支持）
