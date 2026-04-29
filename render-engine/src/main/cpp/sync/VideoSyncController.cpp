#include "VideoSyncController.h"

namespace {
int64_t clampI64(int64_t value, int64_t minValue, int64_t maxValue) {
    if (value < minValue) return minValue;
    if (value > maxValue) return maxValue;
    return value;
}
}

void VideoSyncController::reset() {
    frameTimerUs_ = 0;
    lastFramePtsUs_ = -1;
    frameTimerValid_ = false;
    consecutiveDrops_ = 0;
}

VideoSyncDecision VideoSyncController::prepareFrame(int64_t framePtsUs,
                                                    int64_t nominalFrameDurationUs,
                                                    int64_t audioClockUs,
                                                    int64_t wallNowUs) {
    VideoSyncDecision decision;

    if (lastFramePtsUs_ >= 0 && framePtsUs > lastFramePtsUs_) {
        decision.frameDurationUs = clampI64(framePtsUs - lastFramePtsUs_, 1000, 1000000);
    } else {
        decision.frameDurationUs = nominalFrameDurationUs;
    }
    lastFramePtsUs_ = framePtsUs;

    decision.diffUs = audioClockUs >= 0 ? (framePtsUs - audioClockUs) : 0;

    if (!frameTimerValid_) {
        if (audioClockUs >= 0) {
            int64_t avOffsetUs = clampI64(audioClockUs - framePtsUs, -500000, 500000);
            frameTimerUs_ = wallNowUs - avOffsetUs;
        } else {
            frameTimerUs_ = wallNowUs;
        }
        frameTimerValid_ = true;
        decision.timelineReset = true;
    }

    decision.delayUs = decision.frameDurationUs;
    if (audioClockUs >= 0) {
        int64_t syncThresholdUs = clampI64(decision.frameDurationUs, 40000, 100000);
        if (decision.diffUs <= -syncThresholdUs) {
            decision.delayUs += decision.diffUs;
            if (decision.delayUs < 0) decision.delayUs = 0;
        } else if (decision.diffUs >= syncThresholdUs) {
            if (decision.delayUs > 100000) {
                decision.delayUs += decision.diffUs;
            } else {
                decision.delayUs = 2 * decision.delayUs;
            }
        }
    }

    int64_t targetTimeUs = frameTimerUs_ + decision.delayUs;
    int64_t remainingUs = targetTimeUs - wallNowUs;
    if (remainingUs > 1000) {
        decision.sleepUs = remainingUs > 200000 ? 200000 : remainingUs;
    }

    decision.frameTimerUs = frameTimerUs_;
    decision.consecutiveDrops = consecutiveDrops_;
    return decision;
}

void VideoSyncController::finalizeFrame(const VideoSyncDecision& prepared,
                                        int64_t audioClockUs,
                                        int64_t wallNowUs,
                                        VideoSyncDecision& finalized) {
    finalized = prepared;

    frameTimerUs_ += prepared.delayUs;
    if (wallNowUs - frameTimerUs_ > 100000) {
        frameTimerUs_ = wallNowUs;
    }

    finalized.shouldRender = true;
    if (audioClockUs >= 0) {
        if (wallNowUs > frameTimerUs_ + prepared.frameDurationUs
            && consecutiveDrops_ < 1) {
            finalized.shouldRender = false;
            consecutiveDrops_++;
        }
    }

    if (finalized.shouldRender) {
        consecutiveDrops_ = 0;
    }

    finalized.consecutiveDrops = consecutiveDrops_;
    finalized.frameTimerUs = frameTimerUs_;
}
