# 音画同步 (A/V Sync) 技术文档

## 概述

`RenderEngine` 当前采用 **音频主时钟 (Audio Master Clock)** 方案：

- 音频线程持续解码并写入 `AAudio`
- 视频线程读取当前音频播放位置
- 视频帧的显示时机由音频时钟驱动

这套设计的目标不是“让视频自己按 PTS 匀速跑”，而是：

> **让音频连续播放，把视频调度到尽可能贴近音频的时间线。**

---

## 当前实现的关键结论

这次同步问题最终确认有两个核心点：

1. **音频时钟必须跟真实硬件消费进度走**
   不能用“最近一次写入的 PTS”直接冒充当前播放位置。

2. **正常播放态不能阻塞等待 MediaCodec input buffer**
   否则视频主循环会被 `dequeueInputBuffer(timeout)` 自己卡慢，导致画面持续落后音频。

当前代码已经按这个原则修正。

---

## 整体架构

```
                    ┌──────────────────┐
                    │     Demuxer      │
                    │  (FFmpeg 解封装)  │
                    └────────┬─────────┘
                             │
                 readVideoPacket / readAudioPacket
                             │
              ┌──────────────┴──────────────┐
              │                             │
              ▼                             ▼
   ┌───────────────────┐        ┌───────────────────┐
   │    HwDecoder       │        │   AudioDecoder    │
   │ (MediaCodec 硬解)  │        │ (FFmpeg 软解 +    │
   │                    │        │  swresample 重采样)│
   └────────┬──────────┘        └────────┬──────────┘
            │                            │
            ▼                            ▼
   ┌───────────────────┐        ┌───────────────────┐
   │  SurfaceTexture    │        │    AudioOutput    │
   │  → OES 纹理        │        │  (AAudio 输出)     │
   │  → 渲染树渲染       │        │                   │
   │  → EGL SwapBuffers │        │ getPlayback-      │
   └───────────────────┘        │ PositionUs() ◄──── 音频主时钟
                                 └───────────────────┘
```

---

## 线程模型

### 渲染线程 `renderThread_`

正常播放主循环的关键步骤是：

1. 从 `Demuxer` 读取视频 packet
2. **非阻塞**尝试送入 `MediaCodec`
3. 取出一个解码后的视频帧并送到 `SurfaceTexture`
4. `waitForFrame()` + `updateTexImage()`
5. 根据音频时钟决定视频显示时机
6. 执行渲染树并 `swapBuffers`

### 音频线程 `audioThread_`

1. 从 `Demuxer` 读取音频 packet
2. `AudioDecoder::decode()` 解码并重采样为设备实际输出格式
3. `AudioOutput::write()` 写入 `AAudio`
4. `AudioOutput::getPlaybackPositionUs()` 为视频线程提供音频主时钟

---

## 音频时钟实现

音频时钟当前采用：

```text
audioClockUs = basePtsUs + (currentFramesRead - baseFramesRead) * 1_000_000 / sampleRate
```

其中：

- `basePtsUs`：flush/open 后首次有效音频写入的起始 PTS
- `baseFramesRead`：建立锚点时 `AAudioStream_getFramesRead()` 的值
- `currentFramesRead`：当前时刻设备已真实消费的帧数
- `sampleRate`：`AAudio` 实际打开后的输出采样率

### 为什么这样做

因为它跟的是 **设备真实已消费的音频帧数**，而不是“理论上应该播到哪”。

这比“最近一次 write 的 PTS + 墙钟外推”更稳定，尤其是在：

- pause / resume
- seek
- 设备内部音频缓冲存在延迟

这些情况下更不容易漂。

### 设备实际采样率

`AudioDecoder` 不再固定输出 `44100Hz`。

当前流程是：

1. `AudioOutput::open(requestedSampleRate, requestedChannels)`
2. 从 `AAudioStream` 读取 **实际** `sampleRate / channelCount`
3. `AudioDecoder` 用这个实际输出格式初始化重采样器

这样可避免“解码输出采样率”和“设备实际播放采样率”不一致带来的系统性快慢偏差。

---

## MediaCodec 输入语义

这是这次修复的另一个关键点。

### 之前的问题

如果正常播放态使用“阻塞等待 input buffer”的语义，比如：

```cpp
AMediaCodec_dequeueInputBuffer(codec_, 50000);
```

然后主循环又写成“持续喂包，直到 codec 满”，那么每轮循环最后一次失败都可能白等几十毫秒。

结果就是：

- `feedUs` 接近视频帧间隔
- 视频吞吐被自己卡慢
- 视频越来越落后于音频

### 当前正确做法

`HwDecoder` 现在拆成两类接口：

- `tryQueuePacket()`：**正常播放态**
  - 非阻塞
  - 没有 input buffer 就立即返回 `false`

- `queuePacketWithTimeout()`：**seek / 预热 / 精确定位**
  - 允许在给定超时内等待
  - 目标是尽快解出目标帧，而不是维持 steady-state 播放节奏

### 设计原则

> 正常播放态的 codec pump 必须是 opportunistic 的，不能把渲染线程绑死在 input timeout 上。

---

## 视频同步算法

视频侧仍然使用 FFplay 风格的两段逻辑：

### 1. `frame_timer`

`frame_timer` 是累积式显示时间线。

它不是“这帧 sleep 多久”的一次性变量，而是：

```text
frameTimerUs += delay
```

不断累积推进的“视频应该显示到哪一个墙钟时刻”。

### 2. `compute_target_delay`

视频线程每拿到一帧后，会计算：

```text
diff = framePtsUs - audioClockUs
```

然后按阈值调整本帧 delay：

- `diff < -threshold`：视频落后音频，缩短 delay，甚至压到 0
- `diff > threshold`：视频超前音频，延长 delay
- `|diff|` 在阈值内：按正常帧间隔显示

这部分逻辑在当前代码里已经足够稳定，前一阶段的主要问题并不在这里，而在“视频喂包阶段自己被卡慢”。

---

## 保守丢帧策略

当前实现保留了一个 **很保守** 的丢帧判断：

- 仅在视频明显迟到时，允许少量跳过显示
- 每轮最多连续丢很少的帧
- 不再使用之前那种“为了追赶而连续吞很多帧”的激进策略

这样做的目的不是主动追赶音频，而是避免极端情况下把整条视频时间线彻底拖死。

---

## Seek 同步

Seek 时当前实现有两层保护：

### 1. timeline generation / transition

每次：

- `setVideoSource`
- `seek`
- `seekFast`
- EOF restart

都会进入新的 timeline generation。

音频线程在读取、解码、写出前后都会检查 generation。
如果 generation 已变化，就直接丢弃旧时间线的数据，不再把旧 PCM 写入 `AAudio`。

### 2. 音频与视频分别 flush

- 视频线程负责：
  - `demuxer_.seek()`
  - `hwDecoder_.flush()`

- 音频线程负责：
  - `audioDecoder_.flush()`
  - `audioOutput_.flush()`

这样可以保证 seek 后：

- 视频不会继续消费旧 GOP
- 音频不会继续用旧缓冲里的数据建立时钟

---

## 调试日志

当前保留了两类关键日志，便于继续定位问题：

### `AVSYNC`

用于看时间线关系：

- `audio-anchor`
- `audio-diag`
- `video-timeline-reset`
- `video-diag`

重点看：

```text
diffUs = framePtsUs - audioClockUs
```

### `AVPERF`

用于看每帧耗时分布：

- `feedUs`
- `dequeueUs`
- `waitUs`
- `updateUs`
- `executeUs`
- `swapUs`
- `totalUs`

这组日志在这次修复中起了决定性作用，因为它直接暴露了：

> 之前真正卡住视频的不是 GL，也不是 SurfaceTexture，而是正常播放态错误地阻塞等待 MediaCodec input buffer。

---

## 当前结论

这套实现当前已经满足：

1. 音频时钟跟真实硬件消费进度同步
2. 正常播放态不会再被 codec input timeout 拖慢
3. 视频不会再出现“越播越落后音频”的发散问题
4. seek/source 切换时旧时间线数据不会继续污染音频输出

如果后续还有问题，优先排查顺序应该是：

1. `AVPERF` 是否出现新的阶段性耗时异常
2. `AVSYNC diffUs` 是否是固定小偏移还是持续发散
3. 设备侧 `SurfaceTexture / dataspace / swapBuffers` 是否有平台特定性能问题
