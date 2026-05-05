package com.iwatchme.voiceeval.api

/**
 * 引擎采集时使用的音频格式。默认值与所有主流语音评测后端
 * （腾讯 SOE / 科大讯飞 / Whisper）的要求保持一致。
 *
 * The audio format the engine captures in. Defaults match what every speech
 * evaluation backend (Tencent SOE / iFlyTek / Whisper) requires.
 */
data class AudioFormatSpec(
    // 采样率（Hz）：16kHz 是语音识别的事实标准。
    // Sample rate in Hz; 16kHz is the de-facto standard for speech recognition.
    val sampleRateHz: Int = 16_000,
    // 声道数：评测场景固定单声道。
    // Channel count; pronunciation evaluation always uses mono.
    val channels: Int = 1,
    // 量化位深：16 位 PCM 是 AudioRecord 唯一保证可用的格式。
    // PCM bit depth; 16-bit is the only encoding AudioRecord guarantees on Android.
    val bitsPerSample: Int = 16,
) {
    // 每个采样点占多少字节（=位深/8 × 声道数）。
    // Bytes per single sample frame (= bitsPerSample / 8 × channels).
    val bytesPerSample: Int get() = bitsPerSample / 8 * channels

    // 每秒原始 PCM 字节数；用于估算缓冲区与磁盘占用。
    // Raw PCM bytes produced per second; used to size buffers and estimate disk usage.
    val bytesPerSecond: Int get() = sampleRateHz * bytesPerSample
}
