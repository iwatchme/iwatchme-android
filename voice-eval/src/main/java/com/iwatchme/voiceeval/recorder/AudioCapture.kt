package com.iwatchme.voiceeval.recorder

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import com.iwatchme.voiceeval.api.AudioFormatSpec
import com.iwatchme.voiceeval.api.EvalError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * 把 [AudioRecord] 的阻塞式 pull API 适配成冷 [Flow]&lt;[PcmChunk]&gt;。
 *
 *  - 在专用 IO 调度器上阻塞调用 [AudioRecord.read]。
 *  - 读循环复用同一个 ShortArray —— 稳态下每帧零分配，避免 GC 抖动。
 *  - 无论消费者以何种方式离开（取消、异常、正常结束），都会在 `finally` 中
 *    执行 stop + release。AudioRecord 没释放是后续录音「莫名失败」的头号原因。
 *
 * 此 Flow 是「一次性」的：如果你 collect 两次，会启动两个 AudioRecord 实例，
 * 大部分设备上第二个会被系统直接拒绝。
 *
 * Adapter from [AudioRecord]'s blocking pull-API to a cold [Flow] of
 * [PcmChunk] frames. The flow:
 *
 *  - Blocks on [AudioRecord.read] inside a dedicated IO dispatcher.
 *  - Reuses one ShortArray across reads so the capture loop allocates zero
 *    bytes per frame in steady state.
 *  - Tears AudioRecord down (stop + release) in a `finally` no matter how
 *    the consumer exits — cancellation, exception, or completion. Forgetting
 *    this on AudioRecord is the #1 reason later mic-sessions silently fail.
 *
 * The flow is single-shot: collecting it twice will start two separate
 * AudioRecord instances and Android will reject the second on most devices.
 */
internal class AudioCapture(
    private val format: AudioFormatSpec,
) {

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun stream(): Flow<PcmChunk> = flow {
        val channelMask = if (format.channels == 1) {
            AudioFormat.CHANNEL_IN_MONO
        } else {
            AudioFormat.CHANNEL_IN_STEREO
        }
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBufferBytes = AudioRecord.getMinBufferSize(
            format.sampleRateHz, channelMask, encoding,
        )
        if (minBufferBytes <= 0) {
            throw EvalError.AudioInitFailed()
        }
        // 把缓冲尺寸抬到一个能让单次 read ≈125ms（16kHz/16bit/mono 约 4KB）的值，
        // 与腾讯 SOE 在生产环境的调优保持一致。
        // Round up to a frame size that yields ~125ms per read at 16kHz/16bit/mono
        // (≈ 4KB), matching the original SOE production tuning.
        val bufferShorts = (minBufferBytes / 2).coerceAtLeast(2_048)

        val record = try {
            // 使用 VOICE_RECOGNITION 源：跳过 MIC 源默认应用的 AGC/降噪，
            // 拿到对评测更友好的「裸」语音流。
            // VOICE_RECOGNITION skips the AGC/noise-suppression that MIC applies,
            // giving us the cleaner stream pronunciation scoring prefers.
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                format.sampleRateHz,
                channelMask,
                encoding,
                minBufferBytes * 2,
            )
        } catch (t: Throwable) {
            throw EvalError.AudioInitFailed(t)
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw EvalError.AudioInitFailed()
        }

        val buffer = ShortArray(bufferShorts)
        try {
            record.startRecording()
            val startedAt = System.currentTimeMillis()
            while (true) {
                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) {
                    if (read == AudioRecord.ERROR_INVALID_OPERATION ||
                        read == AudioRecord.ERROR_BAD_VALUE
                    ) {
                        throw EvalError.IoFailure(
                            IllegalStateException("AudioRecord.read returned $read"),
                        )
                    }
                    continue
                }
                emit(
                    PcmChunk(
                        data = buffer,
                        length = read,
                        timestampMs = System.currentTimeMillis() - startedAt,
                    ),
                )
            }
        } finally {
            // 务必释放 AudioRecord —— 否则下次启动麦克风会在系统层面静默失败。
            // Always release AudioRecord — otherwise the next mic session silently fails at the system level.
            runCatching { record.stop() }
            runCatching { record.release() }
        }
    }.flowOn(Dispatchers.IO)
}
