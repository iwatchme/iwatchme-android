package com.iwatchme.voiceeval.recorder

/**
 * 由 [AudioCapture] 产出的单帧 16-bit PCM。
 *
 * 为了减少录音线程的 GC 压力，承载样本的缓冲区会在多次发射间被复用。
 * 因此消费者在挂起或者把 chunk 交给其他协程之前，**必须**先把所需字节拷贝出去。
 * Flow 会保证同一时刻只有一个 chunk 在飞，从而避免读写竞争。
 *
 * A single 16-bit PCM frame produced by [AudioCapture].
 *
 * Buffers are reused across emissions to avoid GC pressure on the capture
 * thread, so consumers MUST copy out the bytes they need before suspending
 * or handing the chunk to another coroutine. The flow guarantees consumers
 * see at most one in-flight chunk at a time.
 */
class PcmChunk(
    // 采样数据缓冲区（在多次发射间被复用）。
    // Sample buffer (reused across emissions).
    val data: ShortArray,
    // [data] 中实际有效的样本数。
    // Number of valid samples currently held in [data].
    val length: Int,
    // 相对于录音开始的时间戳（毫秒）。
    // Timestamp in ms since recording started.
    val timestampMs: Long,
)
