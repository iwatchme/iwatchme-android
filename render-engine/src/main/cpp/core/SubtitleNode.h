#pragma once

#include <string>

#include "RenderNode.h"
#include "gl/FullscreenQuad.h"
#include "gl/ShaderProgram.h"
#include "subtitle/SrtParser.h"
#include "subtitle/TextRenderer.h"

class SubtitleNode : public RenderNode {
public:
    SubtitleNode() = default;
    ~SubtitleNode() override;

    GLuint execute(int64_t timelinePositionUs) override;

    bool initGL(int width, int height, const std::string& fontPath, int fontSizePx);
    void releaseGL();

    void loadSubtitles(const std::string& srtPath);
    void clearSubtitles();
    void setEnabled(bool enabled) { enabled_ = enabled; }
    bool hasSubtitles() const { return subtitleTrack_.isLoaded(); }

private:
    bool enabled_ = true;

    SubtitleTrack subtitleTrack_;
    TextRenderer textRenderer_;
    std::string lastRenderedText_;
    GLuint subtitleTex_ = 0;
    int subtitleTexWidth_ = 0;
    int subtitleTexHeight_ = 0;

    ShaderProgram compositeShader_;
    FullscreenQuad quad_;
    GLuint fbo_ = 0;
    GLuint outputTex_ = 0;
};
