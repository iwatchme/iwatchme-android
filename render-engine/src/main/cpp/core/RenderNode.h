#pragma once

#include <GLES3/gl3.h>
#include <vector>
#include <cstdint>
#include <memory>

class RenderNode {
public:
    virtual ~RenderNode() = default;

    // Execute this node at the given timeline position.
    // Returns the GL texture ID containing the result.
    virtual GLuint execute(int64_t timelinePositionUs) = 0;

    // Input nodes (children in the tree)
    std::vector<RenderNode*> inputs;

    // Output dimensions
    int outputWidth = 0;
    int outputHeight = 0;
};
