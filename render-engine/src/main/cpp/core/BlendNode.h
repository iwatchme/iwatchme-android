#pragma once

#include "RenderNode.h"
#include "gl/ShaderProgram.h"
#include "gl/FullscreenQuad.h"

// BlendNode: alpha-blends two input textures.
// inputs[0] = base layer, inputs[1] = overlay layer.
// When overlay returns 0 (inactive), passes through base unchanged.
class BlendNode : public RenderNode {
public:
    BlendNode() = default;
    ~BlendNode() override;

    GLuint execute(int64_t timelinePositionUs) override;

    bool initGL(int width, int height);
    void releaseGL();

    float overlayAlpha = 1.0f;

private:
    ShaderProgram blendShader_;
    FullscreenQuad quad_;
    GLuint fbo_ = 0;
    GLuint outputTex_ = 0;
};
