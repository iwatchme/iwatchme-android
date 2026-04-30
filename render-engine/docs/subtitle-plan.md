# 字幕渲染实现方案 (FreeType + GL Texture)

## Context

render-engine 已完成视频播放、音画同步、多片段时间线、多轨道叠加等功能。现在需要添加字幕渲染支持：解析 SRT 字幕文件，使用 FreeType 将文本渲染为 GL 纹理，通过渲染树合成到视频画面底部。

**核心思路**：新增 `SubtitleNode`（RenderNode 子类），插入渲染树中视频内容节点和 OutputNode 之间。SubtitleNode 持有 FreeType 文本渲染器和 SRT 字幕数据，每帧根据 `timelinePositionUs` 查找当前字幕文本，仅在文本变化时重新渲染纹理，然后通过着色器将字幕纹理合成到视频画面底部。

渲染树变化：
```
无字幕: OutputNode → [BlendNode →] SourceNode
有字幕: OutputNode → SubtitleNode → [BlendNode →] SourceNode
```

---

## 1. FreeType 集成

### 预构建库目录结构

沿用 FFmpeg 的 prebuilt 模式：

```
iwatchme-android/
  freetype_library/
    android/
      arm64-v8a/
        include/
          ft2build.h
          freetype/       (FreeType headers)
        lib/
          libfreetype.so
```

FreeType 需从源码交叉编译：`--host=aarch64-linux-android --without-harfbuzz --without-bzip2 --without-png`（最小依赖）。

### CMakeLists.txt 变更

`render-engine/CMakeLists.txt`:

```cmake
# 在 FFmpeg 块之后添加
set(FREETYPE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../freetype_library/android)
add_library(freetype SHARED IMPORTED)
set_target_properties(freetype PROPERTIES
    IMPORTED_LOCATION ${FREETYPE_DIR}/${FFMPEG_ABI}/lib/libfreetype.so)
include_directories(${FREETYPE_DIR}/${FFMPEG_ABI}/include)
```

SOURCES 新增：
```cmake
    src/main/cpp/subtitle/SrtParser.cpp
    src/main/cpp/subtitle/TextRenderer.cpp
    src/main/cpp/core/SubtitleNode.cpp
```

target_include_directories 新增：
```cmake
    src/main/cpp/subtitle
```

target_link_libraries 新增 `freetype`。

### build.gradle.kts 变更

`render-engine/build.gradle.kts` 第 28-30 行：

```kotlin
sourceSets.getByName("main") {
    jniLibs.srcDirs(
        "../ffmpeg_library/android/libs",
        "../freetype_library/android/libs"
    )
}
```

---

## 2. SRT 解析器

### 数据模型

**新文件**: `src/main/cpp/subtitle/SrtParser.h`

```cpp
struct SubtitleEntry {
    int64_t startUs;   // 起始时间（微秒）
    int64_t endUs;     // 结束时间（微秒）
    std::string text;  // 字幕文本（可含换行）
};

class SrtParser {
public:
    static bool parseFile(const std::string& filePath,
                          std::vector<SubtitleEntry>& entries);
private:
    static int64_t parseTimestamp(const std::string& ts);
    // "HH:MM:SS,mmm" → microseconds
};

class SubtitleTrack {
public:
    void load(const std::string& srtPath);
    const std::string& textAt(int64_t timelinePositionUs) const;
    // 二分查找 O(log n)，返回 startUs <= pos < endUs 的条目文本
    bool isLoaded() const;
private:
    std::vector<SubtitleEntry> entries_;
    bool loaded_ = false;
};
```

**新文件**: `src/main/cpp/subtitle/SrtParser.cpp`

解析逻辑：
- 状态机：`EXPECT_INDEX → EXPECT_TIME → READING_TEXT`，空行分隔条目
- 处理 UTF-8 BOM（`\xEF\xBB\xBF`）和 `\r\n` 换行
- 输出按 `startUs` 排序的 `vector<SubtitleEntry>`
- `SubtitleTrack::textAt()` 使用 `std::lower_bound` 二分查找

---

## 3. TextRenderer（FreeType 文本渲染器）

**新文件**: `src/main/cpp/subtitle/TextRenderer.h`

```cpp
struct TextBitmap {
    std::vector<uint8_t> pixels;  // RGBA
    int width;
    int height;
};

class TextRenderer {
public:
    bool init(const std::string& fontPath, int fontSizePx);
    void release();

    // UTF-8 文本 → RGBA 位图。支持换行和 CJK 字符。
    bool renderText(const std::string& utf8Text, int maxWidth, TextBitmap& out);

    // 位图上传到 GL 纹理（复用已有纹理）
    void uploadToTexture(const TextBitmap& bitmap, GLuint& texId,
                         int& texW, int& texH);
private:
    static std::vector<uint32_t> decodeUtf8(const std::string& utf8);
    FT_Library ftLibrary_ = nullptr;
    FT_Face ftFace_ = nullptr;
    int fontSizePx_ = 0;
};
```

**新文件**: `src/main/cpp/subtitle/TextRenderer.cpp`

关键实现：

1. **初始化**：`FT_Init_FreeType` → `FT_New_Face` → `FT_Set_Pixel_Sizes`
2. **UTF-8 解码**：标准 UTF-8 → codepoint 转换（必须支持 CJK 多字节字符）
3. **renderText 算法**：
   - 解码 UTF-8 → codepoints
   - 按 `\n` 分逻辑行，每行按 maxWidth 自动换行
   - CJK 字符（U+4E00-U+9FFF 等）按字符换行，Latin 按空格换行
   - 两遍渲染：第一遍计算总尺寸，第二遍填充像素
   - 透明背景 `(0,0,0,0)`，字幕文字白色 `(255,255,255,alpha)`
   - 文字阴影：先偏移 1-2px 渲染黑色文字，再渲染白色文字（提升可读性）
   - 使用 `FT_Load_Char(face, codepoint, FT_LOAD_RENDER)` 获取字形位图
4. **uploadToTexture**：`glTexImage2D(GL_RGBA)` 上传，LINEAR 过滤

---

## 4. SubtitleNode

**新文件**: `src/main/cpp/core/SubtitleNode.h`

```cpp
class SubtitleNode : public RenderNode {
public:
    ~SubtitleNode() override;
    GLuint execute(int64_t timelinePositionUs) override;

    bool initGL(int width, int height, const std::string& fontPath, int fontSizePx);
    void releaseGL();
    void loadSubtitles(const std::string& srtPath);
    void setEnabled(bool enabled) { enabled_ = enabled; }

private:
    bool enabled_ = true;
    SubtitleTrack subtitleTrack_;
    TextRenderer textRenderer_;
    std::string lastRenderedText_;  // 缓存：文本不变则复用纹理
    GLuint subtitleTex_ = 0;
    int subtitleTexWidth_ = 0, subtitleTexHeight_ = 0;

    ShaderProgram compositeShader_;
    FullscreenQuad quad_;
    GLuint fbo_ = 0;
    GLuint outputTex_ = 0;
};
```

**新文件**: `src/main/cpp/core/SubtitleNode.cpp`

**合成着色器**（将字幕纹理定位在画面底部居中）：

```glsl
// Fragment shader
#version 300 es
precision mediump float;
uniform sampler2D uBase;       // 视频画面
uniform sampler2D uSubtitle;   // 字幕纹理（RGBA，透明背景）
uniform vec4 uSubRect;         // 字幕区域 UV 坐标 (x, y, w, h)
in vec2 vTexCoord;
out vec4 fragColor;
void main() {
    vec4 base = texture(uBase, vTexCoord);
    vec2 subUv = (vTexCoord - uSubRect.xy) / uSubRect.zw;
    if (subUv.x >= 0.0 && subUv.x <= 1.0 && subUv.y >= 0.0 && subUv.y <= 1.0) {
        vec4 sub = texture(uSubtitle, vec2(subUv.x, 1.0 - subUv.y));
        fragColor = mix(base, vec4(sub.rgb, 1.0), sub.a);
    } else {
        fragColor = base;
    }
}
```

UV 坐标中 `(0,0)` 是画面左下角（FullscreenQuad 的 UV 布局），所以 `uSubRect.y` 取小值（如 0.05）即定位在底部。

**execute() 逻辑**：

```cpp
GLuint SubtitleNode::execute(int64_t timelinePositionUs) {
    GLuint baseTex = inputs[0]->execute(timelinePositionUs);
    if (baseTex == 0) return 0;
    if (!enabled_ || !subtitleTrack_.isLoaded()) return baseTex;

    const std::string& text = subtitleTrack_.textAt(timelinePositionUs);
    if (text.empty()) return baseTex;  // 当前时间无字幕 → 直通

    // 仅文本变化时重新渲染（同一字幕持续 2-5 秒，节省数十帧的渲染开销）
    if (text != lastRenderedText_) {
        TextBitmap bitmap;
        textRenderer_.renderText(text, (int)(outputWidth * 0.85f), bitmap);
        textRenderer_.uploadToTexture(bitmap, subtitleTex_, subtitleTexWidth_, subtitleTexHeight_);
        lastRenderedText_ = text;
    }
    if (subtitleTex_ == 0) return baseTex;

    // 计算底部居中位置
    float subW = (float)subtitleTexWidth_ / outputWidth;
    float subH = (float)subtitleTexHeight_ / outputHeight;
    float subX = (1.0f - subW) / 2.0f;
    float subY = 0.05f;  // 底部 5% 边距

    // FBO 合成
    glBindFramebuffer(GL_FRAMEBUFFER, fbo_);
    glViewport(0, 0, outputWidth, outputHeight);
    compositeShader_.use();

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, baseTex);
    compositeShader_.setInt("uBase", 0);

    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, subtitleTex_);
    compositeShader_.setInt("uSubtitle", 1);

    float subRect[4] = {subX, subY, subW, subH};
    glUniform4fv(compositeShader_.getUniformLocation("uSubRect"), 1, subRect);

    quad_.draw();

    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, 0);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, 0);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);

    return outputTex_;
}
```

---

## 5. RenderGraphBuilder 变更

**修改文件**: `render/RenderGraphBuilder.h` + `.cpp`

新增成员：
```cpp
std::unique_ptr<SubtitleNode> subtitleNode_;
std::string srtPath_, fontPath_;
int fontSizePx_ = 48;
bool subtitleEnabled_ = false;
```

新增方法：
```cpp
void setSubtitleConfig(const std::string& srtPath, const std::string& fontPath, int fontSizePx);
void setSubtitleEnabled(bool enabled);
SubtitleNode* subtitleNode() const;
```

`rebuild()` 修改 — 在视频合成链和 OutputNode 之间条件插入 SubtitleNode：

```cpp
void rebuild(...) {
    // Step 1: 构建视频合成链（不变）
    RenderNode* videoOutput = primarySource;
    if (overlaySource) {
        // 创建 BlendNode ...
        videoOutput = blendNode.get();
    }

    // Step 2: 条件插入 SubtitleNode
    if (subtitleEnabled_ && !srtPath_.empty()) {
        auto node = std::make_unique<SubtitleNode>();
        if (node->initGL(surfaceWidth, surfaceHeight, fontPath_, fontSizePx_)) {
            node->loadSubtitles(srtPath_);
            node->inputs.push_back(videoOutput);
            videoOutput = node.get();
            subtitleNode_ = std::move(node);
        }
    }

    // Step 3: 连接 OutputNode
    outputNode_->inputs.push_back(videoOutput);
}
```

---

## 6. PlaybackSession 集成

**修改文件**: `engine/PlaybackSession.h` + `.cpp`

新增成员：
```cpp
std::string subtitlePath_;
std::string fontPath_;
int subtitleFontSize_ = 48;
std::atomic<bool> subtitleEnabled_{false};
std::atomic<bool> subtitleConfigChanged_{false};
```

新增公开方法：
```cpp
void setSubtitle(const std::string& srtPath, const std::string& fontPath, int fontSizePx);
void setSubtitleEnabled(bool enabled);
```

`setSubtitle()` 存储配置并设置 `subtitleConfigChanged_`，render thread 检测到后重建渲染树：

```cpp
// 在 renderThreadFunc 中，handleSourceLifecycle 附近添加：
if (subtitleConfigChanged_.load() && pipelineInitialized_) {
    subtitleConfigChanged_.store(false);
    buildRenderTree(ctx.surfaceWidth, ctx.surfaceHeight);
}
```

`buildRenderTree()` 调用前将字幕配置传递给 RenderGraphBuilder：

```cpp
void PlaybackSession::buildRenderTree(int w, int h) {
    renderGraphBuilder_->setSubtitleConfig(subtitlePath_, fontPath_, subtitleFontSize_);
    renderGraphBuilder_->setSubtitleEnabled(subtitleEnabled_.load());
    renderGraphBuilder_->rebuild(primarySource, overlaySource, w, h, overlayAlpha);
}
```

**字体文件处理**：Android assets 不能直接被 native 文件 I/O 读取。Kotlin 层将 .ttf 从 assets 复制到 cacheDir，将缓存文件路径传递给 native。

---

## 7. RenderEngine + JNI 层

**修改文件**: `engine/RenderEngine.h` + `.cpp`

```cpp
void setSubtitle(const std::string& srtPath, const std::string& fontPath, int fontSizePx);
void setSubtitleEnabled(bool enabled);
// 委托给 session_->setSubtitle() / session_->setSubtitleEnabled()
```

**修改文件**: `render_engine_jni.cpp`

```cpp
extern "C" JNIEXPORT void JNICALL
Java_com_iwatchme_renderengine_RenderEngine_nativeSetSubtitle(
    JNIEnv* env, jobject, jlong handle, jstring srtPath, jstring fontPath, jint fontSizePx);

extern "C" JNIEXPORT void JNICALL
Java_com_iwatchme_renderengine_RenderEngine_nativeSetSubtitleEnabled(
    JNIEnv*, jobject, jlong handle, jboolean enabled);
```

**修改文件**: `RenderEngine.kt`

```kotlin
fun setSubtitle(srtPath: String, fontPath: String, fontSizePx: Int = 48) {
    if (nativeHandle != 0L) nativeSetSubtitle(nativeHandle, srtPath, fontPath, fontSizePx)
}
fun setSubtitleEnabled(enabled: Boolean) {
    if (nativeHandle != 0L) nativeSetSubtitleEnabled(nativeHandle, enabled)
}
private external fun nativeSetSubtitle(handle: Long, srtPath: String, fontPath: String, fontSizePx: Int)
private external fun nativeSetSubtitleEnabled(handle: Long, enabled: Boolean)
```

---

## 8. Demo Activity UI

**修改文件**: `app/.../RenderEngineDemoActivity.kt`

- 新增 `subtitlePickerLauncher`（`GetContent` + 复制到 cacheDir）
- 新增"+ Subtitle"按钮加载 .srt 文件
- 新增"Sub ON/OFF"切换按钮
- 捆绑 `NotoSansSC-Regular.ttf` 字体到 `app/src/main/assets/fonts/`（支持 CJK）
- 首次使用时从 assets 复制到 cacheDir

---

## 文件清单

### 新建 (6 个文件)

| 文件 | 说明 |
|------|------|
| `src/main/cpp/subtitle/SrtParser.h` | SRT 数据模型 + 解析器 + SubtitleTrack |
| `src/main/cpp/subtitle/SrtParser.cpp` | SRT 解析实现 + 二分查找 |
| `src/main/cpp/subtitle/TextRenderer.h` | FreeType 文本渲染器声明 |
| `src/main/cpp/subtitle/TextRenderer.cpp` | UTF-8 解码、字形渲染、位图生成、GL 上传 |
| `src/main/cpp/core/SubtitleNode.h` | 字幕合成节点声明 |
| `src/main/cpp/core/SubtitleNode.cpp` | SubtitleNode 实现 + 合成着色器 |

### 新增外部依赖

| 资源 | 说明 |
|------|------|
| `freetype_library/android/arm64-v8a/` | FreeType 预构建 .so + headers |
| `app/src/main/assets/fonts/NotoSansSC-Regular.ttf` | Noto Sans SC 字体（CJK 支持） |

### 修改 (8 个文件)

| 文件 | 变更 |
|------|------|
| `render-engine/CMakeLists.txt` | 添加 FreeType 库链接 + 3 个新源文件 + subtitle include 目录 |
| `render-engine/build.gradle.kts` | jniLibs.srcDirs 添加 freetype_library 路径 |
| `render/RenderGraphBuilder.h` | 新增 `subtitleNode_`、subtitle 配置方法 |
| `render/RenderGraphBuilder.cpp` | `rebuild()` 中条件插入 SubtitleNode |
| `engine/PlaybackSession.h` | 新增字幕状态成员和公开方法 |
| `engine/PlaybackSession.cpp` | `setSubtitle()`、render loop 处理 `subtitleConfigChanged_`、`buildRenderTree()` 传递字幕配置 |
| `engine/RenderEngine.h/.cpp` | 透传 `setSubtitle()` / `setSubtitleEnabled()` |
| `render_engine_jni.cpp` | 2 个新 JNI 函数 |
| `RenderEngine.kt` | 2 个新 Kotlin 方法 + 2 个 native 声明 |
| `RenderEngineDemoActivity.kt` | 字幕文件选择器 + 开关按钮 |

---

## 实施顺序

### Phase 1: FreeType 构建 + TextRenderer

1. 交叉编译 FreeType for arm64-v8a，放置到 `freetype_library/android/`
2. CMakeLists.txt + build.gradle.kts 链接 FreeType
3. 实现 `TextRenderer::init()` / `renderText()` / `uploadToTexture()`
4. **验证**：临时在 `initDecodePipeline` 中调用 `renderText("Hello 你好", ...)` 并 LOGI 位图尺寸

### Phase 2: SRT 解析器

1. 实现 `SrtParser::parseFile()` + `SubtitleTrack::load()` / `textAt()`
2. **验证**：解析测试 SRT 字符串，LOGI 输出解析结果（纯 C++ 无 Android 依赖，也可在 host 上测试）

### Phase 3: SubtitleNode

1. 实现 `SubtitleNode::initGL()` / `execute()` / `releaseGL()`
2. 编写合成着色器
3. **验证**：手动在 `buildRenderTree` 中构造 SubtitleNode，硬编码加载测试 SRT，确认字幕显示在画面上

### Phase 4: RenderGraphBuilder 集成

1. RenderGraphBuilder 添加 subtitle 配置方法
2. `rebuild()` 条件插入 SubtitleNode
3. **验证**：通过配置驱动字幕节点创建，log 输出渲染树结构

### Phase 5: PlaybackSession + RenderEngine + JNI

1. 全链路打通：PlaybackSession → RenderEngine → JNI → Kotlin
2. 处理 `subtitleConfigChanged_` 触发渲染树重建
3. **验证**：从 Kotlin 调用 `setSubtitle()`，确认字幕在播放中正确显示

### Phase 6: Demo UI + 端到端测试

1. Demo Activity 添加字幕文件选择器和开关按钮
2. 捆绑 NotoSansSC 字体
3. 端到端测试

---

## 验证策略

### 单元级测试
- **SrtParser**: 标准 SRT / UTF-8 BOM / Windows 换行 / 多行字幕 / CJK 文本 / 边界条件
- **SubtitleTrack::textAt()**: 首条目前 / 条目之间 / 最后条目后 / 精确起止边界
- **TextRenderer**: 已知文本渲染后位图尺寸是否合理

### 集成测试
- SubtitleNode 三条代码路径：无字幕→直通、有字幕且与上帧相同→复用纹理、字幕变化→重新渲染
- 渲染树连接验证（log 输出）

### 端到端视觉测试
- 准备 30 秒测试视频 + 对应 SRT（5-6 条字幕）
- 播放验证：字幕按正确时间出现/消失、底部居中定位、CJK 正确渲染、多行正确换行
- Seek 验证：跳转后立即显示正确字幕
- 开关验证：播放中切换字幕显示/隐藏
- 无字幕文件回归：无崩溃、无视觉异常

### 性能验证
- 字幕切换帧不应掉帧（FreeType 渲染数十字形 < 1ms）
- 字幕纹理内存开销：~2000x200 RGBA ≈ 1.6MB
- 利用现有 `emitDiagnostics` AVPERF 日志检查 `executeUs` 无尖峰

---

## 已知限制（本期不解决）

| 限制 | 后续路径 |
|------|----------|
| 字幕仅支持 SRT 格式 | 后续可添加 ASS/SSA 解析器 |
| 固定字体大小，无动态调整 | 可添加 `setSubtitleFontSize()` API |
| 无文字描边，仅投影阴影 | 可用多 pass FreeType 渲染描边 |
| 字幕位置固定底部居中 | 可添加 position uniform 支持自定义位置 |
| 字体文件需从 assets 手动复制 | 可改用 AAssetManager 直接读取 |
