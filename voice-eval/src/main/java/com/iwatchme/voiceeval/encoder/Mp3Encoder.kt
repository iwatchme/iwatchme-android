package com.iwatchme.voiceeval.encoder

import com.iwatchme.voiceeval.api.AudioFormatSpec
import com.naman14.androidlame.AndroidLame
import com.naman14.androidlame.LameBuilder
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * 基于 [TAndroidLame](https://github.com/NorthernCaptain/TAndroidLame) 的 MP3 编码器。
 * TAndroidLame 是 libmp3lame 的轻量 JNI 包装；与原 ggr 项目的生产环境保持一致
 * （相同常量、相同调用形态），区别只在于这里依赖 Maven 制品，而不是把预编译的
 * `libggrlamemp3.so` 直接打进项目。
 *
 * 默认参数与生产配置一致：
 *   - 32 kbps CBR
 *   - quality = 7（「够用就好，速度优先」）
 * 这样能在原始 16-bit PCM 基础上压缩约 5 倍 —— 也是评测场景下使用 MP3 的核心原因。
 *
 * ABI 提示：自带的 `libandroidlame.so` 覆盖 arm64-v8a / armeabi-v7a / armeabi / x86，
 * **没有 x86_64**，所以此编码器在 x86_64 模拟器上会加载失败。真机或 Apple Silicon
 * 的 arm64 模拟器没问题。
 *
 * Real MP3 encoder backed by [TAndroidLame](https://github.com/NorthernCaptain/TAndroidLame),
 * a thin JNI wrapper around libmp3lame. Mirrors the production setup of the
 * original ggr project — same constants, same call shape — except we depend
 * on the Maven artifact instead of vendoring a pre-built `libggrlamemp3.so`.
 *
 * Defaults match the production tuning:
 *   - 32 kbps CBR
 *   - quality = 7 ("ok quality, fast")
 * which yields ~5x compression vs raw 16-bit PCM, the whole point of using
 * MP3 for streaming evaluation in the first place.
 *
 * ABI note: the bundled `libandroidlame.so` covers arm64-v8a, armeabi-v7a,
 * armeabi, and x86. There's **no x86_64**, so this encoder will fail to load
 * on a non-arm64 simulator. Real devices and Apple Silicon arm64 emulators
 * are fine.
 */
class Mp3Encoder(
    private val bitrateKbps: Int = 32,
    private val quality: Int = 7,
) : AudioEncoder {

    override val fileExtension: String = "mp3"

    private var lame: AndroidLame? = null
    private var output: BufferedOutputStream? = null
    private var mp3Buffer: ByteArray = ByteArray(0)

    override fun open(format: AudioFormatSpec, outputFile: File) {
        check(lame == null) { "Mp3Encoder already open" }
        require(format.bitsPerSample == 16) { "Mp3Encoder requires 16-bit PCM input" }
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        lame = LameBuilder()
            .setInSampleRate(format.sampleRateHz)
            .setOutSampleRate(format.sampleRateHz)
            .setOutChannels(format.channels)
            .setOutBitrate(bitrateKbps)
            .setQuality(quality)
            .build()

        output = BufferedOutputStream(FileOutputStream(outputFile))
    }

    override fun feed(samples: ShortArray, length: Int): ByteArray {
        val lame = requireNotNull(lame) { "Mp3Encoder.feed before open" }
        val out = requireNotNull(output)

        // LAME 对输出缓冲区的容量要求：至少 7200 + 1.25 × 样本数。
        // 我们采用「按需扩容、永不收缩」策略 —— 这样录音线程在第一帧之后
        // 就不会再触发额外的内存分配。
        // LAME's output-buffer sizing rule: at least 7200 + 1.25 * num_samples.
        // We grow lazily and never shrink — capture-thread allocations stay
        // amortized at zero after the first frame.
        val needed = 7200 + (length * 1.5).toInt() + 1
        if (mp3Buffer.size < needed) {
            mp3Buffer = ByteArray(needed)
        }

        // 单声道路径：把同一个声道同时喂给左右两个通道，与原 FrameRecorder.outputMp3()
        // 的调用形态保持一致。
        // Mono path: feed the same channel to both L and R, matching the
        // original FrameRecorder.outputMp3() call shape.
        val encoded = lame.encode(samples, samples, length, mp3Buffer)
        if (encoded <= 0) return EMPTY

        out.write(mp3Buffer, 0, encoded)
        return mp3Buffer.copyOfRange(0, encoded)
    }

    override fun finish(): ByteArray {
        val lame = requireNotNull(lame) { "Mp3Encoder.finish before open" }
        val out = requireNotNull(output)

        if (mp3Buffer.size < FLUSH_BUFFER_SIZE) {
            mp3Buffer = ByteArray(FLUSH_BUFFER_SIZE)
        }
        val flushed = lame.flush(mp3Buffer)
        try {
            if (flushed > 0) {
                out.write(mp3Buffer, 0, flushed)
            }
            out.flush()
        } finally {
            runCatching { out.close() }
            output = null
            runCatching { lame.close() }
            this.lame = null
        }
        return if (flushed > 0) mp3Buffer.copyOfRange(0, flushed) else EMPTY
    }

    override fun close() {
        runCatching { output?.flush() }
        runCatching { output?.close() }
        output = null
        runCatching { lame?.close() }
        lame = null
    }

    private companion object {
        // 按 LAME API 约定，flush 缓冲区不能小于 7200 字节。
        // LAME's flush buffer must be >= 7200 bytes per its API contract.
        const val FLUSH_BUFFER_SIZE = 7200
        val EMPTY = ByteArray(0)
    }
}
