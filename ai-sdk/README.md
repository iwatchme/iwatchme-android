# TTS SDK

🗣️ A modular, coroutine-based Text-to-Speech engine for Kotlin/Android.

---

[English](#english) | [中文](#中文)

---

## English

### Features

- Multi-engine support — register multiple TTS backends by source ID
- Coroutine-native — built on `kotlinx.coroutines`, fully async
- Connection pooling — bounded SDK pools with configurable concurrency
- Disk caching — automatic cache with pluggable eviction strategies (LRU, LargestFirst, or custom)
- Batch generation — parallel synthesis with progress callbacks
- Streaming preview — real-time `Flow<TtsEvent>` for audio preview
- Audio player — queue-based PCM player with play/pause/stop lifecycle

### Modules

```
tts-core       — Engine, cache, SDK pool, interfaces (Android AAR)
tts-player     — Audio player with queue and state management (JVM JAR)
tts-testing    — FakeTtsSdk and test utilities (JVM JAR)
```

### Installation

Add the GitLab Maven registry to your `build.gradle.kts`:

```kotlin
repositories {
    maven {
        url = uri("https://gitlab.com/api/v4/projects/<PROJECT_ID>/packages/maven")
    }
}
```

Then add dependencies:

```kotlin
dependencies {
    implementation("io.tts.sdk:tts-core:1.0.0")
    implementation("io.tts.sdk:tts-player:1.0.0")
    testImplementation("io.tts.sdk:tts-testing:1.0.0")
}
```

### Quick Start

```kotlin
// 1. Build the engine
val engine = TtsEngine.Builder()
    .sdk(source = 1, factory = { MyTtsSdkImpl() }, poolSize = 3)
    .cacheDir(File("/path/to/cache"))
    .cacheStrategy(LruCacheStrategy())       // or LargestFirstCacheStrategy()
    .build()

// 2. Batch generate
val items = listOf(
    TtsItem(text = "Hello world", source = 1),
    TtsItem(text = "Goodbye", source = 1),
)
val voice = TtsVoiceParams(voiceType = "default")

engine.generate(items, voice) { completed, total ->
    println("Progress: $completed / $total")
}
// items now have filePath set

// 3. Stream preview
engine.preview("Hello", source = 1, voice).collect { event ->
    when (event) {
        is TtsEvent.AudioChunk -> player.enqueue(event.pcm)
        is TtsEvent.Done       -> println("File: ${event.filePath}")
        is TtsEvent.Error      -> println("Error: ${event.retCode}")
        is TtsEvent.Cancelled  -> println("Cancelled")
        else -> {}
    }
}

// 4. Cleanup
engine.close()
```

### Custom Cache Strategy

Implement `TtsCacheStrategy`:

```kotlin
class MyStrategy : TtsCacheStrategy {
    override fun selectFilesToEvict(
        files: List<File>,
        totalSize: Long,
        maxSizeBytes: Long,
    ): List<File> {
        // your eviction logic
    }
}

val engine = TtsEngine.Builder()
    .cacheStrategy(MyStrategy())
    // ...
    .build()
```

### Building

```bash
# Run tests
./gradlew test

# Build AAR (tts-core) + JARs (tts-player, tts-testing)
./gradlew assemble
```

### License

Apache License 2.0 — see [LICENSE](LICENSE).

---

## 中文

### 功能特性

- 多引擎支持 — 通过 source ID 注册多个 TTS 后端
- 协程原生 — 基于 `kotlinx.coroutines`，完全异步
- 连接池 — 有界 SDK 池，可配置并发数
- 磁盘缓存 — 自动缓存，支持可插拔淘汰策略（LRU、最大文件优先、自定义）
- 批量生成 — 并行合成，支持进度回调
- 流式预览 — 实时 `Flow<TtsEvent>` 音频预览
- 音频播放器 — 基于队列的 PCM 播放器，支持播放/暂停/停止生命周期

### 模块结构

```
tts-core       — 引擎、缓存、SDK 池、接口（Android AAR）
tts-player     — 音频播放器，队列与状态管理（JVM JAR）
tts-testing    — FakeTtsSdk 及测试工具（JVM JAR）
```

### 安装

在 `build.gradle.kts` 中添加 GitLab Maven 仓库：

```kotlin
repositories {
    maven {
        url = uri("https://gitlab.com/api/v4/projects/<PROJECT_ID>/packages/maven")
    }
}
```

添加依赖：

```kotlin
dependencies {
    implementation("io.tts.sdk:tts-core:1.0.0")
    implementation("io.tts.sdk:tts-player:1.0.0")
    testImplementation("io.tts.sdk:tts-testing:1.0.0")
}
```

### 快速开始

```kotlin
// 1. 构建引擎
val engine = TtsEngine.Builder()
    .sdk(source = 1, factory = { MyTtsSdkImpl() }, poolSize = 3)
    .cacheDir(File("/path/to/cache"))
    .cacheStrategy(LruCacheStrategy())       // 或 LargestFirstCacheStrategy()
    .build()

// 2. 批量生成
val items = listOf(
    TtsItem(text = "你好世界", source = 1),
    TtsItem(text = "再见", source = 1),
)
val voice = TtsVoiceParams(voiceType = "default")

engine.generate(items, voice) { completed, total ->
    println("进度: $completed / $total")
}
// items 的 filePath 已被赋值

// 3. 流式预览
engine.preview("你好", source = 1, voice).collect { event ->
    when (event) {
        is TtsEvent.AudioChunk -> player.enqueue(event.pcm)
        is TtsEvent.Done       -> println("文件: ${event.filePath}")
        is TtsEvent.Error      -> println("错误: ${event.retCode}")
        is TtsEvent.Cancelled  -> println("已取消")
        else -> {}
    }
}

// 4. 释放资源
engine.close()
```

### 自定义缓存策略

实现 `TtsCacheStrategy` 接口：

```kotlin
class MyStrategy : TtsCacheStrategy {
    override fun selectFilesToEvict(
        files: List<File>,
        totalSize: Long,
        maxSizeBytes: Long,
    ): List<File> {
        // 自定义淘汰逻辑
    }
}

val engine = TtsEngine.Builder()
    .cacheStrategy(MyStrategy())
    // ...
    .build()
```

### 构建

```bash
# 运行测试
./gradlew test

# 构建 AAR (tts-core) + JAR (tts-player, tts-testing)
./gradlew assemble
```

### 许可证

Apache License 2.0 — 详见 [LICENSE](LICENSE)。
