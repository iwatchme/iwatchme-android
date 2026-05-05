package com.iwatchme.voiceeval.encoder

import com.iwatchme.voiceeval.api.AudioFormatSpec
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 真实的 WAV 编码器 —— 把 16-bit PCM 直接写入 RIFF 封装的 .wav 文件。
 *
 * WAV 头里的尺寸字段指向整个负载长度，但在录音结束前我们并不知道这个值。
 * 解决方式遵循标准做法：先写 44 字节占位头，再在 [finish] 里 seek 回头部
 * 把尺寸补上。这样编码器就能保持流式工作，全程不需要把整段录音保存在内存里。
 *
 * 关于「编码器作为策略」的设计：生产环境通常换成基于 libmp3lame 的 MP3 编码器，
 * 可把带宽压到 1/5。两者实现同一个接口，仅 [feed] 返回的字节不同，参见
 * [AudioEncoder] 的 KDoc。
 *
 * Real WAV encoder — captures 16-bit PCM straight to a RIFF-wrapped file.
 *
 * The size fields in a WAV header reference the total payload, but we don't
 * know that until capture is done. We solve it the standard way: write a
 * placeholder header up front, then seek back and patch the sizes inside
 * [finish]. This keeps the encoder streaming-friendly — we never have to
 * buffer the whole recording in memory.
 *
 * NOTE on encoder-as-strategy: in production we'd swap this for an MP3
 * encoder backed by libmp3lame to cut bandwidth ~5x. The interface is the
 * same, only the bytes returned from [feed] differ. See [AudioEncoder]
 * KDoc.
 */
class WavEncoder : AudioEncoder {

    override val fileExtension: String = "wav"

    private var raf: RandomAccessFile? = null
    private var format: AudioFormatSpec? = null
    private var dataBytesWritten: Int = 0

    override fun open(format: AudioFormatSpec, outputFile: File) {
        check(raf == null) { "WavEncoder already open" }
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()
        val raf = RandomAccessFile(outputFile, "rw")
        // 先写 44 字节头部占位，真实尺寸在 finish() 里再回填。
        // 44-byte placeholder; we patch sizes in finish().
        raf.write(ByteArray(WAV_HEADER_SIZE))
        this.raf = raf
        this.format = format
        this.dataBytesWritten = 0
    }

    override fun feed(samples: ShortArray, length: Int): ByteArray {
        val raf = requireNotNull(raf) { "WavEncoder.feed before open" }
        // 16-bit 小端；引擎只采集单声道，因此不需要做声道交织转换。
        // 16-bit little-endian, no channel interleaving conversion needed
        // because the engine only captures mono.
        val bytes = ByteBuffer.allocate(length * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until length) bytes.putShort(samples[i])
        val out = bytes.array()
        raf.write(out)
        dataBytesWritten += out.size
        return out
    }

    override fun finish(): ByteArray {
        val raf = requireNotNull(raf) { "WavEncoder.finish before open" }
        val format = requireNotNull(format)
        try {
            patchHeader(raf, format, dataBytesWritten)
        } finally {
            raf.close()
            this.raf = null
        }
        return ByteArray(0)
    }

    override fun close() {
        runCatching { raf?.close() }
        raf = null
    }

    private fun patchHeader(raf: RandomAccessFile, fmt: AudioFormatSpec, dataBytes: Int) {
        val totalSize = WAV_HEADER_SIZE + dataBytes - 8
        val byteRate = fmt.sampleRateHz * fmt.channels * fmt.bitsPerSample / 8
        val blockAlign = (fmt.channels * fmt.bitsPerSample / 8).toShort()

        val header = ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(totalSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)                                  // PCM 子块大小 / PCM chunk size
        header.putShort(1)                                 // 音频格式=PCM / audio format = PCM
        header.putShort(fmt.channels.toShort())
        header.putInt(fmt.sampleRateHz)
        header.putInt(byteRate)
        header.putShort(blockAlign)
        header.putShort(fmt.bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataBytes)

        raf.seek(0)
        raf.write(header.array())
    }

    companion object {
        private const val WAV_HEADER_SIZE = 44
    }
}
