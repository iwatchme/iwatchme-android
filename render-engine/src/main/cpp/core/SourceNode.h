#pragma once

#include <jni.h>
#include "RenderNode.h"
#include "gl/ShaderProgram.h"
#include "gl/FullscreenQuad.h"

class SurfaceTextureHelper;

// SourceNode: converts OES texture (from SurfaceTexture) to a regular 2D texture.
class SourceNode : public RenderNode {
public:
    SourceNode(SurfaceTextureHelper* stHelper, JNIEnv* env);
    ~SourceNode() override;

    GLuint execute(int64_t timelinePositionUs) override;

    bool initGL(int width, int height);
    void releaseGL();

private:
    SurfaceTextureHelper* stHelper_;
    JNIEnv* env_;

    ShaderProgram oesShader_;
    FullscreenQuad quad_;

    GLuint fbo_ = 0;
    GLuint outputTex_ = 0;
};
