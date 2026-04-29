#pragma once

#include <cstdint>
#include <atomic>
#include <aaudio/AAudio.h>

// AAudio 音频输出 + 音频时钟（A/V 同步主时钟）。
//
// 时钟算法：
//   audio_clock = basePtsUs + (currentFramesRead - baseFramesRead) / sampleRate
//
// 其中 basePtsUs/baseFramesRead 在 flush/open 后首次有效 write 时建立锚点。
// 时钟跟随设备真实已消费的帧数推进，不依赖墙钟外推。
class AudioOutput {
public:
    AudioOutput() = default;
    ~AudioOutput();

    bool open(int sampleRate, int channels);
    void close();

    // 写入 PCM 数据（阻塞直到全部写完或超时）。
    // ptsUs: 这段数据对应的起始 PTS（用于时钟追踪），-1 表示无效。
    int write(const void* data, int numFrames, int64_t ptsUs);

    // 获取当前音频播放位置（微秒）—— A/V 同步主时钟。
    // 时钟尚未建立时返回 -1。
    int64_t getPlaybackPositionUs() const;

    void pause();
    void resume();
    void flush();

    bool isOpen() const { return stream_ != nullptr; }
    int sampleRate() const { return sampleRate_; }
    int channels() const { return channels_; }

private:
    AAudioStream* stream_ = nullptr;
    int sampleRate_ = 44100;
    int channels_ = 2;

    std::atomic<int64_t> basePtsUs_{0};
    std::atomic<int64_t> baseFramesRead_{0};
    std::atomic<bool> clockValid_{false};
};
