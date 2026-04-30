# iwatchme-android

A personal Android demo application for daily hands-on testing and experimentation with Android technologies.

## What is this?

This is my personal playground for exploring various Android development topics. It's not a production app -- just a place to try things out, verify ideas, and keep working examples of different Android tech stacks.

## Modules

| Module | Description |
|--------|-------------|
| `app` | Main application shell with Compose navigation and demo registry |
| `crashLib` | Native crash capture library (C++ / JNI) |
| `render-engine` | Video playback engine using FFmpeg + MediaCodec + OpenGL ES |
| `startupLab` | Startup optimization experiments and inspector UI |
| `startupRuntime` | Lightweight startup task orchestration framework (DAG-based) |
| `customCoroutine` | Kotlin coroutine internals exploration |
| `benchmark` | Macrobenchmark cold-start measurements |
| `baselineprofile` | Baseline profile generation for AOT optimization |
| `build-logic` | Gradle convention plugins for shared build configuration |
| `customGradlePlugin` | ASM-based bytecode instrumentation plugin |

## Tech Stack

- Kotlin + Jetpack Compose
- Gradle convention plugins (build-logic)
- FFmpeg + OpenGL ES (native rendering)
- JNI / C++ (crash capture)
- Macrobenchmark + Baseline Profiles
- Perfetto tracing

## Build

```bash
./gradlew assembleDebug
```

## Author

iwatchme
