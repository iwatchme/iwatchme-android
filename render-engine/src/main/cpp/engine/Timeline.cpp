#include "Timeline.h"
#include "common/log.h"
#include <cstring>

// ============================================================
// DecoderConfig
// ============================================================

bool DecoderConfig::operator==(const DecoderConfig& other) const {
    return codecId == other.codecId
        && width == other.width
        && height == other.height
        && extradata == other.extradata;
}

bool DecoderConfig::operator!=(const DecoderConfig& other) const {
    return !(*this == other);
}

DecoderConfig DecoderConfig::fromCodecParameters(const AVCodecParameters* params) {
    DecoderConfig cfg;
    if (!params) return cfg;
    cfg.codecId = params->codec_id;
    cfg.width = params->width;
    cfg.height = params->height;
    if (params->extradata && params->extradata_size > 0) {
        cfg.extradata.assign(params->extradata,
                             params->extradata + params->extradata_size);
    }
    return cfg;
}

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
