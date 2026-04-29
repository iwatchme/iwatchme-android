#pragma once

#include <cstdint>

struct VideoSyncDecision {
    int64_t frameDurationUs = 0;
    int64_t delayUs = 0;
    int64_t diffUs = 0;
    int64_t sleepUs = 0;
    bool timelineReset = false;
    bool shouldRender = true;
    int consecutiveDrops = 0;
    int64_t frameTimerUs = 0;
};

// 只负责视频同步的局部状态和决策，不感知解码器、时间线和 GL 资源。
class VideoSyncController {
public:
    void reset();

    VideoSyncDecision prepareFrame(int64_t framePtsUs,
                                   int64_t nominalFrameDurationUs,
                                   int64_t audioClockUs,
                                   int64_t wallNowUs);

    void finalizeFrame(const VideoSyncDecision& prepared,
                       int64_t audioClockUs,
                       int64_t wallNowUs,
                       VideoSyncDecision& finalized);

private:
    int64_t frameTimerUs_ = 0;
    int64_t lastFramePtsUs_ = -1;
    bool frameTimerValid_ = false;
    int consecutiveDrops_ = 0;
};
