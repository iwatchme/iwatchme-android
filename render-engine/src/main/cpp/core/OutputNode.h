#pragma once

#include "RenderNode.h"
#include "gl/ShaderProgram.h"
#include "gl/FullscreenQuad.h"

// OutputNode: renders the final texture to the default framebuffer (screen or encoder).
// This is the root node of the render tree.
class OutputNode : public RenderNode {
public:
    OutputNode() = default;
    ~OutputNode() override;

    GLuint execute(int64_t timelinePositionUs) override;

    bool initGL();
    void releaseGL();

private:
    ShaderProgram passthrough_;
    FullscreenQuad quad_;
};
