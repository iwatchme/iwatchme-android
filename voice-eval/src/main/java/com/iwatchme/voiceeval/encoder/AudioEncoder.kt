package com.iwatchme.voiceeval.encoder

import com.iwatchme.voiceeval.api.AudioFormatSpec
import java.io.Closeable
import java.io.File

/**
 * 音频编码器策略接口：把一连串 16-bit PCM 帧编成磁盘上的最终音频文件，
 * 同时返回适合网络传输的字节序列。
 *
 * 引擎对每次评测会按三个阶段调用：
 *  1. [open]   —— 录音前调用一次，用于建立文件句柄、写头部占位等。
 *  2. [feed]   —— 录音过程中针对每帧 PCM 反复调用。
 *  3. [finish] —— 录音结束时调用一次，刷出尾部数据并修补文件头。
 *
 * [feed] 返回的字节是「打分器」要消费的内容：直通编码器返回的是原始 PCM；
 * MP3 编码器返回的是已压缩的 MP3 字节。后续按多大粒度切片由 ChunkSlicer 负责，
 * 编码器自身不关心传输 chunk 的大小。
 *
 * Strategy for turning a stream of 16-bit PCM frames into a finished audio
 * file on disk plus a sequence of network-shaped chunks.
 *
 * The engine drives this in three phases per session:
 *
 *  1. [open] — once, before any audio arrives.
 *  2. [feed] — many times, one per PCM frame from AudioRecord.
 *  3. [finish] — once, returns the absolute path to the finalized file.
 *
 * `feed` returns the encoded bytes that the *scorer* should consume for this
 * frame. For a passthrough encoder this is the same PCM the caller fed in;
 * for an MP3 encoder this would be the encoded MP3 bytes. The engine takes
 * care of grouping them into transmit-sized chunks downstream — encoders
 * don't need to know about chunk size.
 */
interface AudioEncoder : Closeable {

    // 输出文件扩展名（如 "wav" / "mp3"），引擎用它拼接录音文件名。
    // Output file extension (e.g. "wav" / "mp3"); the engine uses it when naming the recording.
    val fileExtension: String

    // 打开输出文件并写入头部占位；必须在 feed 之前调用一次。
    // Open the output file and write any header placeholder; must be called once before feed().
    fun open(format: AudioFormatSpec, outputFile: File)

    // 喂入一帧 PCM；返回此帧编码后的字节（供打分器消费）。
    // Feed one PCM frame; returns the encoded bytes that the scorer should consume for this frame.
    fun feed(samples: ShortArray, length: Int): ByteArray

    // 收尾：把缓冲中残余字节刷出，回填文件头大小字段。
    // Flush any remaining buffered bytes and finalize the file header.
    fun finish(): ByteArray
}
