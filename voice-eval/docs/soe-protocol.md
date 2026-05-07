# 腾讯智聆 SOE 协议参考与本模块的协议仿真层

这篇文档分两部分：

1. **协议参考**：腾讯云智聆口语评测（SOE）真实接口形态，便于将来切到生产时直接对照实现。
2. **仿真实现**：`MockVoiceScorer` 如何按 SOE 协议级仿真驱动 `SoeService`，以及切到生产的步骤。

## 一、SOE 真实接口形态

### 核心事实：HTTP 短轮询，不是 WebSocket

**腾讯云 SOE 没有 WebSocket 流式接口，全部走 HTTP POST 到 `soe.tencentcloudapi.com`**。
所谓「流式」是在**应用层**把一段录音切成多个 HTTP 请求来上传，而不是协议层的长连接。

### 调用链

```
InitOralProcess          → 初始化一个 Session（拿到/确认 SessionId）
TransmitOralProcess (×N) → 分片传音频，每片一个独立 POST
                            最后一片置 IsEnd=1
（必要时）
TransmitOralProcess(IsQuery=1) → 轮询最终评分
```

也存在一个组合接口 `TransmitOralProcessWithInit`，把首片和初始化合二为一 ——
本模块的 `MockVoiceScorer` 走的就是这个路径。

### TransmitOralProcess 字段表

| 字段 | 类型 | 必填 | 含义 |
|---|---|---|---|
| `SessionId` | String | ✅ | 一次评测的 UUID，所有片必须一致 |
| `SeqId` | Integer | ✅ | 分片序号，**从 1 开始**，**上限 3000** |
| `IsEnd` | Integer | ✅ | 0=未完，1=最后一片（触发服务端最终评分）|
| `UserVoiceData` | String | ✅ | **本片音频的 Base64 编码** |
| `VoiceFileType` | Integer | ✅ | 1=raw/pcm、2=wav、3=mp3、4=speex |
| `VoiceEncodeType` | Integer | ✅ | 1=pcm |
| `IsQuery` | Integer | ❌ | 1=该请求**仅查询**结果，不带音频 |
| `SoeAppId` | String | ❌ | 业务应用 ID |
| `IsLongLifeSession` | Integer | ❌ | 长效 session 标识，为 1 时持续 300s |

### 协议级硬约束

1. **音频格式**：仅支持 16k 采样率 / 16bit / 单声道。
2. **分片完整性**：「16bit 的数据必须保证音频长度为偶数」—— 每片字节数必须是偶数。
3. **顺序要求**：SeqId 必须从 1 起步、连续递增。
4. **时间间隔**：分片模式下相邻两片的间隔不能超过 5 秒，否则服务端断开 Session。
5. **包数上限**：SeqId 最大 3000 包。
6. **必须 Base64**：`UserVoiceData` 是 base64 字符串。

### 单片大小

文档原话只说「请参考分片大小设置」，**没在规则页直接给出字节数**。从约束反推合理范围：

- 时间间隔 ≤ 5s ⇒ 单片对应录音时长 < 5s。
- 16kHz/16bit/单声道 = 32 KB/s ⇒ 5s 对应 160KB raw PCM。
- SeqId ≤ 3000 ⇒ 包过小会浪费序号配额。

**业界共识 / 主流 demo 用法**：每片 **80–160ms 音频，约 2–6KB raw PCM**（base64 后约 3–8KB）。
本模块默认 `chunkSize = 4 * 1024` —— 4KB raw 对应 ~125ms 音频，
与 SOE 的间隔约束、AudioRecord 的 `~125ms 一帧` 调优完美对齐。

### 最终评分什么时候返回

- **`IsEnd=0` 的中间请求**：HTTP 200 也会返回响应体，但里头的 `PronAccuracy / PronFluency / Words` 都**无意义**
  （文档原话：「当为流式模式且请求中 IsEnd 未置 1 时，取值无意义」）。
- **`IsEnd=1` 的末片**：服务端开始最终评估，返回的 `Status` 字段可能是：
  - `Evaluating`：还在算；
  - `Finished`：算完；
  - `Failed`：失败。
- **`Evaluating` 时**：可以再发一个 `IsQuery=1` 的请求**轮询**最终结果，直到 `Status=Finished`。

### 返回字段

| 字段 | 范围 | 含义 |
|---|---|---|
| `PronAccuracy` | [-1, 100] | 总准确度 |
| `PronFluency` | [0, 1] | 流利度 |
| `PronCompletion` | [0, 1] | 完整度 |
| `SuggestedScore` | [0, 100] | `= 准确度 × 完整度 × (2 - 完整度)` |
| `Words[]` | — | 逐词分，含 `PhoneInfos` 音素细节 |

## 二、本模块的协议仿真层

### 文件树

```
voice-eval/src/main/java/com/iwatchme/voiceeval/scoring/
├── VoiceScorer.kt              对外接口（Flow<AudioChunk> → ScoringOutcome）
├── MockVoiceScorer.kt          协议驱动型实现：把 chunks 变成一系列 SOE 请求
└── soe/
    ├── SoeProtocol.kt          数据类：SoeTransmitRequest / SoeResponse 等
    ├── SoeService.kt           协议传输层接口（生产=HTTPS，mock=本地仿真）
    └── MockSoeService.kt       同进程内的 SOE 服务端仿真
```

### 关键设计点

| 设计 | 说明 |
|---|---|
| **协议层抽离** | `SoeService` 接口承担「网络」职责。生产环境换成 HTTPS 客户端即可，`MockVoiceScorer` 与 demo 代码不动 |
| **字段名 1:1 对齐 SOE** | `SeqId / IsEnd / VoiceFileType / VoiceEncodeType / UserVoiceData / IsQuery / SessionId` 等全部用 SOE 文档原名 |
| **真实协议规则** | SeqId 从 1 起步、单调连续递增、上限 3000；首片携带 `InitParams`（对应 `TransmitOralProcessWithInit`）；末片置 `IsEnd=1` |
| **Base64 真编码** | `MockVoiceScorer` 用 `android.util.Base64.encodeToString(chunk.bytes, NO_WRAP)` 真做 base64；`MockSoeService` 真做 decode |
| **Evaluating → Finished 轮询** | 末片返回 `Evaluating` 时按文档轮询 `IsQuery=1`，间隔 100ms，上限 50 次（5s） |
| **服务端「评估期」仿真** | `MockSoeService` 收到 `IsEnd=1` 后会强制返回若干次 `Evaluating`，按 `serverProcessingMs` 模拟最终对齐打分耗时 |
| **完整结果合成** | `MockSoeService` 返回的 `SoeResponse` 带齐 `PronAccuracy / PronFluency / PronCompletion / SuggestedScore / Words+PhoneInfos`；`MockVoiceScorer` 把 `SuggestedScore → overallScore`、`Words[].PronAccuracy → WordScore` |

### `MockVoiceScorer` 的工作流

```
chunks.collect { chunk ->
    seq = chunk.index + 1                       // SOE SeqId 从 1 起步（我们 index 从 0）
    req = SoeTransmitRequest(
        sessionId,
        seqId = seq,
        isEnd = chunk.isEnd ? 1 : 0,
        voiceFileType = PCM,
        voiceEncodeType = PCM,
        userVoiceData = base64(chunk.bytes),
        initParams = (seq == 1) ? SoeInitParams(refText, evalMode) : null,
    )
    lastResp = service.transmit(req)
}

while (lastResp.status == Evaluating && attempts++ < 50) {
    delay(100)
    lastResp = service.transmit(SoeTransmitRequest(
        sessionId, isQuery = 1, isEnd = 1, ...
    ))
}

return lastResp.toScoringOutcome()              // SuggestedScore → overallScore
```

### `MockSoeService` 的状态机

```
                   ┌──────────────┐
   首片(InitParams)│  会话创建    │
   ────────────►   │ (lastSeq=0)  │
                   └──────┬───────┘
                          │
                          ▼
                   ┌──────────────┐  IsEnd=0
                   │  接收中间片   │ ◄────── （校验 SeqId 连续）
                   │              │
                   └──────┬───────┘
                          │ IsEnd=1
                          ▼
                   ┌──────────────┐  IsQuery=1（< serverProcessingMs）
                   │ 评估中        │ ◄────────── 返回 Evaluating
                   │ (endAt=now)  │
                   └──────┬───────┘
                          │ 期满后首次 IsQuery=1
                          ▼
                   ┌──────────────┐  后续 IsQuery=1
                   │ 已完成（缓存）│ ────────► 返回 Finished
                   └──────────────┘  （幂等）
```

## 三、切到真实生产

### Step 1：实现 `TencentSoeService`

```kotlin
class TencentSoeService(
    private val secretId: String,
    private val secretKey: String,
    private val client: OkHttpClient = OkHttpClient(),
) : SoeService {
    override suspend fun transmit(request: SoeTransmitRequest): SoeResponse {
        val signed = signTencentV3(secretId, secretKey, request.toJson())
        val resp = client.newCall(
            Request.Builder()
                .url("https://soe.tencentcloudapi.com/")
                .post(signed.body)
                .headers(signed.headers)
                .build()
        ).await()
        return resp.body!!.string().parseAsSoeResponse()
    }
}
```

### Step 2：注入 Builder

```kotlin
val engine = VoiceEvalEngine.Builder(ctx)
    .scorer(MockVoiceScorer(
        service = TencentSoeService(secretId = "...", secretKey = "..."),
        voiceFileType = SoeVoiceFileType.MP3,    // 配合 Mp3Encoder
    ))
    .encoder(::Mp3Encoder)
    .build()
```

### Step 3（可选）：重命名

`MockVoiceScorer` 现在是「协议驱动」而不是「假分数」，名字会有点误导。
可以考虑改成 `SoeVoiceScorer` —— 但那是命名问题，对协议形态没影响。

## 参考链接

- [口语评测（基础版）发音数据传输接口](https://cloud.tencent.com/document/product/884/19318)
- [发音数据传输接口附带初始化过程（常用实践）](https://cloud.tencent.com/document/product/884/32605)
- [发音评估初始化 InitOralProcess](https://cloud.tencent.com/document/product/884/19319)
- [口语评测（基础版）数据结构](https://cloud.tencent.com/document/product/884/19320)
- [口语评测（基础版）概览](https://cloud.tencent.com/document/product/884/84098)
