#include "BlendNode.h"
#include "common/log.h"

static const char* kBlendVS = R"(#version 300 es
layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexCoord;
out vec2 vTexCoord;
void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
    vTexCoord = aTexCoord;
}
)";

static const char* kBlendFS = R"(#version 300 es
precision mediump float;
uniform sampler2D uBase;
uniform sampler2D uOverlay;
uniform float uOverlayAlpha;
in vec2 vTexCoord;
out vec4 fragColor;
void main() {
    vec4 base = texture(uBase, vTexCoord);
    vec4 overlay = texture(uOverlay, vTexCoord);
    float a = overlay.a * uOverlayAlpha;
    fragColor = mix(base, overlay, a);
}
)";

BlendNode::~BlendNode() {
    releaseGL();
}

bool BlendNode::initGL(int width, int height) {
    outputWidth = width;
    outputHeight = height;

    if (!blendShader_.build(kBlendVS, kBlendFS)) {
        LOGE("BlendNode: failed to build blend shader");
        return false;
    }

    quad_.init();

    // Output texture
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
        LOGE("BlendNode: FBO incomplete: 0x%x", status);
        return false;
    }

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glBindTexture(GL_TEXTURE_2D, 0);

    LOGI("BlendNode: GL initialized %dx%d", width, height);
    return true;
}

void BlendNode::releaseGL() {
    if (fbo_) { glDeleteFramebuffers(1, &fbo_); fbo_ = 0; }
    if (outputTex_) { glDeleteTextures(1, &outputTex_); outputTex_ = 0; }
    blendShader_.release();
    quad_.release();
}

GLuint BlendNode::execute(int64_t timelinePositionUs) {
    if (inputs.empty()) return 0;

    // Get base texture
    GLuint baseTex = inputs[0]->execute(timelinePositionUs);
    if (baseTex == 0) return 0;

    // If no overlay input, pass through base
    if (inputs.size() < 2) return baseTex;

    // Get overlay texture (may return 0 if overlay is inactive)
    GLuint overlayTex = inputs[1]->execute(timelinePositionUs);
    if (overlayTex == 0) return baseTex;  // No overlay content → pass through

    if (!fbo_) return baseTex;  // GL not initialized

    // Blend into FBO
    glBindFramebuffer(GL_FRAMEBUFFER, fbo_);
    glViewport(0, 0, outputWidth, outputHeight);

    blendShader_.use();

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, baseTex);
    blendShader_.setInt("uBase", 0);

    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, overlayTex);
    blendShader_.setInt("uOverlay", 1);

    blendShader_.setFloat("uOverlayAlpha", overlayAlpha);

    quad_.draw();

    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, 0);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, 0);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);

    return outputTex_;
}
