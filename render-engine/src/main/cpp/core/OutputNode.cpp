#include "OutputNode.h"
#include "common/log.h"

static const char* kPassthroughVS = R"(#version 300 es
layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexCoord;
out vec2 vTexCoord;
void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
    vTexCoord = aTexCoord;
}
)";

static const char* kPassthroughFS = R"(#version 300 es
precision mediump float;
uniform sampler2D uTexture;
in vec2 vTexCoord;
out vec4 fragColor;
void main() {
    fragColor = texture(uTexture, vTexCoord);
}
)";

OutputNode::~OutputNode() {
    releaseGL();
}

bool OutputNode::initGL() {
    if (!passthrough_.build(kPassthroughVS, kPassthroughFS)) {
        LOGE("OutputNode: failed to build passthrough shader");
        return false;
    }
    quad_.init();
    LOGI("OutputNode: GL initialized");
    return true;
}

void OutputNode::releaseGL() {
    passthrough_.release();
    quad_.release();
}

GLuint OutputNode::execute(int64_t timelinePositionUs) {
    if (inputs.empty()) return 0;

    // Get the texture from the child node
    GLuint inputTex = inputs[0]->execute(timelinePositionUs);
    if (inputTex == 0) return 0;

    // Render to default framebuffer (screen)
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glViewport(0, 0, outputWidth, outputHeight);

    passthrough_.use();
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, inputTex);
    passthrough_.setInt("uTexture", 0);

    quad_.draw();

    glBindTexture(GL_TEXTURE_2D, 0);

    return 0;  // Output to screen, no texture to return
}
