#pragma once

#include "RenderNode.h"

// BlendNode: alpha-blends two textures together.
// Phase 1: stub — returns the first input texture unchanged.
class BlendNode : public RenderNode {
public:
    BlendNode() = default;
    ~BlendNode() override = default;

    GLuint execute(int64_t timelinePositionUs) override {
        if (inputs.empty()) return 0;
        return inputs[0]->execute(timelinePositionUs);
    }
};
