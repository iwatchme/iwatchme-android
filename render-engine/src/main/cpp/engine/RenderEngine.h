#pragma once

#include <jni.h>
#include <thread>
#include <mutex>
#include <atomic>
#include <condition_variable>
#include <string>
#include <android/native_window.h>
#include "gl/EglCore.h"
#include "decode/Demuxer.h"
#include "decode/HwDecoder.h"
#include "decode/SurfaceTextureHelper.h"
#include "core/SourceNode.h"
#include "core/OutputNode.h"

// 播放事件回调接口
class PlaybackCallback {
public:
    virtual ~PlaybackCallback() = default;
    virtual void onPlaybackCompleted() = 0;
};

class RenderEngine {
public:
    explicit RenderEngine(JavaVM* jvm);
    ~RenderEngine();

    void setSurface(ANativeWindow* window);
    bool setVideoSource(const std::string& filePath);

    void play();
    void pause();
    void seek(int64_t positionUs);       // 精确 seek：解码到目标帧（松手时调用）
    void seekFast(int64_t positionUs);   // 快速 seek：只显示最近关键帧（拖动中调用）

    int64_t getDuration() const;
    int64_t getPosition() const;
    int getVideoWidth() const;
    int getVideoHeight() const;

    void setCallback(PlaybackCallback* cb) { callback_ = cb; }

private:
    void renderThreadFunc();
    void startRenderThread();
    void stopRenderThread();

    bool initDecodePipeline(JNIEnv* env);
    void releaseDecodePipeline(JNIEnv* env);

    JavaVM* jvm_;
    EglCore eglCore_;
    EGLSurface eglSurface_ = EGL_NO_SURFACE;

    ANativeWindow* window_ = nullptr;
    std::mutex windowMutex_;
    std::atomic<bool> windowChanged_{false};

    Demuxer demuxer_;
    std::string videoPath_;
    std::mutex videoSourceMutex_;
    std::atomic<bool> videoSourceChanged_{false};

    HwDecoder hwDecoder_;
    SurfaceTextureHelper stHelper_;
    SourceNode* sourceNode_ = nullptr;
    OutputNode* outputNode_ = nullptr;
    bool pipelineInitialized_ = false;

    std::thread renderThread_;
    std::atomic<bool> running_{false};

    std::atomic<bool> playing_{false};
    std::atomic<int64_t> currentPositionUs_{0};
    std::atomic<int64_t> seekTargetUs_{-1};      // 精确 seek 目标
    std::atomic<int64_t> seekFastTargetUs_{-1};  // 快速 seek 目标（关键帧级别）
    std::mutex cmdMutex_;
    std::condition_variable cmdCond_;

    PlaybackCallback* callback_ = nullptr;
};
