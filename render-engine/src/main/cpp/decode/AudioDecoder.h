#pragma once

#include <cstdint>
#include <vector>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libswresample/swresample.h>
}

// 音频软解码器：FFmpeg avcodec 解码 + swresample 重采样为指定 PCM 输出格式。
class AudioDecoder {
public:
    struct DecodeResult {
        int frames = 0;
        int64_t startPtsUs = -1;
        bool hasValidPts = false;
    };

    static constexpr int kBytesPerSample = 2;  // int16_t

    AudioDecoder() = default;
    ~AudioDecoder();

    bool init(AVCodecParameters* params, AVRational timeBase, int outputSampleRate, int outputChannels);
    void release();

    // 解码一个 packet，输出 PCM 数据追加到 outBuffer。
    // 返回输出的采样帧数，以及首个有效输出样本对应的 PTS。
    DecodeResult decode(AVPacket* packet, std::vector<uint8_t>& outBuffer);

    // 刷新解码器缓冲区中残留的帧（seek/eof 时调用）
    void flush();

    bool isInitialized() const { return codecCtx_ != nullptr; }

private:
    int bytesPerFrame() const { return outputChannels_ * kBytesPerSample; }

    AVCodecContext* codecCtx_ = nullptr;
    SwrContext* swrCtx_ = nullptr;
    AVFrame* frame_ = nullptr;
    AVRational timeBase_{1, 1000000};
    int outputSampleRate_ = 44100;
    int outputChannels_ = 2;
};
