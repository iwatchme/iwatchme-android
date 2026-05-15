#include "render/RenderGraphBuilder.h"
#include "common/log.h"

bool RenderGraphBuilder::ensureOutputNodeInitialized() {
    if (outputNode_) {
        return true;
    }

    auto outputNode = std::make_unique<OutputNode>();
    if (!outputNode->initGL()) {
        LOGE("RenderGraphBuilder: OutputNode GL init failed");
        return false;
    }

    outputNode_ = std::move(outputNode);
    return true;
}

void RenderGraphBuilder::release() {
    subtitleNode_.reset();
    blendNode_.reset();
    outputNode_.reset();
}

void RenderGraphBuilder::setSubtitleConfig(const std::string& srtPath,
                                           const std::string& fontPath,
                                           int fontSizePx) {
    srtPath_ = srtPath;
    fontPath_ = fontPath;
    fontSizePx_ = fontSizePx;
}

void RenderGraphBuilder::setSubtitleEnabled(bool enabled) {
    subtitleEnabled_ = enabled;
    if (subtitleNode_) {
        subtitleNode_->setEnabled(enabled);
    }
}

void RenderGraphBuilder::rebuild(SourceNode* primarySource,
                                 SourceNode* overlaySource,
                                 int surfaceWidth,
                                 int surfaceHeight,
                                 float overlayAlpha) {
    if (!outputNode_) {
        return;
    }

    outputNode_->inputs.clear();
    blendNode_.reset();
    subtitleNode_.reset();

    if (!primarySource) {
        return;
    }

    RenderNode* videoTail = primarySource;

    if (overlaySource) {
        auto blendNode = std::make_unique<BlendNode>();
        if (!blendNode->initGL(surfaceWidth, surfaceHeight)) {
            LOGE("RenderGraphBuilder: BlendNode GL init failed");
            return;
        }
        blendNode->overlayAlpha = overlayAlpha;
        blendNode->inputs.push_back(primarySource);
        blendNode->inputs.push_back(overlaySource);
        videoTail = blendNode.get();
        blendNode_ = std::move(blendNode);
    }

    if (subtitleEnabled_ && !srtPath_.empty() && !fontPath_.empty()) {
        auto subtitleNode = std::make_unique<SubtitleNode>();
        if (subtitleNode->initGL(surfaceWidth, surfaceHeight, fontPath_, fontSizePx_)) {
            subtitleNode->loadSubtitles(srtPath_);
            subtitleNode->setEnabled(true);
            subtitleNode->inputs.push_back(videoTail);
            videoTail = subtitleNode.get();
            subtitleNode_ = std::move(subtitleNode);
            LOGI("RenderGraphBuilder: SubtitleNode inserted (srt=%s, font=%s, size=%d)",
                 srtPath_.c_str(), fontPath_.c_str(), fontSizePx_);
        } else {
            LOGE("RenderGraphBuilder: SubtitleNode GL init failed");
        }
    }

    outputNode_->inputs.push_back(videoTail);

    if (blendNode_ && subtitleNode_) {
        LOGI("RenderGraphBuilder: OutputNode -> SubtitleNode -> BlendNode -> [Primary, Overlay]");
    } else if (blendNode_) {
        LOGI("RenderGraphBuilder: OutputNode -> BlendNode -> [Primary, Overlay(alpha=%.2f)]",
             overlayAlpha);
    } else if (subtitleNode_) {
        LOGI("RenderGraphBuilder: OutputNode -> SubtitleNode -> Primary");
    } else {
        LOGI("RenderGraphBuilder: OutputNode -> Primary");
    }
}
