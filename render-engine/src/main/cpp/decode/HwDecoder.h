#pragma once

#include <cstdint>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <android/native_window.h>

extern "C" {
#include <libavcodec/avcodec.h>
}

class HwDecoder {
public:
    // 解码输出帧（两步 API 用）
    struct DecodedFrame {
        int32_t bufferIndex = -1;  // MediaCodec output buffer index
        int64_t pts = -1;         // PTS（stream timebase 单位）
    };

    HwDecoder() = default;
    ~HwDecoder();

    // Initialize with codec params and output surface (Surface mode)
    bool init(AVCodecParameters* params, ANativeWindow* outputSurface);
    void release();

    // 正常播放态：非阻塞尝试喂入一个压缩 packet。
    // 如果当前没有可用的 input buffer，立即返回 false。
    // 这样渲染线程不会被 MediaCodec 的 input timeout 拖慢。
    bool tryQueuePacket(AVPacket* packet);

    // 预热/seek 场景：允许在给定超时内等待 input buffer。
    // 返回 true 表示 packet 已进入 codec；false 表示在 timeout 内未能送入。
    // 这类场景目标是“尽快解出目标帧”，允许做有界等待。
    bool queuePacketWithTimeout(AVPacket* packet, int64_t timeoutUs);

    // Signal end of stream
    void queueEOS();

    // ---- 两步 API（支持早丢帧）----
    // Step 1: 取出解码帧，但不释放 output buffer（不触发 Surface/GL 路径）
    bool dequeueOutput(DecodedFrame& outFrame, int64_t timeoutUs = 10000);

    // Step 2: 释放 output buffer。render=true 送 Surface，render=false 丢弃
    void releaseOutput(int32_t bufferIndex, bool render);

    // ---- 便捷 API（一步完成，始终 render=true）----
    // Seek/预览等场景用，等同于 dequeueOutput + releaseOutput(true)
    int64_t dequeueAndRender(int64_t timeoutUs = 10000);

    // Flush decoder (for seek)
    void flush();

    bool isInitialized() const { return codec_ != nullptr; }

private:
    bool configureCsd(AVCodecParameters* params, AMediaFormat* format);
    bool queuePacketInternal(AVPacket* packet, int64_t timeoutUs);

    AMediaCodec* codec_ = nullptr;
    int queuedCount_ = 0;
    int dequeuedCount_ = 0;
};
