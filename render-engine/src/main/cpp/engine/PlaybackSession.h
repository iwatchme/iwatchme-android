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

private:
    void renderThreadFunc();
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
};
