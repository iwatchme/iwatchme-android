# voice-eval

口语朗读评测引擎（Android Library / AAR）。

用户读一段参考文本 → 录音 → 编码 → 切片 → 流式喂给打分后端 → 拿到逐词分 + 总分；同时把录音文件存到本地，可选上传 CDN。

## 模块定位

这是 ggr 项目里那套 `FrameRecorder + 腾讯 SOE` 流程的现代化重构：

- 用 Kotlin Coroutines + Flow 描述生命周期；
- 把外部依赖（编码器、打分器、上传器）做成可插拔策略，方便单测和 demo 跑 Mock 实现；
- 内置 `MockVoiceScorer` 是「腾讯 SOE 协议级仿真」—— 严格按真实 wire format 驱动，
  生产环境只需替换 `SoeService` 实现即可，调用方无需改动。

## 架构图

```
AudioCapture ──PcmChunk──► WavEncoder ──bytes──► ChunkSlicer ──AudioChunk──► VoiceScorer
                    │                                  │
                    ├──► DbCalculator ──► EvalState.Recording
                    └──► SilenceDetector ──► auto-stop

                                              结束后 ──► AudioUploader（best-effort）
```

四个右侧组件均可插拔，见 `VoiceEvalEngine.Builder`。

## 各部件速览

### 对外 API（`scoring`/`api`/`encoder`/`upload` 包）
| 类型 | 作用 |
|---|---|
| `VoiceEvalEngine` | 公共入口，单例可复用，状态机阻止并发评测 |
| `VoiceEvalEngine.Builder` | 链式配置，所有旋钮都有「接近生产」默认值 |
| `EvalRequest` | 调用方传入：`id` + `refText` + `mode`（自动判定 WORD/SENTENCE/PARAGRAPH） |
| `EvalState`（密封类） | 状态流：`Idle / Preparing / Recording / SilenceHint / Scoring / Completed / Failed` |
| `EvalResult` | 终态结果：`overallScore + words + localPath + uploadedUrl + source` |
| `EvalError`（密封类） | 错误类型：`PermissionDenied / AudioInitFailed / IoFailure / ScorerFailure / Cancelled` |
| `AudioFormatSpec` | 16kHz/单声道/16bit（语音评测后端通用要求） |

### 录音层（`recorder` 包，全部 `internal`）
| 类型 | 作用 |
|---|---|
| `AudioCapture` | `AudioRecord` blocking pull-API → 冷 `Flow<PcmChunk>`；`finally` 必释放 |
| `PcmChunk` | 单帧 16-bit PCM；缓冲区在多次发射间复用，消费者必须及时 copy |
| `DbCalculator` | 简易 RMS 分贝表，驱动 UI 音量条 |
| `SilenceDetector` | 长静默检测：>1s WARNING、>5s AUTO_STOP |

### 编码层（`encoder` 包）
| 类型 | 作用 |
|---|---|
| `AudioEncoder`（接口） | `open() → feed()… → finish()` 三相位策略 |
| `WavEncoder` | 直通：写 RIFF 头占位 → 录完 seek 回头部回填 |
| `Mp3Encoder` | TAndroidLame（libmp3lame JNI），32kbps CBR，5x 压缩比 |

### 切片层（`internal/ChunkSlicer.kt`）
把不定长字节流重新切成等长 `AudioChunk`（默认 4KB），单调 `index`，末片 `isEnd=true`。
对齐腾讯 SOE 协议「等长包 + 末包标志」的形态。

### 打分层（`scoring` 包）
| 类型 | 作用 |
|---|---|
| `VoiceScorer`（接口） | 流式打分策略，输入 `Flow<AudioChunk>`，输出单个 `ScoringOutcome` |
| `MockVoiceScorer` | **协议级仿真**实现，按真实 SOE 协议驱动 `SoeService` |
| `scoring.soe.SoeService` | 协议传输层接口（生产=HTTPS 客户端，mock=本地仿真） |
| `scoring.soe.MockSoeService` | 同进程内 SOE 服务端仿真，严格按协议响应 |
| `scoring.soe.Soe*` 数据类 | 字段名 1:1 对齐 SOE wire format |

### 上传层（`upload` 包）
| 类型 | 作用 |
|---|---|
| `AudioUploader`（接口） | 把最终文件送出端外；与打分**故意**解耦 |
| `MockAudioUploader` | 拷到 `files/voice-eval/uploaded/` 返回 `file://` URL |

### 其他
| 类型 | 作用 |
|---|---|
| `internal.DefaultScoreFactory` | 兜底分数生成器，60–70 之间随机；**有意保留随机性**，避免用户察觉兜底模式 |
| `consumer-rules.pro` | 保留 `VoiceEvalEngine`/`Builder`/`api.**` 不被混淆 |
| `AndroidManifest.xml` | 仅声明 `RECORD_AUDIO` 权限 |

## 调用范式

```kotlin
val engine = VoiceEvalEngine.Builder(ctx)
    .scorer(MockVoiceScorer(seed = 42L, networkLatencyMs = 500))
    .uploader(MockAudioUploader(ctx))
    .build()

engine.evaluate(EvalRequest(id = "lesson1-q1", refText = "Hello world"))
    .collect { state ->
        when (state) {
            is EvalState.Recording -> /* 用 state.currentDb 画音量条 */
            is EvalState.SilenceHint -> /* 提示「请大声朗读」*/
            is EvalState.Completed   -> /* 展示 state.result */
            is EvalState.Failed      -> /* 展示 state.error */
            else -> {}
        }
    }
```

UI 实际只关心三件事：实时分贝、静默提示、终态。

## 深度文档

| 主题 | 文档 |
|---|---|
| 引擎主入口的并发设计、超时/兜底机制、流式 vs 整体打分 | [`docs/scoring-flow.md`](docs/scoring-flow.md) |
| 腾讯智聆 SOE 协议参考 + 本模块的协议仿真层 | [`docs/soe-protocol.md`](docs/soe-protocol.md) |

## Demo

直接看 `app/src/main/java/com/iwatchme/android/demo/voiceeval/VoiceEvalDemoScreen.kt`：
一个独立 Compose 屏，把 Builder 配置、Flow collect、权限管理、资源释放全串起来。
