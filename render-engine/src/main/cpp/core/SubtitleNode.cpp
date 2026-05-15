#include "SubtitleNode.h"

#include "common/log.h"

static const char* kSubtitleVS = R"(#version 300 es
layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexCoord;
out vec2 vTexCoord;
void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
    vTexCoord = aTexCoord;
}
)";

static const char* kSubtitleFS = R"(#version 300 es
precision mediump float;
uniform sampler2D uBase;
uniform sampler2D uSubtitle;
uniform vec4 uSubRect;
in vec2 vTexCoord;
out vec4 fragColor;
void main() {
    vec4 base = texture(uBase, vTexCoord);
    vec2 subUv = (vTexCoord - uSubRect.xy) / uSubRect.zw;
    if (subUv.x >= 0.0 && subUv.x <= 1.0 && subUv.y >= 0.0 && subUv.y <= 1.0) {
        vec4 sub = texture(uSubtitle, vec2(subUv.x, 1.0 - subUv.y));
        fragColor = mix(base, vec4(sub.rgb, 1.0), sub.a);
    } else {
        fragColor = base;
    }
}
)";

SubtitleNode::~SubtitleNode() {
    releaseGL();
}

bool SubtitleNode::initGL(int width, int height, const std::string& fontPath, int fontSizePx) {
    outputWidth = width;
    outputHeight = height;

    if (!compositeShader_.build(kSubtitleVS, kSubtitleFS)) {
        LOGE("SubtitleNode: failed to build composite shader");
        return false;
    }
    quad_.init();

    glGenTextures(1, &outputTex_);
    glBindTexture(GL_TEXTURE_2D, outputTex_);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0,
                 GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    glGenFramebuffers(1, &fbo_);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo_);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                           GL_TEXTURE_2D, outputTex_, 0);
    GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
        LOGE("SubtitleNode: FBO incomplete 0x%x", status);
        return false;
    }
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glBindTexture(GL_TEXTURE_2D, 0);

    if (!textRenderer_.init(fontPath, fontSizePx)) {
        LOGE("SubtitleNode: text renderer init failed");
        return false;
    }

    LOGI("SubtitleNode: GL init %dx%d font=%s size=%d",
         width, height, fontPath.c_str(), fontSizePx);
    return true;
}

void SubtitleNode::releaseGL() {
    if (subtitleTex_) { glDeleteTextures(1, &subtitleTex_); subtitleTex_ = 0; }
    if (outputTex_) { glDeleteTextures(1, &outputTex_); outputTex_ = 0; }
    if (fbo_) { glDeleteFramebuffers(1, &fbo_); fbo_ = 0; }
    compositeShader_.release();
    quad_.release();
    textRenderer_.release();
    subtitleTexWidth_ = 0;
    subtitleTexHeight_ = 0;
    lastRenderedText_.clear();
}

void SubtitleNode::loadSubtitles(const std::string& srtPath) {
    subtitleTrack_.load(srtPath);
    lastRenderedText_.clear();
}

void SubtitleNode::clearSubtitles() {
    subtitleTrack_.clear();
    lastRenderedText_.clear();
}

GLuint SubtitleNode::execute(int64_t timelinePositionUs) {
    if (inputs.empty()) return 0;
    GLuint baseTex = inputs[0]->execute(timelinePositionUs);
    if (baseTex == 0) return 0;
    if (!enabled_ || !subtitleTrack_.isLoaded() || !textRenderer_.isReady()) return baseTex;

    const std::string& text = subtitleTrack_.textAt(timelinePositionUs);
    if (text.empty()) return baseTex;

    if (text != lastRenderedText_) {
        TextBitmap bitmap;
        int maxW = static_cast<int>(outputWidth * 0.85f);
        if (textRenderer_.renderText(text, maxW, bitmap)) {
            textRenderer_.uploadToTexture(bitmap, subtitleTex_,
                                          subtitleTexWidth_, subtitleTexHeight_);
            lastRenderedText_ = text;
        } else {
            return baseTex;
        }
    }
    if (subtitleTex_ == 0 || !fbo_) return baseTex;

    float subW = static_cast<float>(subtitleTexWidth_) / outputWidth;
    float subH = static_cast<float>(subtitleTexHeight_) / outputHeight;
    float subX = (1.0f - subW) * 0.5f;
    float subY = 0.05f;

    glBindFramebuffer(GL_FRAMEBUFFER, fbo_);
    glViewport(0, 0, outputWidth, outputHeight);
    compositeShader_.use();

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, baseTex);
    compositeShader_.setInt("uBase", 0);

    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, subtitleTex_);
    compositeShader_.setInt("uSubtitle", 1);

    float subRect[4] = {subX, subY, subW, subH};
    GLint loc = compositeShader_.getUniformLocation("uSubRect");
    if (loc >= 0) glUniform4fv(loc, 1, subRect);

    quad_.draw();

    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, 0);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, 0);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);

    return outputTex_;
}
