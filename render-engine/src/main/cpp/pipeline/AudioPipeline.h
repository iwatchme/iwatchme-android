#pragma once

#include <atomic>
#include <mutex>
#include <thread>
#include <vector>
#include "audio/AudioOutput.h"
#include "decode/AudioDecoder.h"

class VideoTrackPipeline;

// 音频管线：拥有 AudioDecoder / AudioOutput / 音频线程。
// 当前阶段仍由 RenderEngine 协调 clip 切换与 seek，只把音频执行链路收进本模块。
class AudioPipeline {
public:
    AudioPipeline() = default;
    ~AudioPipeline();

    // 绑定运行上下文。所有指针均为非拥有引用，生命周期由上层保证。
    void bindContext(VideoTrackPipeline* track,
                     std::atomic<bool>* engineRunning,
                     std::atomic<bool>* playing,
                     std::atomic<bool>* transitioning,
                     std::atomic<uint32_t>* generation,
                     std::atomic<int64_t>* currentPositionUs);

    bool configureFromTrack(VideoTrackPipeline* track);
    void release();

    void start();
    void stopAndJoin();

    void pause();
    void resume();
    void flush();
    void notifySeek(int64_t targetUs);

    int64_t getAudioClockUs() const;
    bool hasAudio() const { return hasAudio_; }

private:
    void threadFunc();

    VideoTrackPipeline* track_ = nullptr;
    std::atomic<bool>* engineRunning_ = nullptr;
    std::atomic<bool>* playing_ = nullptr;
    std::atomic<bool>* transitioning_ = nullptr;
    std::atomic<uint32_t>* generation_ = nullptr;
    std::atomic<int64_t>* currentPositionUs_ = nullptr;

    AudioDecoder decoder_;
    AudioOutput output_;
    std::thread thread_;
    std::mutex threadMutex_;          // 保护 start/stopAndJoin 的并发调用
    std::atomic<bool> running_{false};
    std::atomic<int64_t> seekTargetUs_{-1};
    bool hasAudio_ = false;
};
