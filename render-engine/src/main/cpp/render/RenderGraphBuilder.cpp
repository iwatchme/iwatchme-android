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
    blendNode_.reset();
    outputNode_.reset();
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

    if (!primarySource) {
        return;
    }

    if (overlaySource) {
        auto blendNode = std::make_unique<BlendNode>();
        if (!blendNode->initGL(surfaceWidth, surfaceHeight)) {
            LOGE("RenderGraphBuilder: BlendNode GL init failed");
            return;
        }
        blendNode->overlayAlpha = overlayAlpha;
        blendNode->inputs.push_back(primarySource);
        blendNode->inputs.push_back(overlaySource);
        outputNode_->inputs.push_back(blendNode.get());
        blendNode_ = std::move(blendNode);
        LOGI("RenderGraphBuilder: OutputNode -> BlendNode -> [Primary, Overlay(alpha=%.2f)]",
             overlayAlpha);
        return;
    }

    outputNode_->inputs.push_back(primarySource);
    LOGI("RenderGraphBuilder: OutputNode -> SourceNode (single track)");
}
