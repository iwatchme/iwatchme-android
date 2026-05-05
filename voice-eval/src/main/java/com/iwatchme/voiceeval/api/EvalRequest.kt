package com.iwatchme.voiceeval.api

/**
 * 调用方传入的评测请求：指定本轮要读什么文本。
 *
 * `id` 对引擎不透明，宿主 App 可借此把评测结果映射回自己的章节 / 句子 / 单词模型。
 *
 * Caller-supplied input describing what the user is being asked to read aloud.
 *
 * `id` is opaque to the engine — useful for the host app to correlate the
 * scoring result back to a section / sentence / word in its own data model.
 */
data class EvalRequest(
    // 业务侧标识（对引擎不透明），同时用于生成本地录音文件名。
    // Business-side identifier (opaque to the engine); also used to name the local recording.
    val id: String,
    // 参考文本：用户需要朗读的内容，供打分器对齐。
    // Reference text the user is asked to read; used by the scorer for alignment.
    val refText: String,
    // 评测粒度：单词 / 句子 / 段落。默认按词数自动判定。
    // Evaluation granularity: word / sentence / paragraph. Auto-detected from word count by default.
    val mode: EvalMode = EvalMode.fromText(refText),
)

// 评测模式枚举：影响后端的对齐策略与 UI 展示形态。
// Evaluation mode enum: drives the backend alignment strategy and UI presentation.
enum class EvalMode {
    WORD,
    SENTENCE,
    PARAGRAPH;

    companion object {
        // 根据空白分词数粗略判定模式：>20 视作段落，>1 视作句子，否则单词。
        // Heuristic: >20 whitespace tokens → paragraph, >1 → sentence, else single word.
        fun fromText(text: String): EvalMode {
            val tokens = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
            return when {
                tokens > 20 -> PARAGRAPH
                tokens > 1 -> SENTENCE
                else -> WORD
            }
        }
    }
}
