#pragma once

#include <memory>
#include "core/BlendNode.h"
#include "core/OutputNode.h"
#include "core/SourceNode.h"

// RenderGraphBuilder 负责渲染树节点的创建、连接与释放。
// 当前阶段只收口图组装，不改变播放循环的执行顺序。
class RenderGraphBuilder {
public:
    bool ensureOutputNodeInitialized();
    void release();

    void rebuild(SourceNode* primarySource,
                 SourceNode* overlaySource,
                 int surfaceWidth,
                 int surfaceHeight,
                 float overlayAlpha);

    OutputNode* outputNode() const { return outputNode_.get(); }
    BlendNode* blendNode() const { return blendNode_.get(); }

private:
    std::unique_ptr<OutputNode> outputNode_;
    std::unique_ptr<BlendNode> blendNode_;
};
