# Red Engine 渲染播放入门指南

> 面向没有 OpenGL、EGL、FFmpeg、MediaCodec 经验的读者。
> 目标不是背 API，而是看懂一帧画面如何从视频文件走到手机屏幕。

本文分两层：

1. 先讲基础概念：EGL、Texture、OES 纹理、Shader、FBO、SurfaceTexture。
2. 再讲 Red Engine 的真实代码流程：初始化、解码、渲染树、上屏。

如果只看第二层，很多代码会像“魔法”。所以本文先把第一层讲清楚。

---

## 0. 一句话总览

一帧视频画面在 Red Engine 中大致走这条路：

```text
MP4 文件
  -> FFmpeg Demuxer 拆出压缩视频包
  -> AMediaCodec 硬解码
  -> SurfaceTexture 接收解码后的画面
  -> OES 纹理让 GPU 读到这帧
  -> SourceNode 把 OES 纹理转成普通 2D 纹理
  -> Render Tree 可选混合、滤镜、叠加
  -> OutputNode 画到屏幕缓冲
  -> eglSwapBuffers 显示到屏幕
```

这条链路里最重要的思想是：

```text
CPU 负责调度流程。
GPU 负责处理像素。
解码器负责把压缩视频还原成画面。
SurfaceTexture 负责把解码器输出接到 GPU 纹理。
Render Tree 负责把多个 GPU 处理步骤串起来。
```

---

# 第一层：看懂播放流程前必须知道的基础

## 1. CPU、GPU 和 OpenGL 是什么关系

手机里至少有两个重要角色：

```text
CPU:
  运行 C++ / Kotlin 代码
  适合判断、调度、读文件、管理对象

GPU:
  处理大量像素
  适合并行计算
  不适合复杂业务判断
```

OpenGL ES 是 CPU 给 GPU 下命令的 API。

你调用：

```cpp
glBindTexture(...);
glUseProgram(...);
glDrawArrays(...);
```

并不是 CPU 自己在画图，而是在告诉 GPU：

```text
接下来用哪张纹理。
接下来用哪个 shader。
现在开始画。
```

所以理解 GL 代码时，要一直问三个问题：

```text
这个调用改了 GPU 的什么状态？
这个调用创建了什么 GPU 资源？
这个调用是否真正触发绘制？
```

大部分 `gl*` 函数都是在改状态或准备资源。真正触发绘制的通常是：

```cpp
glDrawArrays(...);
glDrawElements(...);
```

在本项目里，`quad_.draw()` 内部最终会调用 `glDrawArrays(...)`。

---

## 2. OpenGL 是一个“状态机”

OpenGL 的一个核心特点是：它有很多“当前状态”。

例如：

```cpp
glBindTexture(GL_TEXTURE_2D, texId);
glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
```

这两行的意思不是：

```text
给 texId 设置参数。
```

而是：

```text
第一行：把 texId 绑定成当前 GL_TEXTURE_2D 纹理。
第二行：给当前 GL_TEXTURE_2D 纹理设置参数。
```

这就是“绑定”的含义。

可以把 OpenGL 想成一个工作台：

```text
glBindTexture:
  把某张纹理放到当前工作位。

glTexParameteri:
  修改当前工作位上那张纹理的参数。

glBindFramebuffer:
  决定接下来画到屏幕，还是画到某张离屏纹理。

glUseProgram:
  决定接下来绘制时使用哪个 shader 程序。
```

如果你看不懂一个 GL 函数，先判断它属于哪类：

| 类型 | 常见函数 | 含义 |
|---|---|---|
| 创建资源 | `glGenTextures`, `glGenFramebuffers`, `glGenBuffers` | 向 GPU 要一个资源编号 |
| 绑定资源 | `glBindTexture`, `glBindFramebuffer`, `glBindBuffer` | 把资源设为当前操作对象 |
| 设置状态 | `glTexParameteri`, `glViewport`, `glUniform*` | 改当前绘制状态 |
| 上传或分配数据 | `glTexImage2D`, `glBufferData` | 给 GPU 资源准备存储或数据 |
| 执行绘制 | `glDrawArrays` | 让 GPU 开始跑 shader 并输出像素 |

---

## 3. EGL：让 OpenGL ES 能在 Android 上画出来

OpenGL ES 只负责“怎么画”。但它不知道 Android 的窗口在哪里，也不知道 GPU 设备怎么连接。

EGL 负责这些基础设施：

```text
找到显示设备。
选择颜色格式。
创建 OpenGL ES 上下文。
创建绘制目标。
把上下文和绘制目标绑定到当前线程。
```

项目代码在 `EglCore` 中封装这些步骤。

### 3.1 `eglGetDisplay`

代码位置：`render-engine/src/main/cpp/gl/EglCore.cpp`

```cpp
display_ = eglGetDisplay(EGL_DEFAULT_DISPLAY);
```

作用：

```text
拿到默认显示设备的 EGLDisplay。
```

可以把 `EGLDisplay` 理解为 EGL 和底层显示系统之间的连接句柄。

它不是屏幕上的画布，也不是最终画面，只是后面所有 EGL 操作的入口。

### 3.2 `eglInitialize`

```cpp
eglInitialize(display_, &major, &minor);
```

作用：

```text
初始化这个 EGLDisplay。
```

这一步成功后，EGL 才真正可用。

### 3.3 `eglChooseConfig`

```cpp
EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
EGL_SURFACE_TYPE, EGL_WINDOW_BIT | EGL_PBUFFER_BIT,
EGL_RED_SIZE, 8,
EGL_GREEN_SIZE, 8,
EGL_BLUE_SIZE, 8,
EGL_ALPHA_SIZE, 8,
```

作用：

```text
告诉 EGL：我要一种支持 OpenGL ES 3.0、能画到窗口、RGBA 每通道 8 位的配置。
```

`config_` 就是满足这些条件的“画布规格”。

### 3.4 `eglCreateContext`

```cpp
context_ = eglCreateContext(display_, config_, EGL_NO_CONTEXT, contextAttribs);
```

作用：

```text
创建 OpenGL ES 上下文。
```

Context 是一组 GL 状态的集合，例如：

```text
当前绑定了哪张纹理。
当前使用哪个 shader。
当前 framebuffer 是谁。
当前 viewport 是多大。
```

所有 GL 调用都依赖“当前线程已经有一个 current context”。

### 3.5 `eglCreateWindowSurface`

```cpp
eglSurface_ = eglCore_.createWindowSurface(window_);
```

作用：

```text
把 Android 的 ANativeWindow 包装成 EGLSurface。
```

`EGLSurface` 是 GL 绘制结果的目标。

在 Red Engine 里，这个目标来自 Android UI 层传进来的 `SurfaceView` 底层窗口。

### 3.6 `eglMakeCurrent`

```cpp
eglCore_.makeCurrent(eglSurface_);
```

内部是：

```cpp
eglMakeCurrent(display_, surface, surface, context_);
```

作用：

```text
把 context 和 surface 绑定到当前渲染线程。
```

这一步之后，当前线程调用的 GL 命令才知道：

```text
用哪套 GL 状态。
最终画到哪个 surface。
```

如果没有 `makeCurrent`，后面的 `glGenTextures`、`glBindTexture`、`glDrawArrays` 都没有正确的执行环境。

---

## 4. Texture：GPU 里的图片

Texture 可以先理解成 GPU 里的图片。

但它和普通 C++ 数组不同：

```text
普通图片数组:
  数据在 CPU 内存里。
  你可以直接读写每个字节。

OpenGL Texture:
  数据在 GPU 可访问的内存里。
  CPU 不能直接摸里面的像素。
  CPU 只能通过 OpenGL API 操作它。
```

### 4.1 `GLuint texId` 是什么

在代码里，纹理通常只是一个整数：

```cpp
GLuint outputTex_;
GLuint oesTexId_;
```

这个整数不是像素数据。

它只是 GPU 资源编号。

```text
texId = 7
```

可以理解成：

```text
GPU 里第 7 号纹理对象。
```

你不能通过 `texId` 直接拿到像素。你只能用它告诉 GL：

```text
我要绑定这张纹理。
我要把这张纹理作为 shader 输入。
我要把绘制结果写进这张纹理。
```

### 4.2 `GL_TEXTURE_2D` 是什么

`GL_TEXTURE_2D` 是普通二维纹理类型。

它表示这张纹理按二维坐标访问：

```text
横向：u 或 s，范围通常是 0 到 1。
纵向：v 或 t，范围通常是 0 到 1。
```

例如：

```text
(0, 0) 表示左下角附近。
(1, 1) 表示右上角附近。
(0.5, 0.5) 表示中心附近。
```

在 Red Engine 里，`SourceNode` 的输出就是普通 `GL_TEXTURE_2D`：

```cpp
glBindTexture(GL_TEXTURE_2D, outputTex_);
glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0,
             GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
```

### 4.3 `glGenTextures`

```cpp
glGenTextures(1, &outputTex_);
```

作用：

```text
向 GL 申请 1 个纹理名称，也就是纹理编号。
```

注意：这一步只是拿编号，不等于已经有像素内容。

### 4.4 `glBindTexture`

```cpp
glBindTexture(GL_TEXTURE_2D, outputTex_);
```

作用：

```text
把 outputTex_ 绑定为当前 GL_TEXTURE_2D 纹理。
```

绑定之后，对 `GL_TEXTURE_2D` 的设置会作用到 `outputTex_`。

例如下面的 `glTexImage2D` 和 `glTexParameteri` 都是在设置这张纹理。

### 4.5 `glTexImage2D`

```cpp
glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0,
             GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
```

作用：

```text
给当前 2D 纹理分配一块 width x height 的 RGBA 存储。
```

最后一个参数是 `nullptr`，意思是：

```text
只分配空间，暂时不从 CPU 上传初始像素。
```

这正适合 `SourceNode`：

```text
它先创建一张空的输出纹理。
后面 GPU 会通过 FBO 把转换结果画进这张纹理。
```

### 4.6 `glTexParameteri`

```cpp
glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
```

作用：

```text
设置纹理被采样时的规则。
```

`GL_LINEAR` 表示：

```text
缩放时用线性插值，画面更平滑。
```

`GL_CLAMP_TO_EDGE` 表示：

```text
如果采样坐标超出边界，就使用边缘像素，不重复平铺。
```

视频渲染通常不希望画面边缘出现重复图案，所以会用 `CLAMP_TO_EDGE`。

---

## 5. Android Surface 链路：解码器如何把画面交给 GPU

视频解码器输出的是一帧帧画面。

问题是：解码后的画面放在哪里？

如果放到 CPU 内存：

```text
解码器 -> CPU 内存 -> 上传到 GPU -> 纹理
```

这会多一次或多次拷贝，性能差。

Red Engine 使用的是 Surface 模式：

```text
解码器 -> Surface / ANativeWindow -> BufferQueue -> SurfaceTexture -> OES 纹理
```

这条链路的目标是让解码结果直接进入 GPU 可采样的图像流。

### 5.1 `Surface`

`Surface` 是 Android 中给生产者写入图像的入口。

视频解码器、摄像头、播放器都可以把画面输出到 `Surface`。

### 5.2 `ANativeWindow`

`ANativeWindow` 是 native C/C++ 层对 `Surface` 的访问句柄。

项目里通过 JNI 创建 Java `Surface`，再转成 `ANativeWindow`：

```cpp
window_ = ANativeWindow_fromSurface(env, surface_);
```

这个 `window_` 会传给 `AMediaCodec_configure`：

```cpp
AMediaCodec_configure(codec_, format, outputSurface, nullptr, 0);
```

这里的 `outputSurface` 就是 `ANativeWindow*`。

### 5.3 `SurfaceTexture`

`SurfaceTexture` 可以接收来自图像流的帧，并把它们作为 OpenGL ES 外部纹理使用。

项目里这样创建：

```cpp
jobject localST = env->NewObject(stClass, stCtor, (jint)oesTexId_);
surfaceTexture_ = env->NewGlobalRef(localST);
```

等价于 Java/Kotlin：

```kotlin
val surfaceTexture = SurfaceTexture(oesTexId)
```

关键点是构造参数 `oesTexId`：

```text
告诉 SurfaceTexture：你收到的新帧，要关联到这张 OES 纹理。
```

### 5.4 `BufferQueue`

可以把 BufferQueue 理解为生产者和消费者之间的队列。

```text
生产者:
  AMediaCodec 解码器，把解码后的帧放进去。

消费者:
  SurfaceTexture，从里面取最新帧，让 GL 通过 OES 纹理采样。
```

应用代码通常不直接操作 BufferQueue，但要理解它的存在。

当项目调用：

```cpp
AMediaCodec_releaseOutputBuffer(codec_, bufferIndex, true);
```

并且 decoder 配置过 output surface 时，`true` 表示：

```text
把这个解码输出 buffer 渲染到 output surface。
```

随后 SurfaceTexture 会收到新帧可用的通知。

### 5.5 `updateTexImage`

项目里每帧会调用：

```cpp
stHelper_.updateTexImage(env);
```

内部是：

```cpp
env->CallVoidMethod(surfaceTexture_, updateTexImageMethod_);
```

作用：

```text
让 SurfaceTexture 更新到图像流中的最新帧。
```

调用之后，前面创建的 OES 纹理就指向最新可采样的画面。

注意：这不是普通意义上的“CPU 拷贝像素”。它是在 Android 图形系统和 GL 之间更新当前可采样的图像内容。

### 5.6 `getTransformMatrix`

项目里 `SourceNode::execute()` 会调用：

```cpp
stHelper_->getTransformMatrix(env_, texMatrix);
```

作用：

```text
拿到 SurfaceTexture 给出的纹理坐标变换矩阵。
```

为什么需要矩阵？

因为解码器输出的图像可能带有裁剪、旋转、翻转或硬件布局差异。

不能假设：

```text
纹理坐标 (0, 0) 一定对应画面左下角。
```

所以采样 OES 纹理前，要用这个矩阵修正纹理坐标。

---

## 6. OES 纹理：硬解码输出的特殊纹理

普通 2D 纹理用：

```cpp
GL_TEXTURE_2D
```

SurfaceTexture 使用的是：

```cpp
GL_TEXTURE_EXTERNAL_OES
```

这是 OpenGL ES 的外部图像纹理类型。

### 6.1 为什么不能直接用普通 2D 纹理

硬解码器输出的画面可能不是标准 RGBA：

```text
可能是 YUV。
可能是芯片厂商私有布局。
可能在特殊图形缓冲区中。
```

CPU 不应该把它读出来再上传。

OES 纹理提供了一个接口：

```text
让 shader 可以采样外部图像流。
```

### 6.2 OES 纹理的限制

OES 纹理不是普通 `GL_TEXTURE_2D`。

常见限制：

```text
绑定时必须用 GL_TEXTURE_EXTERNAL_OES。
shader 里不能用 sampler2D，要用 samplerExternalOES。
通常不能作为 FBO 的颜色输出附件。
纹理坐标需要使用 SurfaceTexture 的 transform matrix。
```

所以 Red Engine 不能把 OES 纹理直接交给所有后续节点。

它先通过 `SourceNode` 转成普通 2D 纹理。

---

## 7. Shader：GPU 上运行的小程序

Shader 是运行在 GPU 上的小程序。

视频渲染里常用两类：

```text
Vertex Shader:
  处理顶点。
  决定一个矩形的四个角在哪里。

Fragment Shader:
  处理像素或片段。
  决定屏幕上每个像素是什么颜色。
```

### 7.1 为什么显示视频也要 shader

GPU 不会自动知道：

```text
你要把哪张纹理画到哪里。
你要怎么处理 OES 纹理坐标。
你要不要混合两张纹理。
你要不要加滤镜。
```

这些规则都要写在 shader 里。

### 7.2 SourceNode 的 OES shader

`SourceNode` 的顶点 shader：

```glsl
layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexCoord;
uniform mat4 uTexMatrix;
out vec2 vTexCoord;

void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
    vTexCoord = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
}
```

它做两件事：

```text
把全屏矩形的顶点放到屏幕或 FBO 对应位置。
用 SurfaceTexture 矩阵修正纹理坐标。
```

`SourceNode` 的片段 shader：

```glsl
#extension GL_OES_EGL_image_external_essl3 : require
uniform samplerExternalOES uTexture;

void main() {
    fragColor = texture(uTexture, vTexCoord);
}
```

它做一件事：

```text
从 OES 纹理采样，输出 RGBA 颜色。
```

### 7.3 `glUseProgram`

项目封装在 `ShaderProgram::use()` 中：

```cpp
glUseProgram(program_);
```

作用：

```text
指定接下来绘制时用哪个 shader 程序。
```

如果没有 `glUseProgram`，GPU 不知道该用哪套顶点和像素计算规则。

### 7.4 `uniform`

shader 里的 `uniform` 是 CPU 传给 GPU 程序的参数。

例如：

```cpp
oesShader_.setInt("uTexture", 0);
oesShader_.setMatrix4("uTexMatrix", texMatrix);
```

含义：

```text
uTexture = 0:
  shader 从 0 号纹理单元采样。

uTexMatrix = texMatrix:
  shader 用这套矩阵修正纹理坐标。
```

---

## 8. 全屏四边形、VBO、VAO

显示一张纹理，本质上不是“贴到屏幕”这么简单。

OpenGL 的绘制方式是：

```text
先画几何形状。
再在这个形状上采样纹理。
```

视频画面通常画在一个铺满目标区域的矩形上。

项目里这个矩形叫 `FullscreenQuad`。

### 8.1 顶点坐标和纹理坐标

一个全屏矩形需要 4 个顶点：

```cpp
// x,    y,    u,    v
-1.0f, -1.0f, 0.0f, 0.0f,
 1.0f, -1.0f, 1.0f, 0.0f,
-1.0f,  1.0f, 0.0f, 1.0f,
 1.0f,  1.0f, 1.0f, 1.0f,
```

`x, y` 是顶点位置：

```text
-1 到 1 是 OpenGL 的标准化设备坐标。
(-1, -1) 到 (1, 1) 刚好覆盖整个绘制区域。
```

`u, v` 是纹理坐标：

```text
0 到 1 表示从纹理的一边采样到另一边。
```

### 8.2 VBO

VBO 是 Vertex Buffer Object。

它的作用：

```text
把顶点数据存到 GPU 侧缓冲区。
```

项目里：

```cpp
glGenBuffers(1, &vbo_);
glBindBuffer(GL_ARRAY_BUFFER, vbo_);
glBufferData(GL_ARRAY_BUFFER, sizeof(kQuadVertices), kQuadVertices, GL_STATIC_DRAW);
```

含义：

```text
创建一个 GPU buffer。
绑定成当前顶点数据 buffer。
把全屏矩形的顶点数据上传进去。
```

### 8.3 VAO

VAO 是 Vertex Array Object。

它的作用：

```text
记录 GPU 应该如何解释 VBO 里的数据。
```

VBO 里只是一串 float：

```text
x0 y0 u0 v0 x1 y1 u1 v1 ...
```

GPU 不知道哪些是位置，哪些是纹理坐标。

所以要告诉它：

```cpp
glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE,
                      4 * sizeof(float), (void*)0);
```

含义：

```text
shader 的 location 0 读取位置。
每个顶点读 2 个 float。
每个顶点总跨度是 4 个 float。
从每个顶点的第 0 个 float 开始读。
```

再告诉它：

```cpp
glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE,
                      4 * sizeof(float), (void*)(2 * sizeof(float)));
```

含义：

```text
shader 的 location 1 读取纹理坐标。
每个顶点读 2 个 float。
每个顶点总跨度仍然是 4 个 float。
从每个顶点的第 2 个 float 开始读。
```

以后绘制时只需要：

```cpp
glBindVertexArray(vao_);
glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
```

GPU 就知道：

```text
画 4 个顶点组成的矩形。
每个顶点的位置和纹理坐标从哪里读。
```

---

## 9. FBO：画到纹理，而不是直接画到屏幕

默认情况下，OpenGL 会画到默认 framebuffer。

在 Android 的窗口渲染里，可以把默认 framebuffer 理解为：

```text
当前 EGLSurface 对应的屏幕后缓冲。
```

但很多时候我们不想直接上屏。

例如 `SourceNode` 的目标是：

```text
把 OES 纹理转换成普通 2D 纹理。
```

这一步只是中间结果，不应该直接显示。

所以需要 FBO。

### 9.1 FBO 的作用

FBO 是 Framebuffer Object。

它允许你：

```text
把绘制结果写入一张纹理。
```

`SourceNode` 创建 FBO：

```cpp
glGenFramebuffers(1, &fbo_);
glBindFramebuffer(GL_FRAMEBUFFER, fbo_);
glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                       GL_TEXTURE_2D, outputTex_, 0);
```

含义：

```text
创建一个 framebuffer。
把它绑定为当前 framebuffer。
把 outputTex_ 挂到这个 framebuffer 的颜色输出位置。
```

之后只要：

```cpp
glBindFramebuffer(GL_FRAMEBUFFER, fbo_);
```

再绘制，结果就会写进 `outputTex_`。

如果：

```cpp
glBindFramebuffer(GL_FRAMEBUFFER, 0);
```

则表示切回默认 framebuffer，也就是画到屏幕后缓冲。

### 9.2 为什么 SourceNode 必须用 FBO

`SourceNode` 做的是：

```text
输入：OES 纹理。
处理：用 shader 采样 OES，并应用 transform matrix。
输出：普通 GL_TEXTURE_2D。
```

要得到一张“输出纹理”，就必须把 shader 的结果画进某张纹理。

FBO 正是完成这件事的工具。

---

## 10. 一次 GL 绘制到底发生了什么

以 `SourceNode::execute()` 为例。

代码主干：

```cpp
glBindFramebuffer(GL_FRAMEBUFFER, fbo_);
glViewport(0, 0, outputWidth, outputHeight);

oesShader_.use();

glActiveTexture(GL_TEXTURE0);
glBindTexture(GL_TEXTURE_EXTERNAL_OES, stHelper_->oesTexId());
oesShader_.setInt("uTexture", 0);
oesShader_.setMatrix4("uTexMatrix", texMatrix);

quad_.draw();
```

逐步解释：

```text
1. glBindFramebuffer:
   这次绘制写入 SourceNode 的 FBO。
   也就是写入 outputTex_。

2. glViewport:
   设置绘制区域大小。
   SourceNode 用视频宽高。

3. oesShader_.use:
   使用能采样 OES 纹理的 shader。

4. glActiveTexture + glBindTexture:
   把 SurfaceTexture 的 OES 纹理绑定到 0 号纹理单元。

5. setInt("uTexture", 0):
   告诉 shader 从 0 号纹理单元采样。

6. setMatrix4("uTexMatrix", texMatrix):
   告诉 shader 如何修正 OES 纹理坐标。

7. quad_.draw:
   真正触发绘制。
   GPU 对全屏矩形覆盖的每个像素运行 fragment shader。
```

绘制结束后：

```text
outputTex_ 中保存了转换后的普通 2D RGBA 画面。
```

---

# 第二层：Red Engine 的真实播放流程

## 11. 关键类地图

当前代码中，主要入口不是旧版单体 `RenderEngine::renderThreadFunc`，而是下面这条链：

```text
Kotlin RenderEngine / RenderEngineView
  -> JNI
  -> C++ RenderEngine
  -> PlaybackSession
  -> VideoTrackPipeline
  -> RenderGraphBuilder
  -> RenderNode 子类
```

关键职责：

| 类 | 职责 |
|---|---|
| `RenderEngine` | native 引擎门面，转发 play、pause、seek、setSurface 等请求 |
| `PlaybackSession` | 管理渲染线程、Surface 生命周期、播放循环、seek、音画同步 |
| `VideoTrackPipeline` | 管理单条视频轨道的 Demuxer、Decoder、SurfaceTexture、SourceNode |
| `Demuxer` | 用 FFmpeg 打开媒体文件，拆出视频包和音频包 |
| `HwDecoder` | 用 AMediaCodec 硬解码视频 |
| `SurfaceTextureHelper` | 创建 OES 纹理、SurfaceTexture、Surface、ANativeWindow |
| `RenderGraphBuilder` | 根据单轨或双轨创建渲染树连接 |
| `SourceNode` | 把 OES 纹理转换成普通 2D 纹理 |
| `BlendNode` | 可选，把主轨和叠加轨混合成一张纹理 |
| `OutputNode` | 把最终纹理画到屏幕 |

---

## 12. 渲染线程启动：先准备 EGL

`PlaybackSession` 构造时启动渲染线程：

```cpp
renderThread_ = std::thread(&PlaybackSession::renderThreadFunc, this);
```

渲染线程入口：

```cpp
void PlaybackSession::renderThreadFunc() {
    RenderLoopContext ctx;
    if (!attachThreadAndInitEgl(ctx)) {
        return;
    }

    while (running_.load()) {
        if (handleSurfaceLifecycle(ctx)) continue;
        if (handleSourceLifecycle(ctx)) continue;
        if (handlePendingFastSeek(ctx)) continue;
        if (handlePendingExactSeek(ctx)) continue;
        if (handleIdleState(ctx)) continue;
        if (handleTimelineEof(ctx)) continue;

        processPlaybackTick(ctx);
    }

    teardownRenderThread(ctx);
}
```

第一步是：

```cpp
attachThreadAndInitEgl(ctx)
```

里面做两件事：

```text
1. AttachCurrentThread:
   把 native 渲染线程挂到 JVM。
   因为后面要调用 Java SurfaceTexture 方法。

2. eglCore_.init:
   初始化 EGLDisplay、EGLConfig、EGLContext。
```

此时只有 context，还没有窗口 surface。

所以 GL 环境基本准备好了，但还不知道最终画到哪里。

---

## 13. Surface 就绪：创建 EGLSurface 并 makeCurrent

Android UI 层创建 `SurfaceView` 后，会把 `Surface` 传到 native。

native 层最终调用：

```cpp
PlaybackSession::setSurface(ANativeWindow* window)
```

它保存 `window_`，并设置：

```cpp
windowChanged_.store(true);
```

渲染线程下一轮会进入：

```cpp
handleSurfaceLifecycle(ctx)
```

核心代码：

```cpp
eglSurface_ = eglCore_.createWindowSurface(window_);
eglCore_.makeCurrent(eglSurface_);
ctx.surfaceWidth = ANativeWindow_getWidth(window_);
ctx.surfaceHeight = ANativeWindow_getHeight(window_);
glViewport(0, 0, ctx.surfaceWidth, ctx.surfaceHeight);
```

这一步结束后：

```text
EGLContext 已创建。
EGLSurface 已创建。
当前渲染线程已经 makeCurrent。
GL 调用可以创建纹理、FBO、shader。
屏幕输出尺寸已记录。
```

这就是为什么解码管线初始化必须等 Surface 就绪：

```text
VideoTrackPipeline::init 会创建 OES 纹理、SourceNode、FBO、shader。
这些都需要当前线程已有有效 GL context。
```

---

## 14. 设置视频源：初始化解码和渲染管线

用户设置视频源或时间线后，`PlaybackSession` 会设置：

```cpp
videoSourceChanged_.store(true);
```

渲染线程进入：

```cpp
handleSourceLifecycle(ctx)
```

核心流程：

```cpp
if (initDecodePipeline(ctx.env)) {
    buildRenderTree(ctx.surfaceWidth, ctx.surfaceHeight);
    ctx.syncController.reset();
    ctx.eof = false;
}
```

也就是：

```text
先初始化解码管线。
再构建渲染树。
```

---

## 15. VideoTrackPipeline：单条视频轨道如何建立

主轨初始化在：

```cpp
VideoTrackPipeline::init(JNIEnv* env)
```

### 15.1 打开媒体文件

```cpp
demuxer_.open(firstClip.sourcePath.c_str())
```

`Demuxer` 用 FFmpeg 做这些事：

```text
打开文件。
读取媒体流信息。
找到最佳视频流。
找到音频流。
准备 bitstream filter。
```

MP4 是容器，不是纯视频帧数组。

里面可能有：

```text
视频流：H.264 / H.265 等压缩数据。
音频流：AAC 等压缩数据。
元数据：时长、分辨率、时间基等。
```

Demuxer 的产物包括：

```text
videoCodecParameters:
  给 HwDecoder 配置解码器。

videoWidth / videoHeight:
  给 SourceNode 创建输出纹理。

videoTimeBase:
  把 packet/frame 的 PTS 转成微秒。

readPacket:
  播放时持续读取音视频 packet。
```

### 15.2 创建 SurfaceTextureHelper

```cpp
stHelper_.create(env)
```

它做五件事：

```text
1. glGenTextures 创建 OES 纹理编号。
2. glBindTexture(GL_TEXTURE_EXTERNAL_OES, oesTexId_) 设置 OES 纹理参数。
3. 创建 Java SurfaceTexture(oesTexId_)。
4. 用 SurfaceTexture 创建 Java Surface。
5. 用 Surface 转成 ANativeWindow。
```

这一步建立了：

```text
AMediaCodec 输出入口:
  ANativeWindow

OpenGL 采样入口:
  oesTexId_
```

也就是：

```text
解码器往 ANativeWindow 输出。
SurfaceTexture 接收帧。
GPU 通过 OES 纹理采样这帧。
```

### 15.3 初始化硬解码器

```cpp
decoder_.init(demuxer_.videoCodecParameters(), stHelper_.nativeWindow())
```

`HwDecoder::init` 做这些事：

```text
根据 codec_id 选择 MIME。
创建 AMediaCodec decoder。
创建 AMediaFormat。
设置 MIME、width、height、csd。
用 outputSurface 配置 decoder。
启动 decoder。
```

关键代码：

```cpp
AMediaCodec_configure(codec_, format, outputSurface, nullptr, 0);
```

这里传入了 `outputSurface`。

所以 decoder 进入 Surface 模式：

```text
解码后的画面不返回 CPU 像素数组。
而是输出到 SurfaceTexture 链路。
```

### 15.4 创建 SourceNode

```cpp
sourceNode_ = std::make_unique<SourceNode>(&stHelper_, env);
sourceNode_->initGL(w, h);
```

`SourceNode` 保存 `SurfaceTextureHelper`，因为每帧执行时要用：

```text
stHelper_->oesTexId():
  采样 OES 纹理。

stHelper_->getTransformMatrix():
  获取坐标修正矩阵。
```

`initGL(w, h)` 会创建：

```text
OES shader。
FullscreenQuad。
普通 2D 输出纹理 outputTex_。
FBO，把 outputTex_ 挂为颜色输出。
```

这时 SourceNode 已经具备能力：

```text
输入 OES 纹理。
输出普通 2D 纹理。
```

---

## 16. 构建 Render Tree

解码管线初始化后，`PlaybackSession` 调用：

```cpp
buildRenderTree(ctx.surfaceWidth, ctx.surfaceHeight)
```

内部委托给：

```cpp
RenderGraphBuilder::rebuild(...)
```

### 16.1 单轨播放

如果只有主轨：

```cpp
outputNode_->inputs.push_back(primarySource);
```

树结构是：

```text
OutputNode
  -> SourceNode
```

执行方向是从根节点开始：

```text
调用 OutputNode::execute
  -> OutputNode 调用 SourceNode::execute
  -> SourceNode 返回 outputTex_
  -> OutputNode 把 outputTex_ 画到屏幕
```

注意：不是 SourceNode 主动推给 OutputNode。

而是父节点执行时向子节点要结果。

### 16.2 双轨叠加

如果有 overlay：

```cpp
blendNode->inputs.push_back(primarySource);
blendNode->inputs.push_back(overlaySource);
outputNode_->inputs.push_back(blendNode.get());
```

树结构是：

```text
OutputNode
  -> BlendNode
       -> Primary SourceNode
       -> Overlay SourceNode
```

执行时：

```text
OutputNode 要最终纹理。
BlendNode 先执行两个 SourceNode。
两个 SourceNode 分别返回普通 2D 纹理。
BlendNode 用 alpha 混合，输出自己的 outputTex_。
OutputNode 把 BlendNode 的 outputTex_ 画到屏幕。
```

### 16.3 为什么要设计 Render Tree

Render Tree 的核心抽象是：

```cpp
virtual GLuint execute(int64_t timelinePositionUs) = 0;
std::vector<RenderNode*> inputs;
```

每个节点都遵守同一规则：

```text
执行自己之前，可以先执行 input 节点。
执行完成后，返回一张纹理 ID。
```

这样后续可以自然扩展：

```text
SourceNode -> FxNode -> BlendNode -> SubtitleNode -> OutputNode
```

每个节点只关心：

```text
我的输入纹理是谁。
我要用什么 shader 处理。
我的输出纹理是什么。
```

---

## 17. 播放循环：一帧如何被处理

当用户调用 `play()` 后：

```cpp
playing_.store(true);
```

渲染线程在条件满足时进入：

```cpp
processPlaybackTick(ctx)
```

每次 tick 处理一帧或尝试处理一帧。

流程：

```cpp
feedAndDecodePrimaryFrame(ctx, frameCtx, perf)
updateOverlayForPlayback(ctx, frameCtx.globalPosUs)
syncAndPresent(ctx, frameCtx, perf)
emitDiagnostics(...)
```

下面只看主画面路径。

### 17.1 喂数据给解码器

```cpp
primaryTrack_->pumpAvailablePackets();
```

`VideoTrackPipeline::pumpAvailablePackets` 会：

```text
先尝试把上次没塞进去的 pending packet 塞进 decoder。
然后从 Demuxer 继续读 packet。
遇到音频 packet 就缓存给音频。
遇到视频 packet 就尝试 queue 到 decoder。
decoder 暂时收不下，就保存为 pending packet。
遇到 EOF，就 queueEOS。
```

这里的 packet 仍然是压缩数据，例如 H.264 码流片段。

### 17.2 从解码器取出一帧

```cpp
primaryTrack_->dequeueFrame(frameCtx.decodedFrame, 30000)
```

内部调用：

```cpp
AMediaCodec_dequeueOutputBuffer(codec_, &info, timeoutUs);
```

含义：

```text
问 decoder：有没有解码完成的输出 buffer？
```

如果有，得到：

```text
bufferIndex:
  decoder 输出 buffer 编号。

pts:
  这帧应该展示的时间戳。
```

### 17.3 判断 trim 和时间线位置

代码会把 decoder 的 PTS 转成微秒：

```cpp
frameCtx.framePtsUs = av_rescale_q(frameCtx.decodedFrame.pts, tb, {1, 1000000});
```

然后判断：

```text
是否早于当前 clip 的 trimIn。
是否超过当前 clip 的 trimOut。
```

如果有效，再映射到全局时间线：

```cpp
frameCtx.globalPosUs = primaryTrack_->mapSourcePtsToTimelineUs(frameCtx.framePtsUs);
```

### 17.4 释放 decoder 输出到 SurfaceTexture

```cpp
primaryTrack_->releaseFrame(frameCtx.decodedFrame.bufferIndex, true);
```

内部：

```cpp
AMediaCodec_releaseOutputBuffer(codec_, bufferIndex, true);
```

`true` 的意义很关键：

```text
把这帧输出到 decoder 配置过的 output surface。
```

这一步之后，画面进入 SurfaceTexture 链路。

### 17.5 更新 OES 纹理

```cpp
primaryTrack_->consumeRenderedFrame(ctx.env, 50);
```

内部：

```cpp
stHelper_.waitForFrame(timeoutMs);
stHelper_.consumeFrameAvailable();
stHelper_.updateTexImage(env);
```

含义：

```text
等待 SurfaceTexture 收到新帧通知。
消费这个通知。
调用 updateTexImage，让 OES 纹理更新到最新帧。
```

到这里，GPU 已经可以通过 `stHelper_->oesTexId()` 采样这帧视频画面。

---

## 18. 音画同步和是否渲染

`syncAndPresent` 会先询问音画同步控制器：

```cpp
VideoSyncDecision preparedDecision = ctx.syncController.prepareFrame(...);
```

它会根据：

```text
当前视频帧 PTS。
音频时钟。
帧时长。
当前系统时间。
```

决定：

```text
是否等待一会儿。
是否应该渲染这一帧。
是否需要丢帧追赶。
```

如果需要等待：

```cpp
usleep((useconds_t)preparedDecision.sleepUs);
```

最终如果：

```cpp
finalizedDecision.shouldRender
```

才会真正执行渲染树。

---

## 19. 执行 Render Tree 并上屏

真正执行渲染树：

```cpp
outputNode->outputWidth = ctx.surfaceWidth;
outputNode->outputHeight = ctx.surfaceHeight;
outputNode->execute(frameCtx.framePtsUs);
```

### 19.1 `OutputNode::execute`

```cpp
GLuint inputTex = inputs[0]->execute(timelinePositionUs);
```

如果单轨，`inputs[0]` 是 `SourceNode`。

所以先执行 `SourceNode::execute()`。

### 19.2 `SourceNode::execute`

核心结果：

```text
OES 纹理 -> outputTex_
```

它绑定自己的 FBO，采样 OES 纹理，画全屏矩形。

结果写入 `SourceNode::outputTex_`。

返回：

```cpp
return outputTex_;
```

### 19.3 `OutputNode` 画到默认 framebuffer

拿到 `inputTex` 后：

```cpp
glBindFramebuffer(GL_FRAMEBUFFER, 0);
glViewport(0, 0, outputWidth, outputHeight);

passthrough_.use();
glActiveTexture(GL_TEXTURE0);
glBindTexture(GL_TEXTURE_2D, inputTex);
passthrough_.setInt("uTexture", 0);

quad_.draw();
```

含义：

```text
切回默认 framebuffer。
使用普通 sampler2D shader。
把最终 2D 纹理画到屏幕后缓冲。
```

此时画已经画完，但用户还不一定看到。

因为它在后缓冲里。

### 19.4 `eglSwapBuffers`

最后：

```cpp
eglCore_.swapBuffers(eglSurface_);
```

内部：

```cpp
eglSwapBuffers(display_, surface);
```

作用：

```text
把后缓冲提交给显示系统。
```

可以理解为：

```text
刚画好的这一帧正式交给屏幕显示。
```

---

# 第三层：完整一帧复盘

现在把整条链合起来。

```text
1. Demuxer 从 MP4 读 packet。
   packet 是压缩视频数据，还不是像素。

2. HwDecoder 把 packet queue 到 AMediaCodec。
   decoder 在硬件中解码。

3. PlaybackSession dequeue 到一个 decoded output buffer。
   拿到 bufferIndex 和 PTS。

4. releaseOutputBuffer(bufferIndex, true)。
   这帧被送到 decoder 的 output surface。

5. SurfaceTexture 收到新帧。
   OnFrameAvailableListener 设置 frameAvailable 标记。

6. updateTexImage。
   OES 纹理更新到最新帧。

7. SourceNode 执行。
   采样 OES 纹理，应用 transform matrix。
   画到 FBO，结果进入普通 2D 纹理 outputTex_。

8. 如果有 overlay，BlendNode 执行。
   分别拿主轨和叠加轨纹理。
   用 alpha 混合成新的 2D 纹理。

9. OutputNode 执行。
   把最终 2D 纹理画到默认 framebuffer。

10. eglSwapBuffers。
    后缓冲提交到屏幕。
    用户看到这一帧。
```

用一张图表示：

```text
MP4
  |
  | Demuxer::readPacket
  v
AVPacket
  |
  | HwDecoder::queuePacket
  v
AMediaCodec
  |
  | dequeueOutputBuffer
  | releaseOutputBuffer(true)
  v
Surface / ANativeWindow
  |
  v
SurfaceTexture
  |
  | updateTexImage
  v
OES Texture
  |
  | SourceNode: OES shader + FBO
  v
2D Texture
  |
  | BlendNode 可选
  v
Final 2D Texture
  |
  | OutputNode: passthrough shader
  v
Default Framebuffer
  |
  | eglSwapBuffers
  v
Screen
```

---

# 关键代码入口索引

建议按这个顺序读代码：

```text
1. render-engine/src/main/cpp/engine/RenderEngine.cpp
   看 native 门面如何转发到 PlaybackSession。

2. render-engine/src/main/cpp/engine/PlaybackSession.cpp
   看渲染线程、Surface 生命周期、播放循环。

3. render-engine/src/main/cpp/gl/EglCore.cpp
   看 EGL 初始化、makeCurrent、swapBuffers。

4. render-engine/src/main/cpp/pipeline/VideoTrackPipeline.cpp
   看单轨视频解码管线如何创建和驱动。

5. render-engine/src/main/cpp/decode/SurfaceTextureHelper.cpp
   看 OES 纹理、SurfaceTexture、Surface、ANativeWindow 如何连起来。

6. render-engine/src/main/cpp/decode/HwDecoder.cpp
   看 AMediaCodec 如何配置 Surface 模式。

7. render-engine/src/main/cpp/core/SourceNode.cpp
   看 OES 纹理如何转成普通 2D 纹理。

8. render-engine/src/main/cpp/render/RenderGraphBuilder.cpp
   看 Render Tree 如何连接。

9. render-engine/src/main/cpp/core/OutputNode.cpp
   看最终纹理如何画到屏幕。
```

---

# 概念速查表

| 概念 | 一句话解释 |
|---|---|
| EGL | Android 显示系统和 OpenGL ES 之间的桥 |
| EGLDisplay | EGL 对显示设备的连接句柄 |
| EGLContext | OpenGL ES 状态集合 |
| EGLSurface | OpenGL ES 的绘制目标 |
| makeCurrent | 把 context 和 surface 绑定到当前线程 |
| Texture | GPU 可采样的图像资源 |
| `GLuint` | GPU 资源编号，不是像素数据 |
| `GL_TEXTURE_2D` | 普通二维纹理 |
| `GL_TEXTURE_EXTERNAL_OES` | SurfaceTexture 使用的外部图像纹理 |
| Surface | Android 图像生产者的输出入口 |
| ANativeWindow | native 层访问 Surface 的句柄 |
| SurfaceTexture | 把图像流接到 OpenGL ES 纹理 |
| BufferQueue | 生产者和消费者之间的图像缓冲队列 |
| Shader | GPU 上运行的小程序 |
| Vertex Shader | 处理顶点位置 |
| Fragment Shader | 计算像素颜色 |
| VBO | 存顶点数据的 GPU buffer |
| VAO | 记录顶点数据如何解释 |
| FBO | 让 GPU 画到纹理的 framebuffer |
| PTS | 帧应该展示的时间戳 |
| Render Tree | 用节点串联多步纹理处理 |

---

# 参考资料

本文概念核对过以下官方或规范资料：

- Android `SurfaceTexture` API 文档：说明 SurfaceTexture 可从视频解码等图像流捕获帧，`updateTexImage()` 更新纹理内容，OES 纹理需要 `GL_TEXTURE_EXTERNAL_OES` 和 `samplerExternalOES`。
- Android NDK Media 文档：说明 `AMediaCodec_releaseOutputBuffer(..., render=true)` 在配置 output surface 后可把 buffer 渲染到 output surface。
- Khronos OpenGL Wiki `glBindTexture`：说明绑定纹理后，针对该 target 的操作会影响当前绑定纹理。
- Khronos OpenGL Wiki `glTexImage2D`：说明该函数定义二维纹理图像，`data == nullptr` 时可只分配纹理存储。
- Khronos `GL_OES_EGL_image_external` / `GL_OES_EGL_image_external_essl3` 扩展文档：说明外部图像纹理和 ESSL3 中 `samplerExternalOES` 的使用。
