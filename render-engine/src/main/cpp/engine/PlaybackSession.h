#pragma once

#include <jni.h>
#include <thread>
#include <mutex>
#include <atomic>
#include <condition_variable>
#include <memory>
#include <string>
#include <functional>
#include <android/native_window.h>
#include "gl/EglCore.h"
#include "engine/Timeline.h"
#include "pipeline/AudioPipeline.h"
#include "pipeline/VideoTrackPipeline.h"
#include "render/RenderGraphBuilder.h"
#include "sync/VideoSyncController.h"

class PlaybackSession {
public:
    explicit PlaybackSession(JavaVM* jvm);
    ~PlaybackSession();

    void setSurface(ANativeWindow* window);
    bool setVideoSource(const std::string& filePath);
    bool setTimeline(std::vector<Clip> clips);
    bool setMultiTrackTimeline(std::vector<Clip> primaryClips,
                               std::vector<Clip> overlayClips,
                               float overlayAlpha);

    void play();
    void pause();
    void seek(int64_t positionUs);
    void seekFast(int64_t positionUs);

    int64_t getDuration() const;
    int64_t getPosition() const;
    int getVideoWidth() const;
    int getVideoHeight() const;

    void setOverlayAlpha(float alpha);
    void setPlaybackCompletedHandler(std::function<void()> handler);

    void setSubtitle(const std::string& srtPath,
                     const std::string& fontPath,
                     int fontSizePx);
    void setSubtitleEnabled(bool enabled);

private:
    enum class SeekMode {
        FastPreview,
        ExactFrame
    };

    struct RenderLoopContext {
        JNIEnv* env = nullptr;
        int surfaceWidth = 0;
        int surfaceHeight = 0;
        bool eof = false;
        VideoSyncController syncController;
        int64_t lastVideoDiagLogUs = 0;
        int64_t lastPerfDiagLogUs = 0;
    };

    struct FrameTickContext {
        HwDecoder::DecodedFrame decodedFrame{};
        int64_t framePtsUs = 0;
        int64_t globalPosUs = 0;
        int64_t nominalDurationUs = 0;
        int64_t audioClockUs = -1;
    };

    struct FramePerfCounters {
        int64_t perfCycleStartUs = 0;
        int64_t feedStartUs = 0;
        int64_t feedEndUs = 0;
        int64_t dequeueStartUs = 0;
        int64_t dequeueEndUs = 0;
        int64_t waitStartUs = 0;
        int64_t waitEndUs = 0;
        int64_t updateStartUs = 0;
        int64_t updateEndUs = 0;
        int64_t executeStartUs = 0;
        int64_t executeEndUs = 0;
        int64_t swapStartUs = 0;
        int64_t swapEndUs = 0;
    };

    void renderThreadFunc();
    bool attachThreadAndInitEgl(RenderLoopContext& ctx);
    void teardownRenderThread(RenderLoopContext& ctx);

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
    int64_t getAudioClockUs() const;

    bool handleSurfaceLifecycle(RenderLoopContext& ctx);
    bool handleSourceLifecycle(RenderLoopContext& ctx);
    bool handlePendingFastSeek(RenderLoopContext& ctx);
    bool handlePendingExactSeek(RenderLoopContext& ctx);
    bool handleIdleState(RenderLoopContext& ctx);
    bool handleTimelineEof(RenderLoopContext& ctx);
    bool restartTimeline(RenderLoopContext& ctx);

    void processPlaybackTick(RenderLoopContext& ctx);
    bool feedAndDecodePrimaryFrame(RenderLoopContext& ctx,
                                   FrameTickContext& frameCtx,
                                   FramePerfCounters& perf);
    void updateOverlayForPlayback(RenderLoopContext& ctx, int64_t timelineUs);
    VideoSyncDecision syncAndPresent(RenderLoopContext& ctx,
                                     FrameTickContext& frameCtx,
                                     FramePerfCounters& perf);
    void emitDiagnostics(RenderLoopContext& ctx,
                         const FrameTickContext& frameCtx,
                         const FramePerfCounters& perf,
                         const VideoSyncDecision& decision);

    bool executeSeekToTimelinePosition(RenderLoopContext& ctx, int64_t targetUs, SeekMode mode);
    bool seekPrimaryTrackTo(RenderLoopContext& ctx,
                            int64_t targetUs,
                            SeekMode mode,
                            int64_t& sourceTargetUs);
    bool acquireSeekFrame(RenderLoopContext& ctx,
                          int64_t sourceTargetUs,
                          SeekMode mode,
                          FrameTickContext& frameCtx,
                          int& skipCount);
    void presentSeekFrame(RenderLoopContext& ctx,
                          const FrameTickContext& frameCtx,
                          SeekMode mode,
                          int64_t requestedTimelineUs);
    void updateOverlayForSeek(RenderLoopContext& ctx, int64_t targetUs, SeekMode mode);

    JavaVM* jvm_;
    EglCore eglCore_;
    EGLSurface eglSurface_ = EGL_NO_SURFACE;

    ANativeWindow* window_ = nullptr;
    std::mutex windowMutex_;
    std::atomic<bool> windowChanged_{false};

    std::mutex videoSourceMutex_;
    std::atomic<bool> videoSourceChanged_{false};

    std::unique_ptr<VideoTrackPipeline> primaryTrack_;
    std::unique_ptr<VideoTrackPipeline> overlayTrack_;
    std::atomic<float> overlayAlpha_{0.5f};
    bool hasOverlay_ = false;

    std::unique_ptr<RenderGraphBuilder> renderGraphBuilder_;
    bool pipelineInitialized_ = false;
    std::unique_ptr<AudioPipeline> audioPipeline_;

    std::thread renderThread_;
    std::atomic<bool> running_{false};

    std::atomic<bool> playing_{false};
    std::atomic<int64_t> currentPositionUs_{0};
    std::atomic<int64_t> seekTargetUs_{-1};
    std::atomic<int64_t> seekFastTargetUs_{-1};
    std::atomic<uint32_t> timelineGeneration_{1};
    std::atomic<bool> timelineTransitioning_{false};
    std::mutex cmdMutex_;
    std::condition_variable cmdCond_;

    std::function<void()> playbackCompletedHandler_;

    std::mutex subtitleMutex_;
    std::string subtitlePath_;
    std::string subtitleFontPath_;
    int subtitleFontSizePx_ = 48;
    std::atomic<bool> subtitleEnabled_{false};
    std::atomic<bool> subtitleConfigChanged_{false};
};
