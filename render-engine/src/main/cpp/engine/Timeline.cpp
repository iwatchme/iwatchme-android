#include "Timeline.h"
#include "common/log.h"

// ============================================================
// Timeline
// ============================================================

void Timeline::setClips(std::vector<Clip> clips) {
    clips_ = std::move(clips);
    recalculateTimeline();
}

void Timeline::appendClip(const std::string& sourcePath,
                           int64_t trimIn, int64_t trimOut) {
    Clip c;
    c.sourcePath = sourcePath;
    c.trimIn = trimIn;
    c.trimOut = trimOut;
    clips_.push_back(std::move(c));
    recalculateTimeline();
}

ClipLookup Timeline::resolve(int64_t globalPosUs) const {
    if (clips_.empty() || globalPosUs < 0) {
        return {-1, -1};
    }
    for (int i = 0; i < (int)clips_.size(); i++) {
        const Clip& c = clips_[i];
        if (globalPosUs >= c.inPoint && globalPosUs < c.outPoint) {
            int64_t sourcePos = c.trimIn + (globalPosUs - c.inPoint);
            return {i, sourcePos};
        }
    }
    // globalPosUs >= totalDuration
    return {-1, -1};
}

void Timeline::recalculateTimeline() {
    int64_t cursor = 0;
    for (auto& c : clips_) {
        c.inPoint = cursor;
        c.outPoint = cursor + (c.trimOut - c.trimIn);
        cursor = c.outPoint;
    }
    totalDurationUs_ = cursor;
    LOGI("Timeline: recalculated %d clips, totalDuration=%.2fs",
         (int)clips_.size(), totalDurationUs_ / 1000000.0);
}
