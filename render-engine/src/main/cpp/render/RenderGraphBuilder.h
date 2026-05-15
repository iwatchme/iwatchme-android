#pragma once

#include <memory>
#include <string>

#include "core/BlendNode.h"
#include "core/OutputNode.h"
#include "core/SourceNode.h"
#include "core/SubtitleNode.h"

// RenderGraphBuilder 负责渲染树节点的创建、连接与释放。
class RenderGraphBuilder {
public:
    bool ensureOutputNodeInitialized();
    void release();

    void rebuild(SourceNode* primarySource,
                 SourceNode* overlaySource,
                 int surfaceWidth,
                 int surfaceHeight,
                 float overlayAlpha);

    void setSubtitleConfig(const std::string& srtPath,
                           const std::string& fontPath,
                           int fontSizePx);
    void setSubtitleEnabled(bool enabled);

    OutputNode* outputNode() const { return outputNode_.get(); }
    BlendNode* blendNode() const { return blendNode_.get(); }
    SubtitleNode* subtitleNode() const { return subtitleNode_.get(); }

private:
    std::unique_ptr<OutputNode> outputNode_;
    std::unique_ptr<BlendNode> blendNode_;
    std::unique_ptr<SubtitleNode> subtitleNode_;

    std::string srtPath_;
    std::string fontPath_;
    int fontSizePx_ = 48;
    bool subtitleEnabled_ = false;
};
