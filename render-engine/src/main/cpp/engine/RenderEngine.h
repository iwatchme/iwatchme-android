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
#include "decode/AudioDecoder.h"
#include "decode/SurfaceTextureHelper.h"
#include "audio/AudioOutput.h"
#include "core/SourceNode.h"
#include "core/OutputNode.h"
#include "core/BlendNode.h"
#include "engine/Timeline.h"

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
    bool setTimeline(std::vector<Clip> clips);
    bool setMultiTrackTimeline(std::vector<Clip> primaryClips,
                               std::vector<Clip> overlayClips,
                               float overlayAlpha);

    void play();
    void pause();
    void seek(int64_t positionUs);       // 精确 seek：解码到目标帧（松手时调用）
    void seekFast(int64_t positionUs);   // 快速 seek：只显示最近关键帧（拖动中调用）

    int64_t getDuration() const;
    int64_t getPosition() const;
    int getVideoWidth() const;
    int getVideoHeight() const;

    void setOverlayAlpha(float alpha);
    void setCallback(PlaybackCallback* cb) { callback_ = cb; }

private:
    void renderThreadFunc();
    void audioThreadFunc();
    void startRenderThread();
    void stopRenderThread();
    uint32_t beginTimelineTransition();
    void endTimelineTransition(uint32_t generation);
    bool switchToClip(int clipIndex, int64_t sourcePositionUs, JNIEnv* env);

    bool initDecodePipeline(JNIEnv* env);
    void releaseDecodePipeline(JNIEnv* env);
    bool initOverlayPipeline(JNIEnv* env, int surfaceWidth, int surfaceHeight);
    void releaseOverlayPipeline(JNIEnv* env);
    void buildRenderTree(int surfaceWidth, int surfaceHeight);

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

    // 主轨道时间线
    Timeline timeline_;
    int activeClipIndex_ = -1;
    DecoderConfig activeDecoderConfig_;
    int64_t skipUntilPtsUs_ = 0;

    // 叠加轨道（overlay track）
    Demuxer overlayDemuxer_;
    HwDecoder overlayHwDecoder_;
    SurfaceTextureHelper overlayStHelper_;
    SourceNode* overlaySourceNode_ = nullptr;
    BlendNode* blendNode_ = nullptr;
    Timeline overlayTimeline_;
    int overlayActiveClipIndex_ = -1;
    DecoderConfig overlayDecoderConfig_;
    int64_t overlaySkipUntilPtsUs_ = 0;
    bool overlayInitialized_ = false;
    std::atomic<float> overlayAlpha_{0.5f};
    bool hasOverlay_ = false;  // 是否设置了叠加轨道
    AVPacket* overlayPkt_ = nullptr;
    bool overlayHasPendingPkt_ = false;
    bool overlayEof_ = false;

    // 视频解码
    HwDecoder hwDecoder_;
    SurfaceTextureHelper stHelper_;
    SourceNode* sourceNode_ = nullptr;
    OutputNode* outputNode_ = nullptr;
    bool pipelineInitialized_ = false;

    // 音频解码 + 输出
    AudioDecoder audioDecoder_;
    AudioOutput audioOutput_;
    std::thread audioThread_;
    std::atomic<bool> audioRunning_{false};
    bool hasAudio_ = false;

    // 音频时钟：A/V 同步的主时钟（微秒）
    // 当有音频时，视频帧节奏由此驱动；无音频时回退到系统时钟
    int64_t getAudioClockUs() const;

    std::thread renderThread_;
    std::atomic<bool> running_{false};

    std::atomic<bool> playing_{false};
    std::atomic<int64_t> currentPositionUs_{0};
    std::atomic<int64_t> seekTargetUs_{-1};      // 精确 seek 目标
    std::atomic<int64_t> seekFastTargetUs_{-1};  // 快速 seek 目标（关键帧级别）
    std::atomic<bool> seekRequested_{false};      // 通知音频线程需要 seek
    std::atomic<int64_t> audioSeekTargetUs_{-1};  // 音频 seek 目标
    std::atomic<uint32_t> timelineGeneration_{1};
    std::atomic<bool> timelineTransitioning_{false};
    std::mutex cmdMutex_;
    std::condition_variable cmdCond_;

    PlaybackCallback* callback_ = nullptr;
};
