#include "TimelineValidator.h"
#include "common/log.h"
#include "decode/Demuxer.h"

namespace {
bool probeClipDurationUs(const std::string& sourcePath, int64_t& durationUs) {
    Demuxer probe;
    if (!probe.open(sourcePath.c_str())) {
        return false;
    }
    durationUs = probe.durationUs();
    probe.close();
    return durationUs > 0;
}
}

bool TimelineValidator::completeAndValidate(std::vector<Clip>& clips, const char* label) {
    for (auto& clip : clips) {
        if (clip.sourcePath.empty()) {
            LOGE("%s: empty source path", label);
            return false;
        }
        if (clip.trimIn < 0) {
            LOGE("%s: trimIn < 0 for %s", label, clip.sourcePath.c_str());
            return false;
        }

        int64_t probedDurationUs = -1;
        if (clip.trimOut < 0 || clip.trimOut <= clip.trimIn) {
            if (!probeClipDurationUs(clip.sourcePath, probedDurationUs)) {
                LOGE("%s: failed to probe %s", label, clip.sourcePath.c_str());
                return false;
            }
        }

        if (clip.trimOut < 0) {
            clip.trimOut = probedDurationUs;
        }

        if (clip.trimOut <= clip.trimIn) {
            LOGE("%s: invalid trim range [%lld, %lld] for %s",
                 label,
                 (long long)clip.trimIn,
                 (long long)clip.trimOut,
                 clip.sourcePath.c_str());
            return false;
        }
    }
    return true;
}
