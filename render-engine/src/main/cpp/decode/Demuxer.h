#pragma once

#include <cstdint>
#include <string>
#include <mutex>
#include <deque>

extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libavcodec/bsf.h>
}

class Demuxer {
public:
    Demuxer() = default;
    ~Demuxer();

    bool open(const char* filePath);
    void close();

    // 读取下一个视频 packet（H.264/HEVC 已转为 Annex B 格式）。EOF 或出错返回 false。
    // 注意：如果在读取过程中遇到音频 packet，会缓存到内部队列，不会丢弃。
    bool readVideoPacket(AVPacket* packet);

    // 读取下一个音频 packet。EOF 或出错返回 false。
    // 注意：如果在读取过程中遇到视频 packet，会缓存到内部队列，不会丢弃。
    bool readAudioPacket(AVPacket* packet);

    // 读取下一个 packet（不区分音频/视频），返回流类型
    enum class PacketType { Video, Audio, Eof };
    PacketType readPacket(AVPacket* packet);

    // Seek（微秒）—— 同时清空内部缓存队列
    bool seek(int64_t positionUs);

    // 视频信息
    int videoWidth() const;
    int videoHeight() const;
    int64_t durationUs() const;
    double fps() const;
    AVRational videoTimeBase() const;
    AVCodecParameters* videoCodecParameters() const;

    // 音频信息
    bool hasAudio() const { return audioStreamIndex_ >= 0; }
    int audioSampleRate() const;
    int audioChannels() const;
    AVRational audioTimeBase() const;
    AVCodecParameters* audioCodecParameters() const;

    bool isOpen() const { return formatCtx_ != nullptr; }

    // 线程安全：多线程读取时需要加锁
    std::mutex& readMutex() { return readMutex_; }

private:
    bool initBitstreamFilter();
    bool applyBsf(AVPacket* packet);
    void flushPacketQueues();

    AVFormatContext* formatCtx_ = nullptr;
    AVBSFContext* bsfCtx_ = nullptr;
    int videoStreamIndex_ = -1;
    int audioStreamIndex_ = -1;
    std::mutex readMutex_;

    // 内部缓存队列：当 readVideoPacket 遇到音频包时缓存到 audioQueue_，反之亦然。
    // 避免两个线程交替读取时互相"偷"对方的 packet。
    std::deque<AVPacket*> videoPacketQueue_;
    std::deque<AVPacket*> audioPacketQueue_;
};
