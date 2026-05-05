package com.iwatchme.voiceeval.scoring.soe

/**
 * 镜像腾讯云智聆口语评测（SOE）的请求/响应数据结构。
 * 字段名故意与 SOE Wire Format 完全对齐 —— 把 [SoeService] 的 mock 实现替换成
 * 真实 HTTPS 客户端时，这里的类可以原封不动地序列化为请求 JSON。
 *
 * Mirror of Tencent Cloud SOE wire-format request/response shapes.
 * Field names intentionally match the SOE API verbatim, so swapping the [SoeService]
 * mock for a real HTTPS client only requires a JSON serializer — no model changes.
 */

// SOE 评测模式（EvalMode）。
// SOE evaluation mode codes.
object SoeEvalMode {
    const val WORD = 0
    const val SENTENCE = 1
    const val PARAGRAPH = 2
}

// SOE 音频文件类型（VoiceFileType）。
// SOE VoiceFileType codes.
object SoeVoiceFileType {
    const val PCM = 1
    const val WAV = 2
    const val MP3 = 3
    const val SPEEX = 4
}

// 服务端评估状态机。
// Server-side evaluation status.
enum class SoeStatus { Evaluating, Finished, Failed }

// 初始化参数：流式模式下随首片合并提交（对应 TransmitOralProcessWithInit）。
// Init params; in streaming mode they ride along with the first chunk
// (TransmitOralProcessWithInit on the production API).
data class SoeInitParams(
    // 参考文本。
    // Reference text the user is asked to read.
    val refText: String,
    // 工作模式：0=流式分片，1=非流式一次性评估。
    // Work mode: 0 = streaming chunks, 1 = one-shot batch.
    val workMode: Int = 0,
    // 评测模式（单词 / 句子 / 段落），对应 [SoeEvalMode]。
    // Evaluation granularity; values come from [SoeEvalMode].
    val evalMode: Int = SoeEvalMode.SENTENCE,
    // 评分调节系数（生产档位调音用）。
    // Score scaling coefficient (production tuning knob).
    val scoreCoeff: Float = 1.0f,
    // 服务端类型：0=英文。
    // Backend type: 0 = English.
    val serverType: Int = 0,
)

// 一次 TransmitOralProcess 请求。
// One TransmitOralProcess request.
data class SoeTransmitRequest(
    // Session 唯一 ID（UUID）。
    // Unique session UUID.
    val sessionId: String,
    // 分片序号；SOE 协议硬要求从 1 起步、单调递增、上限 3000。
    // Sequence number; SOE wire-protocol requires it to start at 1, increase
    // monotonically, and stay ≤ 3000.
    val seqId: Int,
    // 0=未完，1=末片（触发服务端最终对齐 + 打分）。
    // 0 = more to come, 1 = last chunk (triggers final alignment + scoring).
    val isEnd: Int,
    // 音频封装类型（PCM / WAV / MP3 / Speex）。
    // Audio container type (PCM / WAV / MP3 / Speex).
    val voiceFileType: Int,
    // 语音编码类型；目前 SOE 文档只列了 PCM(1)。
    // Voice encoding type; SOE currently documents only PCM (1).
    val voiceEncodeType: Int,
    // Base64 编码后的本片音频字节。
    // Base64-encoded audio bytes for this chunk.
    val userVoiceData: String,
    // 1 = 纯查询请求（不携带新音频，仅来取最终结果）。
    // 1 = query-only request (no fresh audio; just fetches the final result).
    val isQuery: Int = 0,
    // 仅当 seqId=1 时附带，对应 TransmitOralProcessWithInit 的合并 Init。
    // Present only when seqId=1; mirrors the WithInit merge of init + first chunk.
    val initParams: SoeInitParams? = null,
)

// 单个音素评分。
// Per-phoneme score entry.
data class SoePhoneInfo(
    val phone: String,
    // 音素准确度，[-1, 100]，-1 表示未对齐。
    // Phoneme pronunciation accuracy in [-1, 100]; -1 = not aligned.
    val pronAccuracy: Float,
    val memBeginTime: Int,
    val memEndTime: Int,
    val detectedStress: Boolean,
)

// 单个词的评分细节。
// Per-word evaluation detail.
data class SoeWordResult(
    val word: String,
    // 词级准确度，[-1, 100]。
    // Word-level pronunciation accuracy in [-1, 100].
    val pronAccuracy: Float,
    // 词级流利度，[0, 1]。
    // Word-level fluency in [0, 1].
    val pronFluency: Float,
    val memBeginTime: Int,
    val memEndTime: Int,
    val phoneInfos: List<SoePhoneInfo>,
)

/**
 * 一次 SOE 响应，覆盖中间态 [SoeStatus.Evaluating] 和终态 [SoeStatus.Finished]。
 * 文档明确规定：在 IsEnd 未置 1 时，所有数值字段「无意义」 —— 调用方应忽略它们。
 *
 * One SOE response, covering both the Evaluating intermediate state and the
 * Finished terminal state. Per the docs, all numeric fields are "meaningless"
 * while IsEnd hasn't been set to 1 — callers should ignore them.
 */
data class SoeResponse(
    val sessionId: String,
    val status: SoeStatus,
    // 总准确度，[-1, 100]。
    // Overall pronunciation accuracy in [-1, 100].
    val pronAccuracy: Float,
    // 流利度，[0, 1]。
    // Fluency in [0, 1].
    val pronFluency: Float,
    // 完整度，[0, 1]。
    // Completion in [0, 1].
    val pronCompletion: Float,
    // 建议分 = pronAccuracy × pronCompletion × (2 - pronCompletion)。
    // Suggested score = pronAccuracy * pronCompletion * (2 - pronCompletion).
    val suggestedScore: Float,
    val words: List<SoeWordResult>,
)
