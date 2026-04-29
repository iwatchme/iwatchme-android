# MontageTimeline 剪辑引擎完整分析

> 调研日期：2026-04-27
> 目标：理解 B 站蒙太奇 Timeline 编辑引擎的完整构建逻辑，为从零构建类似引擎提供参考

---

## 目录

- [第一部分：整体架构](#第一部分整体架构)
- [第二部分：核心数据模型](#第二部分核心数据模型)
- [第三部分：引擎协议层接口](#第三部分引擎协议层接口)
- [第四部分：Native 层技术栈](#第四部分native-层技术栈)
- [第五部分：OpenGL 基础概念](#第五部分opengl-基础概念)
- [第六部分：渲染树详解](#第六部分渲染树详解)
- [第七部分：Video Clip 完整渲染流程](#第七部分video-clip-完整渲染流程)
- [第八部分：Audio Clip 完整渲染流程](#第八部分audio-clip-完整渲染流程)
- [第九部分：预览与导出的端到端流程](#第九部分预览与导出的端到端流程)
- [第十部分：从零构建指南](#第十部分从零构建指南)

---

# 第一部分：整体架构

## 1.1 五层分层设计

```
┌─────────────────────────────────────────────────────────────────┐
│  Layer 5: 业务层 (Business)                                      │
│  biz/bililive/montage/ · biz/upper/bcut/ · common/editor/       │
│  直播封面编辑、必剪视频编辑、草稿管理等具体业务场景                     │
├─────────────────────────────────────────────────────────────────┤
│  Layer 4: 引擎适配层 (Editor Adapter)                             │
│  common/editorscope/editor-adapter/                              │
│  EditorScopeFactory · StudioEditor · StudioEditorManager         │
│  根据 EngineType(MEICAM/MONTAGE) 返回对应引擎实现                  │
├─────────────────────────────────────────────────────────────────┤
│  Layer 3: 引擎协议层 (Editor Engine Interface)                    │
│  common/editorscope/editor-engine/                               │
│  ITimeline · IVideoTrack · IAudioTrack · IVideoClip · IAudioClip │
│  IStreamingContext · ITimelineCaption · IVideoFx 等 40+ 接口       │
├─────────────────────────────────────────────────────────────────┤
│  Layer 2: 引擎实现层 (Engine Implementation)                      │
│  common/editorscope/editor-engine-studio/ (50+ ImplX 包装类)      │
│  StudioTimelineImplX → Kaleidoscope SDK → Montage SDK            │
│  使用 box/unbox 模式桥接接口与 Native 对象                          │
├─────────────────────────────────────────────────────────────────┤
│  Layer 1: Native 引擎层 (Native Engine)                           │
│  libmontage.so (~3.5MB arm64) + libbmm_jpeg.so + libbmm_mediacore.so │
│  montage.jar (JNI Bridge) → C++ 视频处理核心                       │
│  MontageStreamingContext · MontageTimeline · MontageLiveWindow    │
└─────────────────────────────────────────────────────────────────┘
```

## 1.2 引擎选择机制

```java
// KaleidoscopeFactory.java — 引擎工厂
// 支持 SDK_NVS (美摄/历史) 和 SDK_MON (蒙太奇/当前)
// 目前硬编码返回 Montage 实现
public static Kaleidoscope newKaleidoscope(String sdk) {
    return new Montage();
}
```

## 1.3 box/unbox 桥接模式

每个 `StudioXxxImplX` 类实现对应的 `IXxx` 接口，内部持有真实 SDK 对象：

```java
public final class StudioTimelineImplX implements ITimeline {
    private Timeline mTimeline;  // Kaleidoscope SDK 真实对象

    static ITimeline box(Timeline timeline) {  // SDK对象 → 接口对象
        return timeline == null ? null : new StudioTimelineImplX(timeline);
    }
    
    static Timeline unbox(ITimeline timeline) {  // 接口对象 → SDK对象
        return timeline == null ? null : (Timeline) timeline.getTimeline();
    }
    
    @Override
    public IVideoTrack appendVideoTrack() {
        VideoTrack nvt = mTimeline.appendVideoTrack();
        return nvt != null ? StudioVideoTrackImplX.box(nvt) : null;
    }
}
```

## 1.4 关键文件索引

| 文件 | 职责 |
|------|------|
| `framework/rpc/proto-core/.../timeline/time_line.proto` | 核心数据模型定义 |
| `common/editorscope/editor-engine/src/.../ITimeline.java` | 时间线接口 |
| `common/editorscope/editor-engine/src/.../IStreamingContext.java` | 引擎上下文接口 |
| `common/editorscope/editor-engine/src/.../IVideoTrack.java` | 视频轨道接口 |
| `common/editorscope/editor-engine/src/.../IVideoClip.java` | 视频片段接口 |
| `common/editorscope/editor-engine/src/.../ICustomVideoFx.java` | 自定义特效 GL 回调接口 |
| `common/editorscope/editor-engine-studio/src/.../StudioTimelineImplX.java` | Timeline 实现 |
| `common/editorscope/editor-adapter/` | 引擎选择适配器 |
| `common/editorscope/README.md` | 架构说明 |
| `thirdparty/montagesdk/montage.jar` | Montage JNI 桥接 |
| `thirdparty/montagesdk/changelog.md` | C++ 引擎演进历史 |
| `application/studio/common-studio/kaleidoscope/kaleidoscope-sdk/src/.../montage/` | 47 个 JNI 包装类 |
| `application/studio/common-studio/kaleidoscope/kaleidoscope-lib-montage/montage.aar` | Native .so 文件 |
| `biz/bililive/livestream/biz_common/src/.../montage/` | 直播封面编辑实现 |
| `common/editor/src/.../pb/util/PBAdapterVideoUtils.kt` | Protobuf 序列化适配 |
| `application/studio/mainvideoeditor/src/main/res/raw/*.glsl` | GLSL Shader 文件 |

---

# 第二部分：核心数据模型

**关键文件**: `framework/rpc/proto-core/src/main/proto/studio/bapis/protos/timeline/time_line.proto`

## 2.1 顶层结构

```
Draft (草稿)
 ├── draftId, draftName, duration, coverUrl
 └── TimeLine (时间线)
      ├── config: TimeLineConfig (分辨率/帧率/码率)
      ├── engineType: MEICAM(1) | MONTAGE(2)
      ├── videoTracks[]: VideoTrack        ← 视频轨道（主轨+画中画+虚拟形象）
      ├── audioTracks[]: AudioTrack        ← 音频轨道（原声/BGM/音效/录音/TTS）
      ├── captionTracks[]: CaptionTrack    ← 字幕轨道
      ├── stickerTracks[]: StickerTrack    ← 贴纸轨道
      ├── compoundCaptionTracks[]          ← 组合字幕轨道
      └── timelineVideoFxTracks[]          ← 全局视频特效轨道
```

## 2.2 视频轨道与片段

```protobuf
VideoTrack {
    type: MAIN | PIP | IDOL
    volume: {leftVolume, rightVolume}
    clips[]: VideoClip
    transitions[]: VideoTransition
}

VideoClip {
    // 时间定位 (微秒)
    inPoint, outPoint          // Timeline 上的位置
    trimIn, trimOut            // 源文件裁剪点
    
    // 媒体源
    sourcePath, mediaType(Video/Image), mediaFrom(Asset/File)
    
    // 视觉属性
    opacity, blendingMode, extraVideoRotation
    
    // 变速
    speed (线性), curveType (7种: None/Custom/Highlight/Bullet/Montage/Jump/FlashIn/FlashOut)
    curveString, keepAudioPitch
    
    // 特效链
    fxs[]: VideoClipFx
    
    // 动画
    inAnim, outAnim, compoundAnim
    
    // 色度抠图
    pixelImageMattingFilter
}

VideoClipFx {
    builtinFxName: Transform2D | Storyboard | MaskGenerator | Cartoon | Lut
    businessType: Background | Trans2D | Cut2D | Mask | KeyFrame | Filter | ...
    
    // Transform2D 参数
    transX, transY, rotation, opacity, scaleX, scaleY, fillMode
    
    // 蒙版参数
    regionInfo (Line/Mirror/Rectangle/Cycle), inverseRegion, featherWidth
    
    // 滤镜/调节参数
    intensity, brightness, saturation, contrast, sharpness
    temperature, highlights, shadows, vignette, fade
    
    // 关键帧
    keyFrameInfos[]: VideoClipFx  (递归结构)
    controlPoints: 贝塞尔曲线 (backwardControlPoint, forwardControlPoint)
}

VideoTransition {
    transitionType: BUILTIN | PACKAGE | CUSTOM
    transitionDur, transitionName
    materialId, packagePath
}
```

## 2.3 音频轨道

```protobuf
AudioTrack {
    trackType: Original|BGM|Effect|Record|Voice|PIP|TTS|Avatar
    audioClips[]: AudioClip
}

AudioClip {
    inPoint, outPoint, trimIn, trimOut
    volume, fadeIn, fadeOut, speed, keepAudioPitch
    curveType, curveString        // 曲线变速
    fxs[]: AudioClipFx           // 变声/降噪
    ttsText, ttsCaptionId, ttsVoiceId  // TTS
}
```

## 2.4 关键帧与贝塞尔曲线

```protobuf
KeyFrame {
    timePosition              // 时间点
    transX, transY, rotation, opacity, scale
    controlPointForScaleX/Y, controlPointForTransX/Y, controlPointForRotation
}

ControlPoint {
    backwardControlPoint: Point
    forwardControlPoint: Point
    backwardType/forwardType: NORMAL|CUSTOM|EASEIN|EASEOUT|EASEINOUT|...
}
```

## 2.5 序列化与持久化

```
保存: 运行时对象 → Protobuf (Draft/TimeLine) → 本地文件 / RPC 同步
恢复: Protobuf → PBAdapterVideoUtils.adapterBClipTime() → 重建运行时对象

关键文件:
  common/editor/src/.../pb/util/PBAdapterVideoUtils.kt
```

---

# 第三部分：引擎协议层接口

## 3.1 IStreamingContext — 引擎上下文

```java
// 100+ 配置常量，核心能力:
init(context, flags)                    // 支持 4K/8K/16K/HDR/异步初始化
createTimeline(videoRes, fps, audioRes) // 创建时间线
connectTimelineWithLiveWindow(tl, lw)  // 绑定预览窗口
playbackTimeline / seekTimeline / stop // 播放控制
grabImageFromTimeline(tl, pos, scale)  // 截帧 → Bitmap
compileTimeline(tl, start, end, path, res, bitrate, flags)  // 导出编译
clearCachedResources(flags)            // 分 5 类缓存
```

## 3.2 ITimeline — 时间线管理

```java
appendVideoTrack() / insertVideoTrack(index) / removeVideoTrack(index)
appendAudioTrack() / removeAudioTrack(index)
addCaption(text, inPoint, duration, stylePackageId)
addModularCaption(text, inPoint, duration)
addAnimatedSticker(inPoint, duration, packageId, intercept)
addBuiltinTimelineVideoFx(inPoint, duration, fxName)
getCaptionsByTimelinePosition(position)
getDuration() / getVideoRes()
```

## 3.3 IVideoTrack / IVideoClip

```java
// Track 操作
appendClip(filePath) / insertClip(filePath, index)
removeClip(index, affectSibling) / moveClip(from, to)
splitClip(index, splitPoint)
setBuiltinTransition(srcClipIndex, name, isOverlapped)

// Clip 操作
changeTrimInPoint(point, affectSibling) / changeTrimOutPoint(point, affectSibling)
changeSpeed(speed, keepAudioPitch) / changeCurvesVariableSpeed(curveString, keepPitch)
appendBuiltinFx(fxName) / appendPackagedFx(packageId) / insertCustomFx(renderer, index)
setBlendingMode(mode) / setVolumeGain(left, right)
changeFilePath(newPath)   // 替换素材
```

## 3.4 ICustomVideoFx.IRenderer — 自定义特效回调

```java
interface IRenderer {
    void onInit();                    // GL 上下文创建时，初始化 Shader
    void onPreloadResources();        // 预加载资源
    void onRender(IRenderContext ctx); // 每帧渲染回调
    void onCleanup();                 // 释放 GL 资源
}

interface IRenderContext {
    IVideoFrame getInputVideoFrame();  // 输入帧 (texId + size)
    IVideoFrame getOutputVideoFrame(); // 输出帧 (texId + size)
    long getMediaStreamTime();         // 当前时间戳
}

interface IVideoFrame {
    int getTexId();      // OpenGL 纹理 ID
    int getWidth();
    int getHeight();
    boolean getIsUpsideDownTexture();
}
```

**含义**: C++ 引擎在渲染管线中为自定义特效创建 FBO，把输入/输出纹理 ID 通过 JNI 回调传给 Java，Java 层可以用自己的 GLSL Shader 处理。

---

# 第四部分：Native 层技术栈

## 4.1 核心结论

**Montage（蒙太奇）是 B 站自研的闭源 C++ 视频编辑引擎**，仓库中无 C++ 源码，全部编译为 .so，通过二进制分发。

## 4.2 Native 库清单

| 库文件 | 大小(arm64) | 职责 |
|--------|-------------|------|
| `libmontage.so` | ~3.5MB | 核心编辑引擎：时间线、特效、转场、导出 |
| `libbmm_jpeg.so` | ~381KB | JPEG 编解码（缩略图/封面） |
| `libbmm_mediacore.so` | ~1.16MB | 媒体核心：编解码器、原始流处理 |

## 4.3 技术栈（从 changelog 逆推）

| 技术 | 用途 | 证据 |
|------|------|------|
| **FFmpeg 7.1** | 解封装 + 软解码 + 封装 | `适配ffmpeg7.1` (2025.4) |
| **MediaCodec (NDK)** | 硬件编解码 | `mediacodec编码使用callback异步模式` (2024.12) |
| **OpenGL ES + EGL** | GPU 渲染管线 | `增加egl sync` / `纹理池泄漏` / `drawToTexture` |
| **Aurora** | B站自研特效渲染引擎 | `适配aurora升级` (频繁出现) |
| **NEON** | ARM SIMD 加速 | `渲染支持neon` (2025.3) |
| **CV (OpenCV系)** | 智能抠像/人脸 | `cv升级` / `智能抠像` |

## 4.4 JNI 桥接层

47 个包装类，8554 行代码，位于：
`application/studio/common-studio/kaleidoscope/kaleidoscope-sdk/src/.../montage/`

核心类：MonStreamingContextImpl (1330行)、MonTimelineCaptionImpl (627行)、MonAssetPackageManagerImpl (448行)、MonVideoClipImpl (417行)

---

# 第五部分：OpenGL 基础概念

> 假设读者有 C++ 基础，对 OpenGL 完全陌生

## 5.1 CPU vs GPU

```
CPU: 擅长复杂逻辑，一次处理一件事，很快
     for (int i = 0; i < 1920*1080; i++) pixels[i] = transform(pixels[i]);
     ↑ 逐像素处理，200万次循环

GPU: 擅长简单重复计算，一次处理几千件事
     // 2073600 个核心同时执行:
     pixel = transform(myPixel);
     ↑ 每个核心只处理自己的像素，全部同时完成
```

一帧 1080p = 1920 x 1080 = 207 万像素。视频编辑的每帧滤镜/混合/变换，天然适合 GPU 并行处理。

## 5.2 OpenGL ES

**OpenGL ES** = 一套 C 语言 API，让程序告诉 GPU 做什么。类比遥控器和电视。

Android 上用 OpenGL ES 2.0/3.0（移动端精简版）。

## 5.3 纹理（Texture）— GPU 显存里的一张图片

```
CPU 内存中的图片:
  uint8_t* pixels = new uint8_t[1920 * 1080 * 4];  // RGBA，每像素4字节
  // 住在 CPU 堆内存，GPU 看不到

GPU 显存中的纹理:
  GLuint texId;                           // 纹理 ID，就是一个整数
  glGenTextures(1, &texId);               // 向 GPU 申请编号
  glBindTexture(GL_TEXTURE_2D, texId);    // "接下来操作这个纹理"
  glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA,
               1920, 1080, 0,
               GL_RGBA, GL_UNSIGNED_BYTE,
               pixelData);               // CPU 内存 → GPU 显存
  // 现在 texId 代表 GPU 里的这张图
```

**纹理 = GPU 显存中的一块像素数据 + 一个整数编号（texId）**

类比 C++ 文件描述符：`int fd = open("file")` 拿到一个数字就能操作文件；`GLuint texId = glGenTextures()` 拿到一个数字就能操作 GPU 里的图片。

### glTexImage2D 详解

```
CPU 内存 (RAM)                    GPU 显存 (VRAM)
─────────────                     ────────────────
pixelData ■■■■■■    ──拷贝──→    texture ■■■■■■
(堆内存)               ↑          (显存)
                    这就是 glTexImage2D 做的事
                    把像素数据跨总线传到 GPU
```

```cpp
glTexImage2D(
    GL_TEXTURE_2D,      // 目标：2D 纹理
    0,                   // mipmap 级别：0 = 原始尺寸
    GL_RGBA,             // GPU 端存储格式
    1920, 1080,          // 宽高
    0,                   // 边框（必须 0）
    GL_RGBA,             // CPU 端数据格式
    GL_UNSIGNED_BYTE,    // 每通道数据类型 (uint8_t, 0~255)
    pixelData            // 指向 CPU 内存的指针，传 nullptr 则只分配不填数据
);

// 调用后：
//   1. 驱动从 pixelData 读取 1920×1080×4 = 8,294,400 字节
//   2. 通过 GPU 总线拷贝到 GPU 显存
//   3. 与 glBindTexture 绑定的纹理 ID 关联
//   4. pixelData 可以 delete 了，GPU 有自己的拷贝
```

传 `nullptr` 的场景——创建 FBO 的空白画布：
```cpp
glTexImage2D(..., nullptr);  // 只在 GPU 分配空间，不填数据
// 之后 GPU 自己往里面渲染，不需要 CPU 传数据
```

### 纹理生命周期

```
1. 创建: glGenTextures(1, &texId)         → GPU 分配编号
2. 上传: glTexImage2D(..., pixelData)     → CPU → GPU（或 nullptr 只分配）
3. 使用: glBindTexture + Shader 采样       → GPU 内部读取
4. 销毁: glDeleteTextures(1, &texId)      → 释放 GPU 显存
```

### 纹理采样（在 Shader 中读取像素）

```glsl
uniform sampler2D uTexture;       // "我要读的纹理"
varying vec2 vTexCoord;           // "读哪个位置" (0,0)~(1,1)

void main() {
    vec4 color = texture2D(uTexture, vTexCoord);
    // color.r/g/b/a 各 0.0~1.0
    gl_FragColor = color;
}
```

## 5.4 Shader（着色器）— GPU 上的小程序

**Shader = 一段在 GPU 上运行的小程序，每个像素独立执行一次。**

你只写"一个像素怎么处理"，GPU 自动对 200 万像素并行执行。

### 两种 Shader

```
Vertex Shader（顶点着色器）：决定"图形画在屏幕什么位置"
    输入: 矩形四个角的坐标
    输出: 屏幕位置
         ↓ GPU 自动填满中间的像素
Fragment Shader（片段着色器）：决定"每个像素什么颜色"
    输入: 当前像素的纹理坐标
    输出: RGBA 颜色
```

### 完整示例：把一张图片变暗

```cpp
const char* vertexShader = R"(
    attribute vec2 aPosition;
    attribute vec2 aTexCoord;
    varying vec2 vTexCoord;
    void main() {
        gl_Position = vec4(aPosition, 0.0, 1.0);
        vTexCoord = aTexCoord;
    }
)";

const char* fragmentShader = R"(
    precision mediump float;
    uniform sampler2D uTexture;
    uniform float uBrightness;      // 从 C++ 传入的参数
    varying vec2 vTexCoord;
    void main() {
        vec4 color = texture2D(uTexture, vTexCoord);
        color.rgb *= uBrightness;    // 调暗
        gl_FragColor = color;
    }
)";

GLuint program = compileShaderProgram(vertexShader, fragmentShader);
glUseProgram(program);
glBindTexture(GL_TEXTURE_2D, videoFrameTexId);
glUniform1f(glGetUniformLocation(program, "uBrightness"), 0.5);
glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);  // GPU 对每个像素执行 Shader
```

### Uniform — C++ 往 Shader 传参

```
C++:    glUniform1f(loc, 0.5)               → float
        glUniform3f(loc, r, g, b)            → 颜色
        glUniformMatrix3fv(loc, 1, false, m) → 矩阵
Shader: uniform float uBrightness;          → 接收
```

## 5.5 FBO（Frame Buffer Object）— 渲染到纹理而不是屏幕

### 问题

默认 `glDrawArrays()` 结果画到屏幕。但视频编辑需要对一帧连续做 N 步处理（亮度 → 滤镜 → 蒙版 → ...），每步需要上一步的结果。

### FBO 的本质

**FBO 让 GPU 渲染到一个纹理（而不是屏幕）。**

```
类比 C++ 的 IO 重定向:
  默认:  printf("hello")         → 输出到终端（屏幕）
  FBO:   fprintf(file, "hello")  → 输出到文件（纹理），之后可以再读回来
```

FBO 是 OpenGL ES 2.0+ 的标准功能。

### 创建和使用

```cpp
// ===== 创建 FBO =====

// 1. 创建空白纹理作为"画布"
GLuint outputTex;
glGenTextures(1, &outputTex);
glBindTexture(GL_TEXTURE_2D, outputTex);
glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 1920, 1080, 0,
             GL_RGBA, GL_UNSIGNED_BYTE, nullptr);  // nullptr = 只分配空间
// outputTex 现在是一张空白的 1920x1080 纹理

// 2. 创建 FBO，把纹理绑上去
GLuint fbo;
glGenFramebuffers(1, &fbo);
glBindFramebuffer(GL_FRAMEBUFFER, fbo);
glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                       GL_TEXTURE_2D, outputTex, 0);
// 意思是：接下来渲染结果都写到 outputTex 里

// ===== 使用 FBO =====

// 渲染到 FBO
glBindFramebuffer(GL_FRAMEBUFFER, fbo);       // 切换到 FBO
glUseProgram(brightnessShader);
glBindTexture(GL_TEXTURE_2D, videoFrameTex);  // 输入
glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
// 结果存在 outputTex 里了（不在屏幕上）

// 渲染回屏幕
glBindFramebuffer(GL_FRAMEBUFFER, 0);         // 0 = 默认帧缓冲（屏幕）
glUseProgram(displayShader);
glBindTexture(GL_TEXTURE_2D, outputTex);      // 输入：上一步结果
glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
// 显示到屏幕
```

### FBO Ping-Pong

不能同时从一个纹理读又往它写（未定义行为）。所以用两个 FBO 交替：

```
想象两块画板 A 和 B:
  第1步: 看原始照片，画到画板 A（亮度调节）
  第2步: 看画板 A，画到画板 B（加滤镜）
  第3步: 看画板 B，画到画板 A（加蒙版）→ 覆盖第1步内容，没关系
  第4步: 看画板 A，画到画板 B（最终结果）
  永远从一块读、往另一块写。
```

```cpp
class FBOPair {
    GLuint fbo[2], tex[2];
    int current = 0;
    
    void init(int w, int h) {
        for (int i = 0; i < 2; i++) {
            glGenTextures(1, &tex[i]);
            glBindTexture(GL_TEXTURE_2D, tex[i]);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0,
                         GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
            glGenFramebuffers(1, &fbo[i]);
            glBindFramebuffer(GL_FRAMEBUFFER, fbo[i]);
            glFramebufferTexture2D(..., tex[i], 0);
        }
    }
    
    GLuint getWriteFBO()     { return fbo[current]; }
    GLuint getWriteTexture() { return tex[current]; }
    GLuint getReadTexture()  { return tex[1 - current]; }
    void swap()              { current = 1 - current; }
};
```

## 5.6 EGL — 连接 OpenGL 和 Android 窗口系统

OpenGL 只管渲染，不管显示。EGL 是中间人：

```cpp
EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);  // 物理屏幕
EGLContext context = eglCreateContext(display, ...);       // GL 工作空间
EGLSurface surface = eglCreateWindowSurface(display, ..., nativeWindow, ...);
// surface 可以是: 屏幕 SurfaceView / 编码器 InputSurface / 离屏 PBuffer

eglMakeCurrent(display, surface, surface, context);
// "在这个 context 里渲染的东西，输出到这个 surface"
```

三种输出场景：
- **预览**: EGLSurface → SurfaceView（屏幕）
- **导出**: EGLSurface → MediaCodec.createInputSurface()（编码器）← 零拷贝关键
- **截帧**: EGLSurface → PBuffer → glReadPixels → Bitmap

## 5.7 MediaCodec — 硬件编解码器

```
视频文件 ≠ 图片序列。3 分钟 1080p 未压缩约 44GB，H.264 压缩后约 50MB。
解码 = 压缩数据 → 图片帧。编码 = 图片帧 → 压缩数据。
MediaCodec = Android 硬件芯片做这件事，比 CPU 快几十倍且不发热。
```

### 零拷贝路径（性能关键）

```
H.264 数据 → MediaCodec 硬解 → 解码结果留在 GPU 显存
                                      ↓
                               SurfaceTexture.updateTexImage()
                                      ↓
                               GL_TEXTURE_EXTERNAL_OES (纹理)
                                      ↓
                               Shader 直接采样

全程像素数据不经过 CPU 内存！
```

普通路径：MediaCodec → CPU 内存 → glTexImage2D → GPU 显存（两次拷贝）
零拷贝路径：MediaCodec → GPU 显存（零次 CPU 拷贝）

### FFmpeg vs MediaCodec

```
MediaCodec: 速度快、省电、零拷贝。但格式有限、各厂商有 bug
FFmpeg:     支持几乎所有格式、行为一致。但用 CPU，慢且耗电
策略:       优先 MediaCodec → 失败回落 FFmpeg
```

---

# 第六部分：渲染树详解

## 6.1 渲染树是什么

**渲染树不是一个库或框架，是一种将 Timeline 数据结构转化为 GPU 执行指令的中间表示。**

Timeline 是声明式的（描述用户想要什么），渲染树是命令式的（描述 GPU 该怎么做）。

类比：DOM 树 → 浏览器渲染树 → 像素。Timeline → 渲染树 → 像素。

**RenderNode 不是 OpenGL 自带概念，是引擎自己用 C++ 设计的数据结构。**

## 6.2 节点类型

```
RenderNode (基类)
 ├── SourceNode        — 产出纹理（解码帧/图片/纯色）
 ├── FxNode            — 消费一个纹理 → 产出一个纹理（特效处理）
 ├── BlendNode         — 消费两个纹理 → 产出一个纹理（Alpha 混合）
 ├── TransitionNode    — 消费两个纹理 + 进度 → 产出一个纹理（转场）
 ├── OverlayNode       — 将字幕/贴纸绘制到画面上
 └── OutputNode        — 最终输出到屏幕或编码器
```

## 6.3 RenderNode 的 C++ 实现

```cpp
// ===== 基类 =====
class RenderNode {
public:
    virtual ~RenderNode() = default;
    virtual GLuint execute(int64_t timelinePos) = 0;  // 返回输出纹理 ID
    std::vector<RenderNode*> inputs;
    int outputWidth, outputHeight;
};

// ===== 源节点 =====
class SourceNode : public RenderNode {
    ClipDecoder* decoder;
    GLuint execute(int64_t pos) override {
        return decoder->decodeFrame(pos);  // 解码一帧，返回纹理 ID
    }
};

// ===== 特效节点 =====
class FxNode : public RenderNode {
    GLuint shaderProgram;
    FxParams params;
    FBOPair* fboPair;
    
    GLuint execute(int64_t pos) override {
        GLuint inputTex = inputs[0]->execute(pos);      // 递归获取输入
        if (hasKeyFrames)
            params = interpolateKeyFrames(keyFrames, pos); // 关键帧插值
        
        glBindFramebuffer(GL_FRAMEBUFFER, fboPair->getWriteFBO());
        glUseProgram(shaderProgram);
        glBindTexture(GL_TEXTURE_2D, inputTex);
        setUniforms(params);
        drawFullscreenQuad();
        
        GLuint outputTex = fboPair->getWriteTexture();
        fboPair->swap();
        return outputTex;
    }
};

// ===== 混合节点 =====
class BlendNode : public RenderNode {
    float opacity;
    int blendingMode;
    
    GLuint execute(int64_t pos) override {
        GLuint bottomTex = inputs[0]->execute(pos);
        GLuint topTex = inputs[1]->execute(pos);
        
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        drawFullscreenQuad(bottomTex);                  // 画底图
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        drawQuadWithOpacity(topTex, opacity);            // 画上层
        glDisable(GL_BLEND);
        return outputTex;
    }
};

// ===== 转场节点 =====
class TransitionNode : public RenderNode {
    GLuint transitionShader;
    float progress;  // 0.0 ~ 1.0
    
    GLuint execute(int64_t pos) override {
        GLuint texA = inputs[0]->execute(pos);
        GLuint texB = inputs[1]->execute(pos);
        
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glUseProgram(transitionShader);
        bindTexture(0, texA);
        bindTexture(1, texB);
        glUniform1f(uProgress, progress);  // 如交叉溶解: mix(A, B, progress)
        drawFullscreenQuad();
        return outputTex;
    }
};

// ===== 输出节点 =====
class OutputNode : public RenderNode {
    GLuint execute(int64_t pos) override {
        GLuint finalTex = inputs[0]->execute(pos);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);  // 0 = 屏幕/编码器
        drawFullscreenQuad(finalTex);
        return 0;
    }
};
```

## 6.4 渲染树的执行是递归的

```
output.execute(pos)
  └→ OverlayNode(字幕).execute(pos)
       ├→ BlendNode(多轨合成).execute(pos)
       │    ├→ TransitionNode(转场).execute(pos)
       │    │    ├→ FxNode(ClipA滤镜).execute(pos)
       │    │    │    └→ SourceNode(ClipA解码).execute(pos)
       │    │    │         └→ return texA ←── 叶节点：解码帧纹理
       │    │    │    ←── 滤镜处理后返回纹理
       │    │    └→ FxNode(ClipB滤镜).execute(pos)
       │    │         └→ SourceNode(ClipB解码) → return texB
       │    │    ←── 转场混合两纹理返回
       │    └→ FxNode(画中画ClipC).execute(pos) → ...
       │    ←── Alpha 混合返回
       └→ ←── 叠加字幕纹理返回
  ←── 画到屏幕，完成
```

## 6.5 渲染树的构建算法

```
buildRenderTree(Timeline* timeline, int64_t position):

    RenderNode* current = null

    // Step 1: 逐轨道构建（从底轨到顶轨）
    for each videoTrack in timeline->videoTracks:
        activeClips = findActiveClips(videoTrack, position)
        if activeClips.empty(): continue
        
        if activeClips.size() == 1:
            trackResult = buildClipSubtree(activeClips[0], position)
        if activeClips.size() == 2:  // 转场区间
            tree1 = buildClipSubtree(activeClips[0], position)
            tree2 = buildClipSubtree(activeClips[1], position)
            progress = calcTransitionProgress(transition, position)
            trackResult = new TransitionNode(tree1, tree2, progress)
        
        if current == null:
            current = trackResult
        else:
            current = new BlendNode(current, trackResult, blendingMode, opacity)
    
    // Step 2: 字幕层
    for each caption (if inPoint <= position < outPoint):
        current = new OverlayNode(current, buildCaptionNode(caption))
    
    // Step 3: 贴纸层
    for each sticker (if active):
        current = new OverlayNode(current, buildStickerNode(sticker))
    
    // Step 4: 全局特效
    for each globalFx (if active):
        current = new FxNode(current, globalFx)
    
    return new OutputNode(current)


buildClipSubtree(VideoClip* clip, int64_t position):
    // 计算源文件解码位置
    localTime = position - clip->inPoint
    if 线性变速: sourceTime = trimIn + localTime * speed
    if 曲线变速: sourceTime = trimIn + ∫speedCurve(t)dt
    
    RenderNode* current = new SourceNode(filePath, sourceTime)
    for each fx in clip->fxChain:
        current = new FxNode(current, fx, position)
    return current
```

---

# 第七部分：Video Clip 完整渲染流程

## 7.1 时间位置计算

```
localTime = position - clip.inPoint    // clip 内相对时间

线性变速: sourceTime = trimIn + localTime × speed

曲线变速: sourceTime = trimIn + ∫₀ᵗ speedCurve(τ) dτ
  数值积分:
  float total = 0, dt = 1000;  // 1ms 步长
  for (t = 0; t < localTime; t += dt):
      total += evaluateCurve(curveString, t / clipDuration) * dt;
  sourceTime = trimIn + total;
```

## 7.2 解码

```
MediaCodec 硬解 (优先):
  AMediaCodec → 输出到 Surface → SurfaceTexture.updateTexImage()
  → GL_TEXTURE_EXTERNAL_OES (零拷贝纹理)

FFmpeg 软解 (兜底):
  avcodec_receive_frame → AVFrame (YUV420P)
  → sws_scale (YUV→RGBA) → glTexImage2D → GL_TEXTURE_2D
```

## 7.3 特效链（FBO Ping-Pong）

```
解码帧纹理 → [Shader: Transform2D] → FBO-A 纹理
FBO-A 纹理 → [Shader: 蒙版]       → FBO-B 纹理
FBO-B 纹理 → [Shader: LUT滤镜]    → FBO-A 纹理
FBO-A 纹理 → [Shader: 色彩调节]   → FBO-B 纹理 → 最终结果
```

## 7.4 关键帧插值（贝塞尔曲线）

```cpp
// 三次贝塞尔: B(t) = (1-t)³P0 + 3(1-t)²tP1 + 3(1-t)t²P2 + t³P3
float bezierInterpolate(float t, ControlPoint& cp) {
    float u = 1.0f - t;
    return u*u*u * cp.p0 + 3*u*u*t * cp.forward
         + 3*u*t*t * cp.backward + t*t*t * cp.p1;
}

// 找到当前时间两侧的关键帧，按 t 插值所有属性
VideoFxParams interpolateKeyFrames(vector<KeyFrame>& frames, int64_t time) {
    auto [prev, next] = findBoundingFrames(frames, time);
    float t = (float)(time - prev.time) / (next.time - prev.time);
    return {
        .transX = bezierInterpolate(t, prev.controlPointForTransX),
        .transY = bezierInterpolate(t, prev.controlPointForTransY),
        .rotation = bezierInterpolate(t, prev.controlPointForRotation),
        .scaleX = bezierInterpolate(t, prev.controlPointForScaleX),
        .scaleY = bezierInterpolate(t, prev.controlPointForScaleY),
    };
}
```

---

# 第八部分：Audio Clip 完整渲染流程

音频和视频是**并行的两条独立管线**（独立线程）。

## 8.1 流程

```
Step 1: 确定活跃的音频 Clip
  遍历所有 AudioTrack，找到当前 position 覆盖的 AudioClip

Step 2: 逐 Clip 解码 PCM
  FFmpeg: av_read_frame → avcodec_receive_frame → PCM float32

Step 3: 逐 Clip 应用效果
  音量:   sample *= clip.volume
  淡入:   if (localTime < fadeInDur) sample *= localTime / fadeInDur
  淡出:   if (localTime > dur - fadeOutDur) sample *= (dur - localTime) / fadeOutDur
  变速保调: timeStretch(pcm, speed)  // Sonic/RubberBand 算法
  特效:   变声 pitchShift / 降噪 denoise

Step 4: 混音（多轨合并）
  for (i : bufferSize): mixed[i] = Σ track[i].pcm[i]
  限幅: clamp(sample, -1.0, 1.0)

Step 5: 输出
  预览: AudioTrack.write(mixedPCM)  // Android AudioTrack 播放
  导出: PCM → AAC 编码 → MediaMuxer
```

---

# 第九部分：预览与导出的端到端流程

## 9.1 预览播放

```
GL 线程:
  while (isPlaying && pos < duration):
      renderTree = buildRenderTree(timeline, pos)
      renderTree.execute(pos)
      eglSwapBuffers(display, windowSurface)  // 显示到屏幕
      
      // 帧率控制
      elapsed = now - frameStart
      if (elapsed < frameDur): sleep(frameDur - elapsed)
      else if (DROP_FRAME): pos += frameDur  // 跳帧
      pos += frameDur

音频线程 (并行):
  AudioTrack.play()
  while (isPlaying):
      pcm = renderAudio(timeline, audioPos, bufferSize)
      AudioTrack.write(pcm)
      // 音视频同步：视频参考音频时钟
```

## 9.2 导出

```
// 初始化
videoEncoder = MediaCodec("video/avc" 或 "video/hevc")
encoderSurface = videoEncoder.createInputSurface()  // 零拷贝关键
muxer = MediaMuxer(outputPath, MP4)

// 逐帧循环
for (pos = 0; pos < duration; pos += 1/fps):
    // 视频
    eglMakeCurrent(display, encoderSurface, ..., context)
    buildRenderTree(timeline, pos).execute(pos)
    eglPresentationTimeANDROID(display, encoderSurface, pts)
    eglSwapBuffers(display, encoderSurface)  // 帧进入编码器
    drainEncoder → muxer.writeSampleData     // 编码后写入 MP4
    
    // 音频 (交错处理)
    pcm → audioEncoder → muxer.writeSampleData
    
    // 硬编失败 → 回落软编 (x264/x265)

// 收尾
videoEncoder.signalEndOfInputStream()
muxer.stop()
```

### 零拷贝导出路径

```
渲染树执行 (GL)
    ↓ glDrawArrays → 渲染到编码器 Surface（GPU 内部传输）
MediaCodec 硬编码 (H.264/H.265)
    ↓ dequeueOutputBuffer → NAL 单元
MediaMuxer → output.mp4

CPU 只做控制流，像素数据全程在 GPU/VPU 硬件内流转。
```

---

# 第十部分：从零构建指南

## 10.1 技术选型

| 模块 | 方案 |
|------|------|
| 解码 | MediaCodec (硬解) + FFmpeg (软解兜底) |
| 渲染 | OpenGL ES 3.0 |
| 特效 | GLSL Shader |
| 字幕 | Skia / FreeType + GL Texture |
| 编码 | MediaCodec (硬编) + x264/x265 (软编兜底) |
| 音频 | AudioTrack + Sonic (变速保调) |
| 序列化 | Protobuf |
| 架构 | C++ 核心 + JNI 桥接 + Kotlin 接口层 |

## 10.2 开发路线图

```
Phase 1: 基础播放
  MediaCodec 解码 → OpenGL 渲染窗口 → 单轨播放+Seek → 音视频同步

Phase 2: 时间线编辑
  多片段模型 → 裁剪 → 分割 → 排序/删除 → 多轨道

Phase 3: 特效系统
  Transform2D → 滤镜(LUT+参数) → 转场 → 蒙版 → 关键帧+贝塞尔插值

Phase 4: 字幕/贴纸
  字幕渲染 → 贴纸 → 触摸交互 → 素材包管理

Phase 5: 导出
  编码管线 → 多分辨率/码率 → 进度回调 → 断点续编

Phase 6: 优化
  硬件加速全链路 → 纹理缓存 → 缩略图/波形预生成 → 曲线变速
```

---

## 概念速查表

| 概念 | 是什么 | OpenGL 自带? | 引擎中的角色 |
|------|--------|:---:|------|
| 纹理 (Texture) | GPU 显存中的一张图片 | 是 | 视频帧、中间结果、字幕位图 |
| Shader | GPU 上运行的小程序 | 是 | 每种特效对应一个 Shader |
| FBO | 渲染到纹理而非屏幕 | 是 | 特效链核心，ping-pong 使用 |
| EGL | 连接 OpenGL 和窗口/编码器 | 是 | 控制输出到屏幕还是编码器 |
| MediaCodec | 硬件编解码器 | 否 | 视频帧解码/编码 |
| RenderNode | 渲染操作抽象节点 | **否** | 组织 GPU 操作为可执行的树 |
| 渲染树 | RenderNode 组成的树 | **否** | Timeline → GPU 操作序列 |
| Ping-Pong | 两个 FBO 交替使用 | **否** | 多步连续特效处理 |
| 零拷贝 | 数据不经过 CPU | **否** | 性能关键路径 |
