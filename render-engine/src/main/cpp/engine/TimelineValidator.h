#pragma once

#include <vector>
#include "engine/Timeline.h"

class TimelineValidator {
public:
    // 补全 trimOut == -1 的片段，并校验 trim 范围合法。
    // 仅负责 probe / validate，不负责提交到 Timeline。
    static bool completeAndValidate(std::vector<Clip>& clips, const char* label);
};
