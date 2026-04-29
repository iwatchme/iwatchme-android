# Red Engine 渲染流程完全指南（小白版）

> 面向音视频/OpenGL/FFmpeg 零基础读者，从第一行代码到画面显示，按因果链逐步讲解。
> 每个专业概念在它**第一次被需要的地方**展开解释。

---

## 心智模型：两个世界

在开始之前，你需要建立一个基本认知——你的手机里有两个"世界"：

```
┌───────────────────────────┐     ┌───────────────────────────┐
│        CPU 世界            │     │         GPU 世界           │
│                           │     │                           │
│  · 你写的 C++/Kotlin 代码  │     │  · 成百上千个小核心并行工作 │
│  · 一次只做一件事（串行）   │     │  · 同时处理数百万个像素     │
│  · 擅长复杂逻辑判断        │     │  · 擅长大量简单重复计算     │
│  · 能直接读写内存          │     │  · 有自己独立的显存         │
└───────────────────────────┘     └───────────────────────────┘
              │                                 ↑
              │    你的代码无法直接碰 GPU 的东西    │
              │    只能通过 API "下指令"          │
              └─────────────────────────────────┘
```

你写的所有 `gl*` 开头的函数，都是在向 GPU 世界"下指令"。GPU 世界的东西（纹理、缓冲区等）你摸不到，只能通过**编号**（一个 `GLuint` 整数）来引用它们——就像你用快递单号追踪包裹，但你永远不会亲自去快递仓库搬货���

---

## 总览：一帧视频从文件到屏幕的因果链

```
MP4文件 → 拆包(Demuxer) → 解码(HwDecoder) → 传送(SurfaceTexture)
→ 格式转换(SourceNode) → 画到屏幕(OutputNode) → 翻页显示(swapBuffers)
```

这条链中的每一环都依赖上一环的**产物**。没有上一环，下一环就做不了。

下面我们按时间顺序，跟着代码走一遍。

---

## 第一幕：搭建画室（EGL 初始化）

### 为什么需要这一步？

GPU 是一块独立的硬件。你想让它帮你画画，必须先"连上它"——就像你想用打印机，得先装驱动、连 USB 线、放好纸。

EGL 就是 CPU 与 GPU 之间的**连接协议**。

### 什么是 EGL？

**全称**：Embedded-System Graphics Library（嵌入式系统图形库）

**作用**：在 OpenGL ES 能画画之前，负责处理所有"基础设施"问题：
- 找到 GPU 硬件设备
- 协商画布格式（颜色深度、是否透明等）
- 创建画画所需的上下文环境
- 管理画布（画到屏幕还是内存）

**比喻**：EGL = 搭建一间画室的全套工作（选房间、通电、装灯、立画架、备画布）。OpenGL ES 只负责"画画"本身，但画室得 EGL 来搭。

### 代码（RenderEngine.cpp:140-148）

```cpp
void RenderEngine::renderThreadFunc() {
    // 在渲染线程上绑定 JNI 环境（因为后面要调用 Java 层的 SurfaceTexture）
    JNIEnv* env = nullptr;
    jvm_->AttachCurrentThread(&env, nullptr);

    // ══════ 搭建画室 ══════
    if (!eglCore_.init()) {
        return;  // 画室搭不起来，整个引擎就无法工作
    }
    ...
}
```

### EglCore::init() 逐行解析（EglCore.cpp:8-44）

```cpp
bool EglCore::init() {

    // ① 找到 GPU 设备
    display_ = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    // 比喻："找到这栋楼里的画室在哪层"
    // EGL_DEFAULT_DISPLAY = 用手机默认的那块 GPU
    // display_ = GPU 设备的句柄（身份证号）

    // ② 初始化 EGL
    EGLint major, minor;
    eglInitialize(display_, &major, &minor);
    // 比喻："画室通电，检测到 EGL 版本 1.4"
    // 这一步之后，display_ 才算真正可用

    // ③ 选择画布配置（什么规格的画布）
    chooseConfig();
    // 内部的配置要求：
    //   EGL_RENDERABLE_TYPE = ES3.0  → "画布要支持 OpenGL ES 3.0 的画笔"
    //   EGL_SURFACE_TYPE = WINDOW|PBUFFER → "能画到窗口，也能画到内存"
    //   EGL_RED/GREEN/BLUE/ALPHA_SIZE = 8 → "每个颜色通道 8 位(0~255)"
    //
    // 比喻："我要一种支持 3200 万色、能挂在墙上也能放桌上的画布"
    // config_ = 满足条件的画布规格编号

    // ④ 创建 GL 上下文
    EGLint contextAttribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 3,  // 指定 OpenGL ES 3.0
        EGL_NONE
    };
    context_ = eglCreateContext(display_, config_, EGL_NO_CONTEXT, contextAttribs);
    // 比喻："准备好一套 ES3.0 规格的画笔 + 调色盘 + 各种绘画工具"
    //
    // context_ 是什么？
    // · 它是 GPU 的"状态集合"——当前用什么颜色画、绑定了哪张纹理、
    //   用了哪个 shader，这些信息全都记录在 context 里。
    // · 一个线程同一时刻只能绑定一个 context。
    // · 所有 gl* 调用都隐式操作当前线程绑定的 context。

    // ⑤ 获取一个可选的扩展功能（用于视频导出时给帧打时间戳）
    eglPresentationTimeANDROID_ = ...;
    // 目前用不到，Phase 2 导出视频时会需要
}
```

### 这一步结束后的状态

```
已有：
  · display_ → GPU 设备连接
  · config_  → 画布规格（RGBA8888）
  · context_ → 画笔工具套装

还没有：
  · 画布（eglSurface_）→ 不知道画到哪里
  · 解码管道 → 没有视频数据来源

结论：画室搭好了，但画架上没有画布，也没有素材。
      进入 while 循环等待。
```

---

## 第二幕：等待画布就绪（Surface 设定）

### 为什么需要等待？

Android 的 SurfaceView 不是你 `new` 出来就能用的。系统需要时间来分配底层的图形缓冲区。
只有当 `surfaceCreated` 回调触发后，这块 Surface 才真正可用。

### 触发时机

```
用户看到界面 → Android 系统创建 SurfaceView → surfaceCreated 回调
→ Kotlin 层调用 setSurface(surface) → C++ 层 setSurface(ANativeWindow*)
→ windowChanged_ = true → 渲染线程检测到变更
```

### 代码（RenderEngine.cpp:165-186）

```cpp
// 渲染线程的 while 循环中
if (windowChanged_.load()) {
    windowChanged_.store(false);

    // 如果之前有旧画布，先销毁
    if (eglSurface_ != EGL_NO_SURFACE) {
        eglCore_.makeNothingCurrent();        // "放下画笔"
        eglCore_.destroySurface(eglSurface_); // "撤掉旧画布"
        eglSurface_ = EGL_NO_SURFACE;
    }

    std::lock_guard<std::mutex> lock(windowMutex_);
    if (window_) {
        // ★ 关键：创建 EGL Surface（画布）★
        eglSurface_ = eglCore_.createWindowSurface(window_);

        if (eglSurface_ != EGL_NO_SURFACE) {
            // ★ 关键：把画布挂上画架，拿起画笔 ★
            eglCore_.makeCurrent(eglSurface_);

            // 获取画布尺寸（后面 OutputNode 画画时需要）
            surfaceWidth = ANativeWindow_getWidth(window_);
            surfaceHeight = ANativeWindow_getHeight(window_);
        }
    }
}
```

### 概念解释：EGLSurface（画布）

**它是什么**：GPU 绘制结果的目的地。你所有的 GL 绘制指令，最终的像素都写到这个 Surface 上。

**为什么需要它**：
- context_（画笔）准备好了，但画笔悬在空中——没有画布接住颜料
- 必须先有 Surface，才能 `makeCurrent`，才能发 GL 指令

**种类**：
| 类型 | 用途 | 比喻 |
|------|------|------|
| Window Surface | 画到屏幕上（绑定 ANativeWindow） | 画布钉在展览墙上 |
| Pbuffer Surface | 画到内存里（离屏渲染） | 画布放在桌上（别人看不见） |
| 编码器 Surface | 画到编码器输入（导出视频）| 画布送到印刷厂 |

Red Engine 当前用的是 Window Surface → 画面直接上屏幕。

### 概念解释：makeCurrent（绑定）

```cpp
eglMakeCurrent(display_, eglSurface_, eglSurface_, context_);
```

**它做了什么**：把 context（画笔）和 surface（画布）绑定到当前线程。

**比喻**：走进画室，拿起画笔，对准画布——"从现在起，我在这个线程上发出的所有 GL 指令，结果都画到这块画布���。"

**为什么是线程级的**：一个画笔同一时刻只能被一只手（一个线程）拿着。所以所有 GL 操作必须在同一个渲染线程上。

### 这一步结束后的状态

```
已有：
  · display_, config_, context_ → 画室基础设施 ✓
  · eglSurface_ → 画布挂好了 ✓ (Window Surface, 绑定到屏幕)
  · makeCurrent 已调用 → GL 指令可以正常执行 ✓
  · surfaceWidth/Height → 知道画布多大 ✓

还没有：
  · 视频数据来源（解码管道）

结论：画室准备完毕，万事俱备，只欠"素材"（视频��据）。
```

---

## 第三幕：建立解码管道（initDecodePipeline）

### 为什么必须在 Surface 就绪之后才能做？

因为管道内部要调用 GL 指令（创建纹理、编译 Shader 等），而 GL 指令只有在 `makeCurrent` 之后才有效。依赖链：

```
Surface 就绪 → makeCurrent → GL 可用 → initDecodePipeline 中的 GL 调用
```

代码里的保护：

```cpp
if (videoSourceChanged_.load()) {
    if (eglSurface_ != EGL_NO_SURFACE) {   // ← 这个检查！
        initDecodePipeline(env);            // 只有 Surface 好了才做
    }
}
```

### 管道的总体结构

```
initDecodePipeline 的目标：建立一条从"MP4文件"到"屏幕"的完整流水线

  ①Demuxer ─→ ②SurfaceTexture ─→ ③HwDecoder ─→ ④SourceNode ─→ ⑤OutputNode
  "开水源"    "建传送带"        "装抽水泵"    "建净化车间"    "建配送站"
  
  每一步都依赖上一步的产物：
  ①→③ codecParams（编码格式信息）
  ②→③ ANativeWindow（传送带入口）
  ②→④ SurfaceTextureHelper*（传送带引用）
  ①→④ width, height（视频分辨率）
  ④→⑤ sourceNode_（渲染树连接）
```

---

### 管道第①���：Demuxer —— 打开 MP4 文件

```cpp
// RenderEngine.cpp:88-91
if (!demuxer_.open(path.c_str())) {
    return false;
}
```

#### 为什么需要 Demuxer？

MP4 文件不是把视频帧一帧一帧地排列在那��。它是一个**容器**（Container），里面混合了：
- 视频轨道（H.264 压缩数据）
- 音频轨道（AAC 压缩数据）
- 字幕轨道
- 元数据（时长、帧率、分辨率等）

Demuxer（解复用器）的工作 = **拆快递**：把混在一起的数据拆分开，一个一个地取出视频数据包。

```
MP4 文件（混合封装）：
┌──────────────────────────────────────────────────┐
│ [视频包1][音频包1][音频包2][视频包2][音频包3]...    │
│  ↑ H.264          ↑ AAC                          │
└──────────────────────────────────────────────────┘
                    │
            Demuxer（拆包）
                    │
                    ▼
         只取出视频包：[视频包1] [视频包2] [视频包3] ...
         每个包 = 一帧（或几帧）的 H.264 压缩数据
```

Red Engine 用 **FFmpeg 的 avformat** 库来做 Demuxer。

#### 这一步的产物

| 产物 | 类型 | 被谁使用 | 干什么 |
|------|------|---------|--------|
| codecParameters() | AVCodecParameters* | HwDecoder | 告诉解码器"视频是什么格式" |
| videoWidth/Height | int | SourceNode | 知道该创建多大的纹理 |
| fps() | double | 播放循环 | 控制播放速度 |
| readVideoPacket() | 函数 | 播放循环 | 逐个读取压缩数据包 |

---

### 管道第②步：SurfaceTextureHelper —— 建立传送带

```cpp
// RenderEngine.cpp:93-96
if (!stHelper_.create(env)) {
    return false;
}
```

#### 为什么需要这一步？

解码器即将把视频帧解码出来，但解码后的画面要送到哪里？

- 如果送到 CPU 内存 → 还得再上传到 GPU → 慢（两次拷贝）
- 如果直接送到 GPU 纹理 → 零拷贝 → 快

SurfaceTexture 就是让解码器**直接往 GPU 纹理里写画面**的桥梁。

#### 什么是 SurfaceTexture？

**一句话**：Android 提供的"生产者→消费者"管道，让硬件（解码器/摄像头）的输出直接变成 GPU 可用的纹理。

**比喻**：一条**传送带**——
- 入口（Surface / ANativeWindow）：解码器往这头放画面
- 出口（OES 纹理）：GPU 从这头取画面
- 中间的机制（BufferQueue）：自动排队、防止冲突

#### SurfaceTextureHelper::create() 逐行解析

```cpp
bool SurfaceTextureHelper::create(JNIEnv* env) {
```

**第1步：创建 OES 纹理（传送带的终点）**

```cpp
    glGenTextures(1, &oesTexId_);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, oesTexId_);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);
```

---

##### 【概念插入】什么是纹理（Texture）？

**一句话**：GPU 世界里的"图片"。

在 CPU 世界里，图片是一个像素数组（`unsigned char pixels[]`）。但 GPU 不能直接用 CPU 的数组——它有自己的显存。**纹理**就是"已经上传到 GPU 显存中的图片"。

你不能直接操作纹理里的像素（它们在 GPU 那边），你只能通过 GL 指令告诉 GPU："用这张纹理做某件事"。

**为什么叫"纹理"而不叫"图片"**：历史原因。最初 3D 游戏用它把图案"贴"到 3D 物体表面（像给家具贴木纹），所以叫 Texture（纹理/质地）。现在它的用途远超"贴图"，但名字沿用了。

---

##### 【概念插入】什么是 OES 纹理？

**全称**：GL_OES_EGL_image_external —— OpenGL ES 外部图像扩展

**为什么需要一种"特殊"纹理？**

普通纹理（GL_TEXTURE_2D）的使用方式：
1. 你在 CPU 侧准备好 RGBA 像素数组
2. 调用 `glTexImage2D(... pixels ...)` 上传到 GPU
3. GPU 收到一张标准格式的图片

但硬件解码器的输出：
- 格式不是 RGBA，可能是 YUV420、NV12、甚至芯片厂商私有格式
- 数据在解码器硬件的专用内存里，CPU 侧拿不到
- 坐标系可能是倒的（不同手机不一样）

**OES 纹理 = GPU 提供的"万能接口"**：

> "不管你往里面写的是什么格式，我在读取（采样）的时候会自动帮你转成 RGBA。"

它的"特殊"之处：

| 特性 | 普通 2D 纹理 | OES 纹理 |
|------|:---:|:---:|
| 谁写入数据 | 你（CPU）用 glTexImage2D | 硬件（解码器/摄像头）通过 BufferQueue |
| 你能看到里面什么格式吗 | 能，你自己传的 | 不能，只知道"有画面" |
| 坐标系 | 固定(左下=0,0) | 不确定，需要变换矩阵 |
| 能当 FBO 的输出目标吗 | 能 | **不能** |
| 能被普通 sampler2D 采样吗 | 能 | **不能**，必须用 samplerExternalOES |

**所以后面 SourceNode 的工作就是**：把这张"受限的 OES 纹理"转成"自由的 2D 纹理"。

---

##### 【概念插入】glGenTextures / glBindTexture / glTexParameteri

```cpp
glGenTextures(1, &oesTexId_);
```
"向 GPU 申请 1 ���纹理编号。" GPU 返回一个整数（比如 7）存到 oesTexId_ 里。
此时 GPU 那边只是分配了一个编号，还没有实际的数据。好比你去酒店前台拿到了房卡（房号7），但房间还是空的。

```cpp
glBindTexture(GL_TEXTURE_EXTERNAL_OES, oesTexId_);
```
"我接下来的操作针对 7 号纹理，类型是 OES。" 好比你刷房卡进了 7 号房间——之后的所有设置都在这个房间里生效。

```cpp
glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
```
设置"当纹理被缩小时怎么处理"。`GL_LINEAR`（线性插值）= 周围像素做平均，画面柔和。另一个选项 `GL_NEAREST` = 取最近像素，画面有锯齿但快。

```
GL_LINEAR（平滑缩小）：        GL_NEAREST（锯齿缩小）：
┌────────────┐               ┌────────────┐
│ ░░▒▒▓▓██  ���               │ ░ ▒ ▓ █    │
│  柔和过渡  │               │  硬边方块  │
└────────────┘               └────────────┘
```

```cpp
glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
```
设置"采样坐标超出 0~1 范围时怎么办"。
- S 方向 = 横向（类比 X 轴）
- T 方向 = 纵向（类比 Y 轴）
- `GL_CLAMP_TO_EDGE` = 超出边缘时，重复边缘最后一个像素的颜色（不会出现奇怪的重复图案）

```cpp
glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);
```
"我设置完了，退出房间。"解绑（绑定到 0 = 解绑当前目标）。

---

**第2步：创建 Java 层 SurfaceTexture（传送带本体）**

```cpp
    jclass stClass = env->FindClass("android/graphics/SurfaceTexture");
    jmethodID stCtor = env->GetMethodID(stClass, "<init>", "(I)V");
    jobject localST = env->NewObject(stClass, stCtor, (jint)oesTexId_);
    surfaceTexture_ = env->NewGlobalRef(localST);
```

这段代码等价于 Java/Kotlin 中的：
```kotlin
val surfaceTexture = SurfaceTexture(oesTexId_)
```

为什么用 JNI 写？因为 Red Engine 是 C++ 引擎，但 SurfaceTexture 是 Android Java API。

**关键参数 `oesTexId_`**：告诉 SurfaceTexture"你收到画面后，放到这个编号的 OES 纹理上"。这就是把传送带的"出口"连接到了 OES 纹理。

**第3步：创建 Surface（传送带入口）**

```cpp
    jclass surfaceClass = env->FindClass("android/view/Surface");
    jmethodID surfaceCtor = env->GetMethodID(surfaceClass, "<init>",
                                              "(Landroid/graphics/SurfaceTexture;)V");
    jobject localSurface = env->NewObject(surfaceClass, surfaceCtor, surfaceTexture_);
    surface_ = env->NewGlobalRef(localSurface);
```

等价于 Java：
```kotlin
val surface = Surface(surfaceTexture)
```

Surface = 传送带的"入口闸门"。生产者（解码器）通过这个入口往传送带上放东西。

**第4步：获取 ANativeWindow（入口的 C 语言句柄）**

```cpp
    window_ = ANativeWindow_fromSurface(env, surface_);
```

Java 的 `Surface` 和 C 的 `ANativeWindow` 是同一个东西的不同视角：
- `Surface` → Java 世界的门牌号
- `ANativeWindow` → C/C++ 世界的门牌号

因为 AMediaCodec（解码器）的 C 接口需要 ANativeWindow，所以要转换一下。

#### 第②步建立的连接

```
┌──────────────────────────────────────────────────────────┐
│  stHelper_.create() 建立了这条通道：                        ���
│                                                          │
│  ANativeWindow ← Surface ← SurfaceTexture → OES纹理     │
│  (C层入口)      (Java入口)   (传送带本体)    (出口/终点)   │
│       ↑                                         ↑        │
│       │                                         │        │
│  给解码器用                                  给 GPU 用    │
│  (下一步③)                                  (后续④)      │
└──────────────────────────────────────────────────────────┘
```

#### 这一步的产物

| 产物 | 被谁使用 |
|------|---------|
| `oesTexId_`（OES 纹理编号）| SourceNode::execute() 中采样这张纹理 |
| `window_`（ANativeWindow）| 传给 HwDecoder，作为解码输出目标 |
| `updateTexImage()`（方法）| 播放循环中，把最新帧锁定到 OES 纹理 |
| `getTransformMatrix()`（方法）| SourceNode 中获取坐标变换矩阵 |

---

### 管道第③步：HwDecoder —— 配置硬件解码器

```cpp
// RenderEngine.cpp:98-102
if (!hwDecoder_.init(demuxer_.codecParameters(), stHelper_.nativeWindow())) {
    return false;       //   ↑ 来自第①步            ↑ 来自第②步
}
```

#### 两个参数分别来自哪里？

- `demuxer_.codecParameters()`：来自第①步，告诉解码器"这是 H.264 格式��分辨率 1920×1080"
- `stHelper_.nativeWindow()`：来自第②步，告诉解码器"你的输出送到这个传送带入口"

#### HwDecoder::init() 核心逻辑（HwDecoder.cpp:81-123）

```cpp
bool HwDecoder::init(AVCodecParameters* params, ANativeWindow* outputSurface) {

    // ① 根据编码格式选择解码器类型
    const char* mime = nullptr;
    switch (params->codec_id) {
        case AV_CODEC_ID_H264:  mime = "video/avc"; break;
        case AV_CODEC_ID_HEVC:  mime = "video/hevc"; break;
        case AV_CODEC_ID_VP9:   mime = "video/x-vnd.on2.vp9"; break;
    }
    // mime = MIME 类型字符串，Android 系统用它找到对应的硬件解码器

    // ② 创建解码器实例
    codec_ = AMediaCodec_createDecoderByType(mime);
    // 比喻："告诉 Android 系统：我要一台 H.264 解码机器"
    // 系统会找到手机芯片里的硬件解码单元（如高通的 Venus、华为的 VPU）

    // ③ 配置解码器格式
    AMediaFormat* format = AMediaFormat_new();
    AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, mime);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_WIDTH, params->width);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_HEIGHT, params->height);
    // "这台机器要处理的是：H.264 格式、1920宽、1080高"

    // ④ ★★★ 关键一行：配置为 Surface Mode ★★★
    AMediaCodec_configure(codec_, format, outputSurface, nullptr, 0);
    //                                    ↑↑↑↑↑↑↑↑↑↑↑↑↑
    //                         传入了 ANativeWindow（来自第②步）
    //                         → Surface Mode（零拷贝）

    // 如果第三个参数传 nullptr → Buffer Mode（有拷贝，数据送到 CPU 内存）
    // 传了 Surface           → Surface Mode（零拷贝，数据直接到 GPU）

    // ⑤ 启动解码器
    AMediaCodec_start(codec_);
    // "机器开始运转，可以往里喂数据了"
}
```

#### 什么是 Surface Mode vs Buffer Mode？

```
Buffer Mode（不用）：
  解码器 → CPU内存(你的数组) → 你手动上传到GPU → 纹理
           ↑ 拷贝1              ↑ 拷贝2
  慢、耗电、需要自己管格式转换

Surface Mode（Red Engine 的选择）：
  解码器 → ANativeWindow → BufferQueue → OES纹理
           ↑ 来自②         ② 内部       ② 的终点
  全程零拷贝：解码器和GPU共享同一块物理内存
```

#### 什么是"零拷贝"？

**传统方式**（有拷贝）：
```
解码器完成 → 把像素数据复制到 CPU 内存 → 再复制到 GPU 显存 → 纹理可用
             (拷贝1: 约6MB/帧)           (拷贝2: 约6MB/帧)
             每秒30帧 = 360MB/s 的无谓数据搬运
```

**零拷贝方式**（Surface Mode）：
```
解码器完成 → 这块内存的"所有权"从解码器转给 GPU → 纹理可用
             数据纹丝不动！只是修改了一个指针
             额外开销 ≈ 0
```

**比喻**：
- 有拷贝 = 你把图书馆的书抄一遍带回家，再抄一遍给朋友
- 零拷贝 = 图书馆把那本书的借阅卡转给你，书没动过

#### 第③步建立的连接

```
至此，解码通道完全打通：

  demuxer_.readVideoPacket()  →  压缩数据包(AVPacket)
       │
       ▼
  hwDecoder_.queuePacket()    →  塞进解码器输入
       │
       │  (解码器硬件内部处理)
       ▼
  hwDecoder_.dequeueAndRender() → releaseOutputBuffer(true)
       │                              ↑ true = "送到 Surface"
       ▼
  BufferQueue（②内部）接收到一帧 → 通知 SurfaceTexture
       │
       ▼
  stHelper_.updateTexImage()  →  OES 纹理指向最新帧
```

---

### 管道第④步：SourceNode —— 从 OES 转为标准 2D 纹理

```cpp
// RenderEngine.cpp:101-111
sourceNode_ = new SourceNode(&stHelper_, env);
//                            ↑ 来自第②步（需要访问 OES 纹理和变换矩阵）
sourceNode_->initGL(w, h);
//                   ↑  ↑ 来自第①步（视频分辨率）
```

#### 为什么需要 SourceNode？做这步转换的目的是什么？

OES 纹理有严重限制（不能当 FBO 目标、不能被普通 shader 读取、坐标不确定）。但后续的滤镜处理、混合处理、导出编码等操作，都需要一张"正常的"2D 纹理。

SourceNode 的任务：**用一次 GPU 渲染，把 OES 纹理的内容"复印"到一张标准 2D 纹理上**。

```
OES 纹理（受限、可能倒着、格式未知）
     │
     │ SourceNode::execute()
     │ (用 OES shader 做一次渲染)
     ▼
普通 2D 纹理（自由、正的、标准 RGBA）
     │
     └→ 可以传给滤镜节点 / 混合节点 / 编码器 / 屏幕...
```

#### SourceNode::initGL() 做了什么

```cpp
bool SourceNode::initGL(int width, int height) {
    outputWidth = width;   // 记住分辨率（后面创建纹理和设置视口需要）
    outputHeight = height;

    // ═══ 第1件事：编译 Shader（GPU 的绘制配方）═══
    oesShader_.build(oesVertSrc, oesFragSrc);

    // ═══ 第2件事：创建 FullscreenQuad（全屏矩形）═══
    quad_.init();

    // ═══ 第3件事：创建输出纹理（2D）═══
    glGenTextures(1, &outputTex_);
    glBindTexture(GL_TEXTURE_2D, outputTex_);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0,
                 GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    // "创建一张 width × height 的空白 RGBA 画布"
    // nullptr = 不填初始数据（空白）

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    // ═══ 第4件事：创建 FBO 并挂载输出纹理 ═══
    glGenFramebuffers(1, &fbo_);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo_);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                           GL_TEXTURE_2D, outputTex_, 0);

    glCheckFramebufferStatus(GL_FRAMEBUFFER);  // 检查是否搭建成功
    glBindFramebuffer(GL_FRAMEBUFFER, 0);      // 解绑，切回默认（屏幕）
}
```

下面依次解释这四件事中的概念。

---

##### 【概念插入】什么��� Shader？

**全称**：着色器

**一句话**：你写给 GPU 的小程序，告诉 GPU"每个像素该算出什么颜色"。

**为什么需要它**：GPU 有几百个核心可以并行工作，但它需要一个"配方"——对每个像素做同样的计算。这个配方就是 Shader。

**两种 Shader**：

| Vertex Shader（顶点着色器） | Fragment Shader（片段着色器） |
|:---:|:---:|
| 处理"形状的角在哪" | 处理"每个像素是什么颜色" |
| 输入：顶点坐标 | 输入：插值后的纹理坐标 |
| 输出：屏幕位置 | 输出：像素颜色（RGBA） |
| 执行次数：几个（顶点数） | 执行次数：几百万（像素数） |

**SourceNode 的 OES Shader**：

```glsl
// 顶点着色器 —— "每个角放在哪"
#version 300 es
layout(location = 0) in vec2 aPosition;   // 输入：顶点位置
layout(location = 1) in vec2 aTexCoord;   // 输入：纹理坐标
uniform mat4 uTexMatrix;                  // 统一变量：变换矩阵
out vec2 vTexCoord;                       // 传给片段着色器

void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
    // 顶点位置直接用（已经是全屏坐标了）

    vTexCoord = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
    // 纹理坐标要经过变换矩阵处理
    // 这个矩阵来自 SurfaceTexture，它会修正画面的翻转/旋转
}
```

```glsl
// 片段着色器 —— "每个像素是什么色"
#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
// ↑ 声明需要 OES 纹理能力

precision mediump float;
uniform samplerExternalOES uTexture;   // OES 纹理采样器
in vec2 vTexCoord;                     // 从顶点着色器插值来的坐标
out vec4 fragColor;                    // 输出颜色

void main() {
    fragColor = texture(uTexture, vTexCoord);
    // 从 OES 纹理上取颜色 → 输出
    // GPU 内部自动把 YUV/硬件格式转成 RGBA
    // 这一行被 几百万个 GPU 核心同时执行（每个核心处理一个像素）
}
```

**Shader 的编译过程**（ShaderProgram::build）：

```
源代码(字符串) → glCompileShader → glLinkProgram → 可执行的GPU程序
                  "编译配方"          "装订成册"      "可以按配方画画了"
```

就像 C++ 代码需要编译才能运行一样，Shader 也需要编译。不同的是，这个编译发生在 **运行时**（程序启动后），由 GPU 驱动完成——因为不同手机的 GPU 不一样，需要针对当前硬件编译。

---

##### 【概念插入】什么是 VBO 和 VAO？

**为什么在这里需要它们？**

Shader 只是"配方"，但 GPU 还需要知道**对什么形状**执行这个配方。在视频引擎中，我们需要一个铺满整个画面的矩形——把纹理的每个像素都处理一遍。

这个矩形的顶点数据需要传给 GPU，VBO 和 VAO 就是完成这件事的。

---

###### VBO（Vertex Buffer Object，顶点缓冲对象）

**一句话**：把顶点数据从 CPU 传到 GPU 显存的"快递包裹"。

**为什么不能每次画画时临时传？** 因为 CPU→GPU 的数据传输很慢。VBO 让你只传一次，之后 GPU 每帧直接从自己的显存读，不用等 CPU。

**FullscreenQuad 的顶点数据**：

```cpp
static const float kQuadVertices[] = {
    // x,    y,    u,    v      ← 每个顶点4个数字
    -1.0f, -1.0f, 0.0f, 0.0f,  // 顶点0：屏幕左下角，对应纹理左下角
     1.0f, -1.0f, 1.0f, 0.0f,  // 顶点1：屏幕右下角，对应纹理右下角
    -1.0f,  1.0f, 0.0f, 1.0f,  // 顶点2：屏幕左上角，对应纹理左上角
     1.0f,  1.0f, 1.0f, 1.0f,  // 顶点3：屏幕右上角，对应纹理右上角
};
```

坐标系说明：
- x, y 范围 -1 到 +1（NDC 坐标）：(-1,-1)=左下角，(+1,+1)=右上角，铺满全屏
- u, v 范围 0 到 1（纹理坐标）：(0,0)=纹理左下角，(1,1)=纹理右上角，覆盖整张纹理

4 个角 = 整个屏幕 = 整张纹理。所以 shader 对每个像素采样后，结果就是完整画面。

**上传到 GPU**：

```cpp
glGenBuffers(1, &vbo_);
// "GPU，给我分配一个快递柜格子（编号）"

glBindBuffer(GL_ARRAY_BUFFER, vbo_);
// "我要往这个格子里放东西"

glBufferData(GL_ARRAY_BUFFER, sizeof(kQuadVertices), kQuadVertices, GL_STATIC_DRAW);
// "把这 64 字节的数据放进去，内容不会变（STATIC），用途是画画（DRAW）"
// 数据从 CPU 内存拷贝到 GPU 显存，之后 CPU 侧的数组就不需要了
```

---

###### VAO（Vertex Array Object，顶点数组对象）

**一句话**：告诉 GPU "怎么从 VBO 里正确拆出各个属性"的说明书。

**为什么需要它？**

VBO 里就是一串连续的 float 数字：

```
[-1.0, -1.0, 0.0, 0.0, 1.0, -1.0, 1.0, 0.0, ...]
```

GPU 看到的就是一坨字节，不知道哪些是位置、哪些是纹理坐标。VAO 就是告诉它"怎么拆"。

**详细拆解过程**：

VBO 在 GPU 显存中的字节排列：

```
字节地址:  0    4    8    12   16   20   24   28   32   36   40   44   48   52   56   60
          ┌────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┐
内容:      │-1.0│-1.0│ 0.0│ 0.0│ 1.0│-1.0│ 1.0│ 0.0│-1.0│ 1.0│ 0.0│ 1.0│ 1.0│ 1.0│ 1.0│ 1.0│
          └────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┘
          │     顶点0      │     顶点1      │     顶点2      │     顶点3      │
```

每个 float 占 4 字节。每个顶点 4 个 float = 16 字节。

**设置属性0（位置 aPosition）**：

```cpp
glEnableVertexAttribArray(0);     // "属性0 启用"
glVertexAttribPointer(
    0,                            // 属性编号 0（对应 shader 里 layout(location=0)）
    2,                            // size=2：每次取 2 个数字（x 和 y）
    GL_FLOAT,                     // 每个数字是 float（4字节）
    GL_FALSE,                     // 不归一化
    4 * sizeof(float),            // stride=16：每16字节是一个"重复单元"
    (void*)0                      // offset=0：从字节0开始读第一个
);
```

**stride（步长）= 16 的意思**：

```
"从顶点0的位置数据开头，到顶点1的位置数据开头，隔了多远？"

字节0 → x₀   ─┐
字节4 → y₀    │ 这是属性0要读的（size=2, 共8字节）
字节8 → u₀    │ 
字节12 → v₀   │ ← 这8字节不是属性0的,跳过
字节16 → x₁   ─┘ ← 到了！距离 = 16字节 = stride

所以 stride = 一个完整顶点的总大小 = 4个float × 4字节 = 16字节
```

**offset（偏移）= 0 的意思**：

```
"第一个要读��数据在 VBO 的第几字节？"

位置数据从 VBO 开头就开始了（字节0是 x₀），所以 offset = 0
```

GPU 的读取模式：
```
从字节0开始,取2个float → (-1.0, -1.0) → 顶点0的位置
跳到字节16,取2个float → ( 1.0, -1.0) → 顶点1的位置
跳到字节32,取2个float → (-1.0,  1.0) → 顶点2的位置
跳到字节48,取2个float → ( 1.0,  1.0) → 顶点3的位置
```

**设置属性1（纹理坐标 aTexCoord）**：

```cpp
glEnableVertexAttribArray(1);     // "属性1 启用"
glVertexAttribPointer(
    1,                            // 属性编号 1
    2,                            // size=2：每次取 2 个数字（u 和 v）
    GL_FLOAT,                     // float
    GL_FALSE,                     // 不归一化
    4 * sizeof(float),            // stride=16（和属性0一样，因为共用一个VBO）
    (void*)(2 * sizeof(float))    // offset=8：从字节8开始读第一个
);
```

**offset = 8 的意思**：

```
在每个顶点的16字节中，纹理坐标(u,v)在哪里？

字节0 → x  ─┐
字节4 → y   ├ 前面有 2个float = 8字节 → 跳过
字节8 → u  ─┐ ← 从这里开始！offset = 8
字节12 → v  ┘
```

GPU 的读取模式：
```
从字节8开始, 取2个float → (0.0, 0.0) → 顶点0的纹理坐标
跳到字节24,取2个float → (1.0, 0.0) → 顶点1的纹理坐标
跳到字节40,取2个float → (0.0, 1.0) → 顶点2的纹理坐标
跳到字节56,取2个float → (1.0, 1.0) → 顶点3的纹理坐标
```

**两个属性同时从同一块 VBO 中读，互不干扰**：

```
字节:   0    4    8    12   16   20   24   28   ...
       ┌────┬────┬────┬────┬────┬────┬────┬────┬─
       │ x₀ │ y₀ │ u₀ │ v₀ │ x₁ │ y₁ │ u₁ │ v₁ │
       └────┴────┴────┴────┴────┴────┴────┴────┴─
       ▲────▲              ▲───��▲
       属性0读这里           属性0读这里
            ▲────▲              ▲────▲
            属性1读这里           属性1读这里
```

**VAO 的作用就是把这两套读取规则"快照"下来**。以后每次画画只需要 `glBindVertexArray(vao_)` 一行，GPU 就知道所有的拆包规则，不用重复设置。

---

##### 【概念插入】什么是 FBO（Framebuffer Object）？

**全称**：帧缓冲对象

**一句话**：让 GPU 把画面画到一张纹理上（而不是屏幕上）的"工作台"。

**为什么 SourceNode 需要它？**

SourceNode 要做的事是：从 OES 纹理采样 → 画到 outputTex_ 上。

但 GPU 默认的绘制目标是**屏幕**（framebuffer 0）。如果直接画，结果就跑到屏幕上了——而我们还没做完后续处理（滤镜、混合等）！

FBO 让你说："接下来我画的东西，不要去屏幕，画到我指定的这张纹理里。"

```
没有 FBO：                        有 FBO：
  shader画画 → 屏幕               shader画画 → outputTex_（离屏）
  (直接显示,无法后续处理)            后续节点还可以继续加工
```

**比喻**：
- 默认绘制 = 直接在展厅墙上画（观众立刻看到半成品）
- FBO 绘制 = 先在工作室的画板上画（画好再搬到展厅）

**创建 FBO 并挂载纹理**：

```cpp
glGenFramebuffers(1, &fbo_);
// "给我一块画板的编号"

glBindFramebuffer(GL_FRAMEBUFFER, fbo_);
// "我现在要设置这块画板"

glFramebufferTexture2D(
    GL_FRAMEBUFFER,          // 目标：当前绑定的画板
    GL_COLOR_ATTACHMENT0,    // 挂载点："颜色输出" 位置
    GL_TEXTURE_2D,           // 挂什么类型：2D 纹理
    outputTex_,              // 挂哪张纹理：outputTex_
    0                        // mipmap 层级 0（最大尺寸那层）
);
// "把 outputTex_ 钉在画板的颜色输出位置"
// 之后所有画画结果 → 自动写入 outputTex_ 的像素里

glBindFramebuffer(GL_FRAMEBUFFER, 0);
// "设置完了，切回默认目标（屏幕）"
// 0 = 默认帧缓冲 = 屏幕
```

**切换绘制目标只需���一行**：
```cpp
glBindFramebuffer(GL_FRAMEBUFFER, fbo_);  // 画到 FBO（离屏）
glBindFramebuffer(GL_FRAMEBUFFER, 0);     // 画到屏幕
```

---

#### SourceNode initGL 的产物

| 产物 | 作用 |
|------|------|
| `oesShader_` | GPU 可执行程序，能从 OES 纹理采样并输出 RGBA |
| `quad_` (VAO+VBO) | 全屏矩形的顶点数据，Shader 要画在上面 |
| `outputTex_` | 空白的 2D 纹理，大小 = 视频分辨率，接收渲染结果 |
| `fbo_` | 帧缓冲，把 outputTex_ 挂在上面当"画布" |

这些组合在一起，就构成了一个"OES→2D 转换器"。每帧调用 execute() 时会全部用上。

---

### 管道第⑤步：OutputNode —— 创建最终输出节点

```cpp
// RenderEngine.cpp:113-118
outputNode_ = new OutputNode();
outputNode_->initGL();
outputNode_->inputs.push_back(sourceNode_);
//                             ↑ 来自第④步
```

#### 为什么需要单独的 OutputNode？

SourceNode 产出的是一张 2D 纹理（在 FBO 里）。但这张纹理在 GPU 显存中，用户看不到。需要有人把它"搬到屏幕上"。

OutputNode 的职责：从子节点（SourceNode）拿到 2D 纹理 → 画到默认帧缓冲（屏幕）上。

#### OutputNode::initGL() 做了什么

```cpp
bool OutputNode::initGL() {
    // 编译一个 passthrough shader（直通着色器：原样输出，不做任何变换）
    passthrough_.build(kPassthroughVS, kPassthroughFS);
    // 片段着色器只有一行：fragColor = texture(uTexture, vTexCoord);
    // 和 SourceNode 的 OES shader 类似，但使用普通的 sampler2D
    // （因为输入已经是标准 2D 纹理了，不是 OES）

    // 同样创建一个全屏矩形
    quad_.init();
}
```

**inputs.push_back(sourceNode_)** —— 建立渲染树连接：

```
渲染树结构：
  OutputNode（根节点）
     │
     │ inputs[0]
     ↓
  SourceNode（叶子节点）
  
  执行顺序（后序遍历）：先执行子节点，再执行父节点
  SourceNode::execute() → outputTex_ → OutputNode::execute() → 屏幕
```

---

### 管道建设完成！最终连接图

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  initDecodePipeline 建立的完整数据通路：                           │
│                                                                 │
│  ① Demuxer ────读取压缩数据────→ AVPacket                        │
│                                    │                            │
│                                    ▼                            │
│  ③ HwDecoder ──解码(零拷贝)──→ BufferQueue                      │
│                                    │                            │
│                                    ▼                            │
│  ② SurfaceTexture ──updateTexImage──→ OES 纹理                  │
│                                           │                     │
│                                           ▼                     │
│  ④ SourceNode ──OES shader + FBO──→ outputTex_(2D纹理)          │
│                                           │                     │
│                                           ▼                     │
│  ⑤ OutputNode ──passthrough shader──→ framebuffer 0（屏幕缓冲）  │
│                                           │                     │
│                                           ▼                     │
│  第二幕的 eglSurface_ ──swapBuffers──→ 手机屏幕显示              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 第四幕：播放循环（每一帧的旅程）

管道建好后，`pipelineInitialized_ = true`。当用户按下播放（`playing_ = true`），while 循环进入主播放逻辑。

每次循环处理**一帧**画面。下面逐步跟一帧走完全程。

---

### 第A步：从文件读取一个压缩数据包

```cpp
// RenderEngine.cpp:296-311

while (!eof) {
    if (!hasPendingPkt) {
        if (!demuxer_.readVideoPacket(pkt)) {
            eof = true;               // 文件读完了
            hwDecoder_.queueEOS();    // 告诉解码器"没有更多数据了"
            break;
        }
        hasPendingPkt = true;         // 标记：有一个包待处理
    }
    if (hwDecoder_.queuePacket(pkt)) {
        av_packet_unref(pkt);         // 解码器收了 → 释放包
        hasPendingPkt = false;
    } else {
        break;  // 解码器满了 → 这个包留着，下轮循环再试
    }
}
```

**发生了什么**：
1. 从 MP4 文件中读出一个 H.264 压缩数据包（AVPacket）
2. 尝试塞进解码器的输入队列
3. 如果解码器说"我满了"→ 把包保留，下一轮再喂

**为什么用 while 循环不停地喂？**

解码器像一个管道——你从一头塞数据，它从另一头吐出画面。你需要"持续喂"，直到它���诉你"消化不了了"。

```
Demuxer: [包1] [包2] [包3] [包4] ...
              ↓    ↓    ↓
         ┌──────────────────────┐
         │    HwDecoder 内部     │
         │   (正在解码中...)     │
         │   [满了,不收了]       │←── queuePacket 返回 false
         └──────────┬───────────┘
                    ↓
              输出一帧画面
```

---

### 第B步：取出一帧解码后的画面

```cpp
// RenderEngine.cpp:314-315

int64_t pts = hwDecoder_.dequeueAndRender(30000);
if (pts < 0) continue;  // 还没有帧就绪，回到循环顶部继续喂数据
```

**HwDecoder::dequeueAndRender() 内部**：

```cpp
ssize_t bufIdx = AMediaCodec_dequeueOutputBuffer(codec_, &info, timeoutUs);
// "解码器，你有处理好的画面吗？"
// 等待最多 30ms（30000 微秒）

if (bufIdx >= 0) {
    // 有！第 bufIdx 号输出 buffer 里有一帧画面

    AMediaCodec_releaseOutputBuffer(codec_, bufIdx, true);
    //                                              ↑↑↑↑
    // true = "把这帧画面送到 Surface 去"
    // 这一行触发了零拷贝传递！
    // buffer 的所有权从解码器 → BufferQueue → SurfaceTexture

    return info.presentationTimeUs;  // 返回这帧的时间戳（PTS）
}
```

**`releaseOutputBuffer(true)` 是整个零拷贝链的触发点**：

```
releaseOutputBuffer(bufIdx, true)
       │
       │  (Android 内部 BufferQueue 机制)
       ▼
SurfaceTexture 收到一帧
→ 触发 onFrameAvailable() 回调
→ frameAvailable_ = true
→ frameCond_.notify_one()  （唤醒等待中的渲染线程）
```

---

### 第C步：把画面锁定到 OES 纹理上

```cpp
// RenderEngine.cpp:318-320

stHelper_.waitForFrame(50);
// "等待传送带到货通知（最多等50ms）"
// 内部：frameCond_.wait_for(lock, 50ms)

stHelper_.consumeFrameAvailable();
// "清除到货标记"（防止重复处理）

stHelper_.updateTexImage(env);
// ★ 关键操作 ★
// 调用 Java 层 SurfaceTexture.updateTexImage()
// 效果：OES 纹理现在"指向"最新的那一帧画面
// 注意：不是拷贝！只是切换了 buffer 指针
```

执行完这三行后，`oesTexId_`（OES 纹理）上就是最新的视频帧了。

---

### 第D步：帧节奏控制（保证播放速度正确）

```cpp
// RenderEngine.cpp:326-346

// 把解码器返回的 PTS 转换为微秒
int64_t framePtsUs = av_rescale_q(pts, tb, {1, 1000000});
currentPositionUs_.store(framePtsUs);

auto nowUs = ...; // 当前系统时间（微秒）

if (anchorPtsUs < 0) {
    // 第一帧：建立"锚点"
    anchorPtsUs = framePtsUs;    // 记住：第一帧的视频时间是多少
    anchorWallUs = nowUs;        // 记住：第一帧的系统时间是多少
} else {
    // 后续帧：计算应该等多久
    int64_t videoDeltaUs = framePtsUs - anchorPtsUs;   // 视频时间过了多久
    int64_t wallDeltaUs  = nowUs - anchorWallUs;       // 系统时间过了多久
    int64_t sleepUs = videoDeltaUs - wallDeltaUs;      // 差值

    if (sleepUs > 1000) {
        usleep(sleepUs);  // 帧超前了 → 等一等
    }
    // sleepUs < 0 说明帧已经迟了 → 立即渲染（不丢帧）
}
```

**为什么需要这一步？**

解码器解码非常快（可能远快于实际帧率）。如果不控制节奏，30fps 的视频会在 1 秒内播完 10 秒的内容。

**锚点法原理**：

```
第1帧：PTS=0ms,     系统时间=1000ms    → 建立锚点 (0, 1000)
第2帧：PTS=33ms,    系统时间=1035ms
        视频过了 33ms, 系统过了 35ms → 差值=-2ms → 略落后,立即渲染
第3帧：PTS=66ms,    系统时间=1050ms
        视频过了 66ms, 系统过了 50ms → 差值=16ms → 太快了,sleep 16ms
```

---

### 第E步：渲染树执行

```cpp
// RenderEngine.cpp:349-352

outputNode_->outputWidth = surfaceWidth;    // 告诉 OutputNode 屏幕多大
outputNode_->outputHeight = surfaceHeight;
outputNode_->execute(framePtsUs);           // 启动渲染树！
eglCore_.swapBuffers(eglSurface_);          // 翻页送显
```

**`outputNode_->execute()` 内部会触发整条渲染链**：

```cpp
// OutputNode::execute()
GLuint inputTex = inputs[0]->execute(timelinePositionUs);
//                ↑ inputs[0] = sourceNode_
//                先执行 SourceNode
```

```cpp
// SourceNode::execute() —— 被 OutputNode 调用
GLuint SourceNode::execute(...) {
    // ❶ 获取变换矩阵（"这帧画面怎么摆正"）
    float texMatrix[16];
    stHelper_->getTransformMatrix(env_, texMatrix);

    // ❷ 切换到 FBO（"画到 outputTex_ 上，不画到屏幕"）
    glBindFramebuffer(GL_FRAMEBUFFER, fbo_);
    glViewport(0, 0, outputWidth, outputHeight);  // 画布范围 = 视频分辨率

    // ❸ 使用 OES shader
    oesShader_.use();

    // ❹ 绑定 OES 纹理到 0 号纹理插槽
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, stHelper_->oesTexId());
    oesShader_.setInt("uTexture", 0);       // "shader 里的 uTexture → 0号插槽"
    oesShader_.setMatrix4("uTexMatrix", texMatrix);  // "用这个矩阵摆正"

    // ❺ 画全屏矩形
    quad_.draw();
    // GPU 几百万核心同时执行 fragment shader：
    // 从 OES 纹理采样 → 通过矩阵变换坐标 → 写入 FBO 上的 outputTex_

    // ❻ 清理，切回默认
    glBindFramebuffer(GL_FRAMEBUFFER, 0);

    return outputTex_;  // "我的产物：一张标准的 2D 纹理"
}
```

回到 OutputNode::execute()：

```cpp
// OutputNode::execute() —— 续
GLuint inputTex = ...; // = sourceNode_ 返回的 outputTex_

// ❼ 切换到屏幕（framebuffer 0）
glBindFramebuffer(GL_FRAMEBUFFER, 0);
glViewport(0, 0, outputWidth, outputHeight);  // 画布范围 = 屏幕尺寸

// ❽ 使用直通 shader
passthrough_.use();
glActiveTexture(GL_TEXTURE0);
glBindTexture(GL_TEXTURE_2D, inputTex);       // 绑定 SourceNode 的输出
passthrough_.setInt("uTexture", 0);

// ❾ 画全屏矩形
quad_.draw();
// GPU 把 inputTex 的内容原样画到屏幕后缓冲上
```

---

### 第F步：swapBuffers —— 画面显示

```cpp
eglCore_.swapBuffers(eglSurface_);
```

**什么是双缓冲？为什么需要 swap？**

GPU 用"双缓冲"机制防止用户看到画了一半的画面：

```
┌──────────────┐    ┌──────────────┐
│  前缓冲       │    │  后缓冲       │
│  (正在显示)   │    │  (正在绘制)   │
│              │    │              │
│  上一帧画面   │    │  当前帧画面   │
│              │    │  (刚画完)     │
└──────────────┘    └──────────────┘
       ↑                    │
       │                    │
       └── swapBuffers() ───┘
            "瞬间互换角色"
            后缓冲→前缓冲（显示）
            前缓冲→后缓冲（可以画新的了）
```

如果没有双缓冲，用户会看到 GPU "画了一半"的中间状态（撕裂/闪烁）。

swap 之后，用户在手机屏幕上看到了这一帧画面。

---

## 第五幕：完整一帧���旅程（最终总结）

```
    时间线 →→→→→→→→→→→→→→→→→→→→→→→→→→���→→→→→

    ┌─────────────────────────────────────────────────────────────┐
    │                    一帧画面的完整旅行                          │
    └─────────────────────────────────────────────────────────────┘

    MP4 文件
       │
       │ demuxer_.readVideoPacket(pkt)
       │ "拆包：从容器中取出一个 H.264 压缩数据包"
       ▼
    AVPacket（压缩数据,约几十KB）
       │
       │ hwDecoder_.queuePacket(pkt)
       │ "塞进解码器输入队列"
       ▼
    ┌─────��──────────────────────┐
    │  手机芯片的视频解码硬件单元  │
    │  (高通 Venus / 华为 VPU)   │
    │  "把 H.264 还原成像素"     │
    └─────────────┬──────────────┘
                  │
                  │ AMediaCodec_releaseOutputBuffer(true)
                  │ "解码完成,送到传送带"
                  ▼
    ┌────────────────────────────┐
    │  BufferQueue（共享内存）     │
    │  解码器和 GPU 共享这块内存   │
    │  数据没有移动（零拷贝）      │
    └─────────────┬──────────────┘
                  │
                  │ stHelper_.updateTexImage()
                  │ "传送带取件 → 锁定到 OES 纹理"
                  ▼
    OES 纹理（GPU 可读,格式未知,可能是反的）
                  │
                  │ SourceNode::execute()
                  │ "OES shader + 变换矩阵 + FBO"
                  │ "207万个 GPU 核心并行：采样→转RGBA→写入 outputTex_"
                  ▼
    outputTex_（标准 2D RGBA 纹理,正向,自由使用）
                  │
                  │ OutputNode::execute()
                  │ "passthrough shader → framebuffer 0"
                  │ "207万个 GPU 核心并行：采样→原样输出到屏幕后缓冲"
                  ▼
    屏幕后缓冲（画完了,但用户还看不到）
                  │
                  │ eglSwapBuffers()
                  │ "前后缓冲互换"
                  ▼
    ┌──────────┐
    │  手机屏幕  │ ← 用户看到这一帧画面！
    └──────────┘

    ─── 整个过程耗时约 5~15ms（取决于分辨率和硬件）───
    ─── 每秒重复 30 次 = 30fps 流畅视频 ───
```

---

## 附录：各步骤之间的产物传递关系

```
第一幕（EGL初始化）
  └→ context_（画笔）──────────────────────────┐
                                               │ 所有 GL 调用的前提
第二幕（Surface就绪）                            │
  ��→ eglSurface_（画布）───┐                    │
  └→ makeCurrent ──────────┼───── GL 可用 ─────┘
  └→ surfaceWidth/Height ──┼──→ OutputNode::execute() 中的 glViewport
                            │
第三幕（建管道）              │
  ①demuxer                  │
    └→ codecParams ─────────┼──→ ③HwDecoder::init() "什么格式"
    └→ width/height ────────┼──→ ④SourceNode::initGL() "纹理多大"
    └→ readVideoPacket() ───┼──→ 第四幕每帧：读取压缩包
    └→ fps() ───────────────┼──→ 第四幕：帧率信息
                            │
  ②stHelper                 │
    └→ window_ ─────────────┼──→ ③HwDecoder::init() "输出到哪"
    └→ oesTexId_ ───────────┼──→ ④SourceNode::execute() "采样哪张纹理"
    └→ updateTexImage() ────┼──→ 第四幕每帧：取件
    └→ getTransformMatrix() ┼──→ 第四幕每帧：变换矩阵
                            │
  ③hwDecoder                │
    └→ queuePacket() ───────┼──→ 第四幕每帧：喂数据
    └→ dequeueAndRender() ──┼──→ 第四幕���帧：触发零拷贝传递
                            │
  ④sourceNode               │
    └→ execute() ───────────┼──→ 返回 outputTex_
    └→ outputTex_ ──────────┼──→ ⑤OutputNode 采样它
                            │
  ⑤outputNode               │
    └→ inputs[0]=sourceNode ┼──→ execute() 时调用 sourceNode
    └→ execute() ───────────┼──→ 画到 framebuffer 0
                            │
第四幕（播放循环）             │
  └→ swapBuffers ───────────┼──→ eglSurface_（来自第二幕）
                            │       └→ 屏幕显示
                            │
```

---

## 概念速查表

| 概念 | 全称 | 一句话解释 | 在哪一步被需要 | 为什么需要它 |
|------|------|-----------|--------------|------------|
| EGL | Embedded Graphics Library | CPU↔GPU 的连接协议 | 第一幕 | 没有它就没法用 GPU |
| Display | EGL Display | GPU 设备连接 | 第一幕 | 找到哪块 GPU |
| Context | EGL Context | GPU 状态集合（画笔+调色盘） | 第一幕 | 所有 GL 操作的前提 |
| EGLSurface | EGL Surface | GPU 绘制目标（画布） | 第二幕 | 指定画到哪里 |
| makeCurrent | - | 绑定 context+surface 到线程 | 第二幕 | GL 调用才能生效 |
| Texture | 纹理 | GPU 显存中的图片 | 第三幕② | 存储画面数据 |
| OES纹理 | GL_OES_EGL_image_external | 硬件直写的特殊纹理 | 第三幕② | 接收解码器零拷贝输出 |
| SurfaceTexture | - | 解码器→GPU 的传送带 | 第三幕② | 零拷贝桥梁 |
| ANativeWindow | - | Surface 的 C 层句柄 | 第三幕② | 传给 C 接口的解码器 |
| AMediaCodec | - | NDK 硬件编解码器接口 | 第三幕③ | 利用硬件加速解码 |
| Surface Mode | - | 解码输出直连 Surface | 第三幕③ | 实现零拷贝 |
| Shader | 着色器 | GPU 的绘制配方程序 | 第三幕④ | 告诉 GPU 每像素怎么算 |
| VBO | Vertex Buffer Object | 顶点数据的 GPU 存储 | 第三幕④ | 告诉 GPU 画什么形状 |
| VAO | Vertex Array Object | VBO 的拆包说明书 | 第三幕④ | 告诉 GPU 怎么拆数据 |
| FBO | Framebuffer Object | 离屏渲染目标 | 第三幕④ | 画到纹理而非屏幕 |
| swapBuffers | - | 双缓冲交换 | 第四幕F | 避免撕裂，一次性送显 |
| PTS | Presentation Time Stamp | 帧显示时间戳 | 第四幕D | 控制播放速度 |
