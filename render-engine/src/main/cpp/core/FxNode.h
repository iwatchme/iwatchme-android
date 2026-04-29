#pragma once

#include "RenderNode.h"

// FxNode: applies a shader effect to an input texture.
// Phase 1: pass-through stub — returns the input texture unchanged.
class FxNode : public RenderNode {
public:
    FxNode() = default;
    ~FxNode() override = default;

    GLuint execute(int64_t timelinePositionUs) override {
        if (inputs.empty()) return 0;
        return inputs[0]->execute(timelinePositionUs);
    }
};
