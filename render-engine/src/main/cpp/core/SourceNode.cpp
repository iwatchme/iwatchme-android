#include "SourceNode.h"
#include "decode/SurfaceTextureHelper.h"
#include "common/log.h"
#include <GLES2/gl2ext.h>

static const char* oesVertSrc = R"(#version 300 es
layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexCoord;
uniform mat4 uTexMatrix;
out vec2 vTexCoord;
void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
    vTexCoord = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
}
)";

static const char* oesFragSrc = R"(#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
uniform samplerExternalOES uTexture;
in vec2 vTexCoord;
out vec4 fragColor;
void main() {
    fragColor = texture(uTexture, vTexCoord);
}
)";

SourceNode::SourceNode(SurfaceTextureHelper* stHelper, JNIEnv* env)
    : stHelper_(stHelper), env_(env) {
}

SourceNode::~SourceNode() {
    releaseGL();
}

bool SourceNode::initGL(int width, int height) {
    outputWidth = width;
    outputHeight = height;

    if (!oesShader_.build(oesVertSrc, oesFragSrc)) {
        LOGE("SourceNode: failed to build OES shader");
        return false;
    }

    quad_.init();

    // Output 2D texture
    glGenTextures(1, &outputTex_);
    glBindTexture(GL_TEXTURE_2D, outputTex_);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0,
                 GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    // FBO
    glGenFramebuffers(1, &fbo_);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo_);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                           GL_TEXTURE_2D, outputTex_, 0);

    GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
        LOGE("SourceNode: FBO incomplete: 0x%x", status);
        return false;
    }

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glBindTexture(GL_TEXTURE_2D, 0);

    LOGI("SourceNode: GL initialized %dx%d, outputTex=%u", width, height, outputTex_);
    return true;
}

void SourceNode::releaseGL() {
    if (fbo_) { glDeleteFramebuffers(1, &fbo_); fbo_ = 0; }
    if (outputTex_) { glDeleteTextures(1, &outputTex_); outputTex_ = 0; }
    oesShader_.release();
    quad_.release();
}

GLuint SourceNode::execute(int64_t /* timelinePositionUs */) {
    if (!stHelper_ || !fbo_) return 0;

    // Get the texture transform matrix from SurfaceTexture
    float texMatrix[16];
    stHelper_->getTransformMatrix(env_, texMatrix);

    // Render OES texture to 2D output texture via FBO
    glBindFramebuffer(GL_FRAMEBUFFER, fbo_);
    glViewport(0, 0, outputWidth, outputHeight);

    oesShader_.use();

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, stHelper_->oesTexId());
    oesShader_.setInt("uTexture", 0);
    oesShader_.setMatrix4("uTexMatrix", texMatrix);

    quad_.draw();

    glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);

    return outputTex_;
}
