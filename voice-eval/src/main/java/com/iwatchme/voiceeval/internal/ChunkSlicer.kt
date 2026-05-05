package com.iwatchme.voiceeval.internal

import com.iwatchme.voiceeval.scoring.AudioChunk

/**
 * 把任意大小的编码字节流，重新切成固定尺寸的 [AudioChunk]：
 * 序号单调递增，恰好有一个 isEnd=true 的尾部 chunk。
 * 这种形态对齐了腾讯 SOE 等真实评测协议「每个传输包等长、最后一个为 EOF」的约定。
 *
 * 类内部是有状态的，且面向单次评测；每次评测都要新建一个实例。
 * 非线程安全 —— 引擎只在单个协程里驱动它。
 *
 * Re-bins an arbitrary stream of encoded byte arrays into fixed-size
 * [AudioChunk]s with monotonic indices and exactly one final isEnd=true
 * chunk. Mirrors the SOE wire protocol where every transmit packet is the
 * same size except the tail.
 *
 * Stateful, single-session — instantiate one per evaluation. Not thread-safe;
 * the engine drives it from a single coroutine.
 */
internal class ChunkSlicer(
    private val chunkSize: Int = 4 * 1024,
) {

    // 中间缓冲：尚未凑够一片 chunk 的字节就攒在这里。
    // Intermediate buffer; bytes that don't yet fill a chunk live here.
    private val buffer = ArrayList<Byte>(chunkSize * 2)

    // 下一个 chunk 的序号，单调递增。
    // Sequence counter for the next emitted chunk.
    private var nextIndex = 0

    fun feed(bytes: ByteArray): List<AudioChunk> {
        if (bytes.isEmpty()) return emptyList()
        for (b in bytes) buffer.add(b)
        val out = ArrayList<AudioChunk>()
        while (buffer.size >= chunkSize) {
            val slice = ByteArray(chunkSize)
            for (i in 0 until chunkSize) slice[i] = buffer[i]
            // 一次性 O(n) 整体前移；比执行 4096 次 ArrayList.removeAt(0) 划算得多。
            // Drop the consumed prefix in one O(n) shift; cheaper than 4096
            // individual removeAt(0) calls on ArrayList.
            buffer.subList(0, chunkSize).clear()
            out.add(AudioChunk(index = nextIndex++, isEnd = false, bytes = slice))
        }
        return out
    }

    /**
     * 把缓冲区里所有剩余字节（含编码器 finish 时刷出的尾部数据）打包成最后一个 chunk。
     * 即使为空也至少发射一个 chunk，让下游打分器一定能看到 isEnd 信号。
     *
     * Drain whatever's left in the buffer (plus any trailing flush bytes)
     * as one final chunk. Always emits at least one chunk so downstream
     * scorers see a definitive isEnd signal.
     */
    fun finish(trailing: ByteArray): AudioChunk {
        for (b in trailing) buffer.add(b)
        val tail = ByteArray(buffer.size)
        for (i in tail.indices) tail[i] = buffer[i]
        buffer.clear()
        return AudioChunk(index = nextIndex++, isEnd = true, bytes = tail)
    }
}
