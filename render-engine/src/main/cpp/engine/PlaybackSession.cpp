#include "engine/PlaybackSession.h"
#include "common/log.h"
#include "engine/TimelineValidator.h"
#include "sync/VideoSyncController.h"
#include <GLES3/gl3.h>
#include <unistd.h>
#include <chrono>
#include <algorithm>

extern "C" {
#include <libavcodec/avcodec.h>
}

namespace {
constexpr int64_t kAvSyncDiagIntervalUs = 500000;

int64_t steadyNowUs() {
    return std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}
}

PlaybackSession::PlaybackSession(JavaVM* jvm)
    : jvm_(jvm),
      primaryTrack_(std::make_unique<VideoTrackPipeline>()),
      overlayTrack_(std::make_unique<VideoTrackPipeline>()),
      renderGraphBuilder_(std::make_unique<RenderGraphBuilder>()),
      audioPipeline_(std::make_unique<AudioPipeline>()) {
    overlayTrack_->setAudioPacketCachingEnabled(false);
    audioPipeline_->bindContext(primaryTrack_.get(),
                                &running_,
                                &playing_,
                                &timelineTransitioning_,
                                &timelineGeneration_,
                                &currentPositionUs_);
    LOGI("PlaybackSession created");
    startRenderThread();
}

PlaybackSession::~PlaybackSession() {
    stopRenderThread();
    LOGI("PlaybackSession destroyed");
}

void PlaybackSession::setSurface(ANativeWindow* window) {
    std::lock_guard<std::mutex> lock(windowMutex_);
    if (window_) ANativeWindow_release(window_);
    window_ = window;
    if (window_) ANativeWindow_acquire(window_);
    windowChanged_.store(true);
    cmdCond_.notify_one();
    LOGI("PlaybackSession: surface %s", window ? "set" : "cleared");
}

bool PlaybackSession::setVideoSource(const std::string& filePath) {
    Clip c;
    c.sourcePath = filePath;
    c.trimIn = 0;
    c.trimOut = -1;
    std::vector<Clip> clips;
    clips.push_back(std::move(c));
    return setTimeline(std::move(clips));
}

void PlaybackSession::play() {
    playing_.store(true);
    if (audioPipeline_ && audioPipeline_->hasAudio()) audioPipeline_->resume();
    cmdCond_.notify_one();
    LOGI("PlaybackSession: play");
}

void PlaybackSession::pause() {
    playing_.store(false);
    if (audioPipeline_ && audioPipeline_->hasAudio()) audioPipeline_->pause();
    LOGI("PlaybackSession: pause");
}

void PlaybackSession::seek(int64_t positionUs) {
    uint32_t generation = beginTimelineTransition();
    seekTargetUs_.store(positionUs);
    seekFastTargetUs_.store(-1);
    if (audioPipeline_) audioPipeline_->notifySeek(positionUs);
    cmdCond_.notify_one();
    LOGI("AVSYNC transition seek generation=%u targetUs=%lld",
         generation, (long long)positionUs);
}

void PlaybackSession::seekFast(int64_t positionUs) {
    uint32_t generation = beginTimelineTransition();
    seekFastTargetUs_.store(positionUs);
    if (audioPipeline_) audioPipeline_->notifySeek(positionUs);
    cmdCond_.notify_one();
    LOGI("AVSYNC transition seekFast generation=%u targetUs=%lld",
         generation, (long long)positionUs);
}

void PlaybackSession::setOverlayAlpha(float alpha) {
    overlayAlpha_.store(alpha);
}

int64_t PlaybackSession::getDuration() const {
    int64_t primary = primaryTrack_ ? primaryTrack_->durationUs() : 0;
    if (hasOverlay_ && overlayTrack_ && !overlayTrack_->timeline().isEmpty()) {
        int64_t overlay = overlayTrack_->durationUs();
        return std::max(primary, overlay);
    }
    return primary;
}

int64_t PlaybackSession::getPosition() const { return currentPositionUs_.load(); }
int PlaybackSession::getVideoWidth() const { return primaryTrack_ ? primaryTrack_->videoWidth() : 0; }
int PlaybackSession::getVideoHeight() const { return primaryTrack_ ? primaryTrack_->videoHeight() : 0; }

void PlaybackSession::setPlaybackCompletedHandler(std::function<void()> handler) {
    playbackCompletedHandler_ = std::move(handler);
}

int64_t PlaybackSession::getAudioClockUs() const {
    return audioPipeline_ ? audioPipeline_->getAudioClockUs() : -1;
}

uint32_t PlaybackSession::beginTimelineTransition() {
    timelineTransitioning_.store(true);
    return timelineGeneration_.fetch_add(1) + 1;
}

void PlaybackSession::endTimelineTransition(uint32_t generation) {
    if (timelineGeneration_.load() == generation) {
        timelineTransitioning_.store(false);
    }
}

bool PlaybackSession::switchToClip(int clipIndex, int64_t sourcePositionUs, JNIEnv* env) {
    if (!primaryTrack_) return false;
    if (clipIndex < 0 || clipIndex >= primaryTrack_->timeline().clipCount()) return false;
    const Clip& clip = primaryTrack_->timeline().clipAt(clipIndex);
    LOGI("PlaybackSession: switchToClip %d -> %d, sourcePos=%lld, path=%s",
         primaryTrack_->activeClipIndex(), clipIndex, (long long)sourcePositionUs, clip.sourcePath.c_str());

    if (audioPipeline_) audioPipeline_->stopAndJoin();

    if (!primaryTrack_->switchToClip(clipIndex, sourcePositionUs, env)) {
        LOGE("switchToClip: primaryTrack switch failed");
        return false;
    }

    if (audioPipeline_) {
        audioPipeline_->configureFromTrack(primaryTrack_.get());
        if (audioPipeline_->hasAudio()) {
            audioPipeline_->start();
        }
    }

    LOGI("AVSYNC clip-switch clipIndex=%d sourcePos=%lld audio=%d",
         clipIndex,
         (long long)sourcePositionUs,
         (audioPipeline_ && audioPipeline_->hasAudio()) ? 1 : 0);
    return true;
}

bool PlaybackSession::setTimeline(std::vector<Clip> clips) {
    uint32_t generation = beginTimelineTransition();

    if (!TimelineValidator::completeAndValidate(clips, "PlaybackSession: setTimeline")) {
        return false;
    }

    {
        std::lock_guard<std::mutex> lock(videoSourceMutex_);
        Timeline timeline;
        timeline.setClips(std::move(clips));
        primaryTrack_->configureTimeline(std::move(timeline));
        videoSourceChanged_.store(true);
    }

    cmdCond_.notify_one();
    LOGI("PlaybackSession: timeline set %d clips, duration=%.2fs, generation=%u",
         primaryTrack_->timeline().clipCount(),
         primaryTrack_->timeline().durationUs() / 1000000.0,
         generation);
    return true;
}

bool PlaybackSession::setMultiTrackTimeline(std::vector<Clip> primaryClips,
                                            std::vector<Clip> overlayClips,
                                            float overlayAlpha) {
    uint32_t generation = beginTimelineTransition();

    if (!TimelineValidator::completeAndValidate(primaryClips, "setMultiTrackTimeline(primary)")) {
        return false;
    }
    if (!TimelineValidator::completeAndValidate(overlayClips, "setMultiTrackTimeline(overlay)")) {
        return false;
    }

    {
        std::lock_guard<std::mutex> lock(videoSourceMutex_);
        Timeline primaryTimeline;
        primaryTimeline.setClips(std::move(primaryClips));
        primaryTrack_->configureTimeline(std::move(primaryTimeline));
        if (!overlayClips.empty()) {
            Timeline overlayTimeline;
            overlayTimeline.setClips(std::move(overlayClips));
            overlayTrack_->configureTimeline(std::move(overlayTimeline));
            overlayAlpha_ = overlayAlpha;
            hasOverlay_ = true;
        } else {
            overlayTrack_->configureTimeline(Timeline{});
            hasOverlay_ = false;
        }
        videoSourceChanged_.store(true);
    }

    cmdCond_.notify_one();
    LOGI("setMultiTrackTimeline: primary=%d clips, overlay=%s (alpha=%.2f), generation=%u",
         primaryTrack_->timeline().clipCount(),
         hasOverlay_ ? std::to_string(overlayTrack_->timeline().clipCount()).c_str() : "none",
         overlayAlpha_.load(), generation);
    return true;
}

void PlaybackSession::startRenderThread() {
    running_.store(true);
    renderThread_ = std::thread(&PlaybackSession::renderThreadFunc, this);
}

void PlaybackSession::stopRenderThread() {
    running_.store(false);
    playing_.store(false);
    cmdCond_.notify_one();
    if (audioPipeline_) audioPipeline_->stopAndJoin();
    if (renderThread_.joinable()) renderThread_.join();
}

bool PlaybackSession::initDecodePipeline(JNIEnv* env) {
    releaseDecodePipeline(env);

    if (!primaryTrack_ || primaryTrack_->timeline().isEmpty()) {
        LOGE("PlaybackSession: initDecodePipeline: timeline is empty");
        return false;
    }

    if (!primaryTrack_->init(env)) {
        LOGE("PlaybackSession: primary track init failed");
        return false;
    }

    int w = primaryTrack_->videoWidth();
    int h = primaryTrack_->videoHeight();

    if (!renderGraphBuilder_ || !renderGraphBuilder_->ensureOutputNodeInitialized()) {
        return false;
    }

    if (hasOverlay_ && overlayTrack_ && !overlayTrack_->timeline().isEmpty()) {
        if (!initOverlayPipeline(env, w, h)) {
            LOGW("PlaybackSession: overlay init failed, proceeding with primary only");
        }
    }

    if (audioPipeline_) {
        audioPipeline_->bindContext(primaryTrack_.get(),
                                    &running_,
                                    &playing_,
                                    &timelineTransitioning_,
                                    &timelineGeneration_,
                                    &currentPositionUs_);
        audioPipeline_->configureFromTrack(primaryTrack_.get());
        if (audioPipeline_->hasAudio()) {
            audioPipeline_->start();
            LOGI("PlaybackSession: audio pipeline initialized");
        }
    }

    currentPositionUs_.store(0);
    pipelineInitialized_ = true;
    LOGI("PlaybackSession: pipeline initialized %dx%d, audio=%s, clip=%d/%d",
         w, h, (audioPipeline_ && audioPipeline_->hasAudio()) ? "yes" : "no",
         primaryTrack_->activeClipIndex(),
         primaryTrack_->timeline().clipCount());
    return true;
}

void PlaybackSession::releaseDecodePipeline(JNIEnv* env) {
    if (!pipelineInitialized_) return;

    if (audioPipeline_) audioPipeline_->release();
    releaseOverlayPipeline(env);

    if (renderGraphBuilder_) {
        renderGraphBuilder_->release();
    }
    if (primaryTrack_) {
        primaryTrack_->release(env);
    }

    pipelineInitialized_ = false;
    LOGI("PlaybackSession: pipeline released");
}

bool PlaybackSession::initOverlayPipeline(JNIEnv* env, int surfaceWidth, int surfaceHeight) {
    (void)surfaceWidth;
    (void)surfaceHeight;
    releaseOverlayPipeline(env);

    if (!hasOverlay_ || !overlayTrack_ || overlayTrack_->timeline().isEmpty()) return false;
    if (!overlayTrack_->init(env)) {
        return false;
    }
    if (overlayTrack_->sourceNode()) {
        overlayTrack_->sourceNode()->setActive(false);
    }
    LOGI("initOverlayPipeline: initialized %dx%d, alpha=%.2f",
         overlayTrack_->videoWidth(),
         overlayTrack_->videoHeight(),
         overlayAlpha_.load());
    return true;
}

void PlaybackSession::releaseOverlayPipeline(JNIEnv* env) {
    if (!overlayTrack_ || !overlayTrack_->sourceNode()) return;
    overlayTrack_->release(env);
    LOGI("releaseOverlayPipeline: released");
}

void PlaybackSession::buildRenderTree(int surfaceWidth, int surfaceHeight) {
    if (!primaryTrack_ || !primaryTrack_->sourceNode() || !renderGraphBuilder_) return;
    renderGraphBuilder_->rebuild(
        primaryTrack_->sourceNode(),
        (hasOverlay_ && overlayTrack_) ? overlayTrack_->sourceNode() : nullptr,
        surfaceWidth,
        surfaceHeight,
        overlayAlpha_.load());
}

void PlaybackSession::renderThreadFunc() {
    RenderLoopContext ctx;
    if (!attachThreadAndInitEgl(ctx)) {
        return;
    }

    while (running_.load()) {
        if (handleSurfaceLifecycle(ctx)) continue;
        if (handleSourceLifecycle(ctx)) continue;
        if (handlePendingFastSeek(ctx)) continue;
        if (handlePendingExactSeek(ctx)) continue;
        if (handleIdleState(ctx)) continue;
        if (handleTimelineEof(ctx)) continue;

        processPlaybackTick(ctx);
    }

    teardownRenderThread(ctx);
}

bool PlaybackSession::attachThreadAndInitEgl(RenderLoopContext& ctx) {
    jvm_->AttachCurrentThread(&ctx.env, nullptr);

    if (!eglCore_.init()) {
        LOGE("PlaybackSession: EGL init failed");
        jvm_->DetachCurrentThread();
        ctx.env = nullptr;
        return false;
    }

    LOGI("PlaybackSession: render thread started");
    return true;
}

void PlaybackSession::teardownRenderThread(RenderLoopContext& ctx) {
    releaseDecodePipeline(ctx.env);

    if (eglSurface_ != EGL_NO_SURFACE) {
        eglCore_.makeNothingCurrent();
        eglCore_.destroySurface(eglSurface_);
        eglSurface_ = EGL_NO_SURFACE;
    }
    eglCore_.release();

    {
        std::lock_guard<std::mutex> lock(windowMutex_);
        if (window_) {
            ANativeWindow_release(window_);
            window_ = nullptr;
        }
    }

    if (ctx.env) {
        jvm_->DetachCurrentThread();
        ctx.env = nullptr;
    }
    LOGI("PlaybackSession: render thread stopped");
}

bool PlaybackSession::handleSurfaceLifecycle(RenderLoopContext& ctx) {
    if (!windowChanged_.load()) {
        return false;
    }

    windowChanged_.store(false);

    if (eglSurface_ != EGL_NO_SURFACE) {
        eglCore_.makeNothingCurrent();
        eglCore_.destroySurface(eglSurface_);
        eglSurface_ = EGL_NO_SURFACE;
    }

    std::lock_guard<std::mutex> lock(windowMutex_);
    if (window_) {
        eglSurface_ = eglCore_.createWindowSurface(window_);
        if (eglSurface_ != EGL_NO_SURFACE) {
            eglCore_.makeCurrent(eglSurface_);
            ctx.surfaceWidth = ANativeWindow_getWidth(window_);
            ctx.surfaceHeight = ANativeWindow_getHeight(window_);
            glViewport(0, 0, ctx.surfaceWidth, ctx.surfaceHeight);
            LOGI("PlaybackSession: EGL surface %dx%d", ctx.surfaceWidth, ctx.surfaceHeight);
        }
    }

    return true;
}

bool PlaybackSession::handleSourceLifecycle(RenderLoopContext& ctx) {
    if (!videoSourceChanged_.load() || eglSurface_ == EGL_NO_SURFACE) {
        return false;
    }

    uint32_t transitionGeneration = timelineGeneration_.load();
    videoSourceChanged_.store(false);
    if (initDecodePipeline(ctx.env)) {
        buildRenderTree(ctx.surfaceWidth, ctx.surfaceHeight);
        ctx.syncController.reset();
        ctx.eof = false;
        LOGI("PlaybackSession: ready, fps=%.1f, overlay=%s",
             primaryTrack_->fps(),
             (hasOverlay_ && overlayTrack_ && overlayTrack_->sourceNode()) ? "yes" : "no");
        LOGI("AVSYNC transition source-ready generation=%u audio=%d",
             transitionGeneration,
             (audioPipeline_ && audioPipeline_->hasAudio()) ? 1 : 0);
    }
    endTimelineTransition(transitionGeneration);
    return true;
}

bool PlaybackSession::handlePendingFastSeek(RenderLoopContext& ctx) {
    int64_t fastTarget = seekFastTargetUs_.exchange(-1);
    if (fastTarget < 0 || !pipelineInitialized_) {
        return false;
    }

    uint32_t transitionGeneration = timelineGeneration_.load();
    executeSeekToTimelinePosition(ctx, fastTarget, SeekMode::FastPreview);
    if (seekFastTargetUs_.load() >= 0) {
        return true;
    }
    endTimelineTransition(transitionGeneration);
    return true;
}

bool PlaybackSession::handlePendingExactSeek(RenderLoopContext& ctx) {
    int64_t seekTarget = seekTargetUs_.exchange(-1);
    if (seekTarget < 0 || !pipelineInitialized_) {
        return false;
    }

    uint32_t transitionGeneration = timelineGeneration_.load();
    executeSeekToTimelinePosition(ctx, seekTarget, SeekMode::ExactFrame);
    endTimelineTransition(transitionGeneration);
    return true;
}

bool PlaybackSession::handleIdleState(RenderLoopContext& ctx) {
    if (playing_.load() && eglSurface_ != EGL_NO_SURFACE && pipelineInitialized_) {
        return false;
    }

    ctx.syncController.reset();
    std::unique_lock<std::mutex> lock(cmdMutex_);
    cmdCond_.wait_for(lock, std::chrono::milliseconds(50));
    return true;
}

bool PlaybackSession::handleTimelineEof(RenderLoopContext& ctx) {
    if (!ctx.eof) {
        return false;
    }

    int next = primaryTrack_->activeClipIndex() + 1;
    if (primaryTrack_ && !primaryTrack_->timeline().isEmpty() &&
        next < primaryTrack_->timeline().clipCount()) {
        uint32_t gen = beginTimelineTransition();
        switchToClip(next, primaryTrack_->timeline().clipAt(next).trimIn, ctx.env);
        ctx.syncController.reset();
        ctx.eof = false;
        endTimelineTransition(gen);
        LOGI("PlaybackSession: clip EOF -> switch to clip %d", next);
        return true;
    }

    return restartTimeline(ctx);
}

bool PlaybackSession::restartTimeline(RenderLoopContext& ctx) {
    uint32_t transitionGeneration = beginTimelineTransition();
    if (primaryTrack_ && !primaryTrack_->timeline().isEmpty() &&
        primaryTrack_->timeline().clipCount() > 1) {
        switchToClip(0, primaryTrack_->timeline().clipAt(0).trimIn, ctx.env);
    } else {
        int64_t startPos = (primaryTrack_ && !primaryTrack_->timeline().isEmpty())
            ? primaryTrack_->timeline().clipAt(0).trimIn
            : 0;
        if (audioPipeline_) audioPipeline_->notifySeek(startPos);
        if (primaryTrack_) {
            primaryTrack_->seekInCurrentClip(startPos);
        }
        if (audioPipeline_ && audioPipeline_->hasAudio()) audioPipeline_->flush();
    }

    if (hasOverlay_ && overlayTrack_ && !overlayTrack_->timeline().isEmpty()) {
        const Clip& oFirst = overlayTrack_->timeline().clipAt(0);
        if (overlayTrack_->activeClipIndex() != 0) {
            overlayTrack_->switchToClip(0, oFirst.trimIn, ctx.env);
        } else {
            overlayTrack_->seekInCurrentClip(oFirst.trimIn);
        }
        if (overlayTrack_->sourceNode()) {
            overlayTrack_->sourceNode()->setActive(false);
        }
    }

    currentPositionUs_.store(0);
    ctx.syncController.reset();
    ctx.eof = false;
    endTimelineTransition(transitionGeneration);
    LOGI("PlaybackSession: restart from beginning");
    return true;
}

void PlaybackSession::processPlaybackTick(RenderLoopContext& ctx) {
    FrameTickContext frameCtx;
    FramePerfCounters perf;
    if (!feedAndDecodePrimaryFrame(ctx, frameCtx, perf)) {
        return;
    }

    updateOverlayForPlayback(ctx, frameCtx.globalPosUs);
    VideoSyncDecision decision = syncAndPresent(ctx, frameCtx, perf);
    emitDiagnostics(ctx, frameCtx, perf, decision);

    if (!ctx.eof) {
        return;
    }

    int next = primaryTrack_->activeClipIndex() + 1;
    if (!primaryTrack_ || primaryTrack_->timeline().isEmpty() ||
        next >= primaryTrack_->timeline().clipCount()) {
        playing_.store(false);
        if (audioPipeline_ && audioPipeline_->hasAudio()) audioPipeline_->pause();
        LOGI("PlaybackSession: playback completed (EOF)");
        if (playbackCompletedHandler_) {
            playbackCompletedHandler_();
        }
    } else {
        LOGI("PlaybackSession: clip %d EOF, next clip %d pending",
             primaryTrack_->activeClipIndex(), next);
    }
}

bool PlaybackSession::feedAndDecodePrimaryFrame(RenderLoopContext& ctx,
                                                FrameTickContext& frameCtx,
                                                FramePerfCounters& perf) {
    AVRational tb = primaryTrack_->videoTimeBase();
    double fps = primaryTrack_->fps();
    frameCtx.nominalDurationUs = (fps > 0) ? static_cast<int64_t>(1000000.0 / fps) : 33333;

    perf.perfCycleStartUs = steadyNowUs();
    perf.feedStartUs = perf.perfCycleStartUs;
    primaryTrack_->pumpAvailablePackets();
    ctx.eof = primaryTrack_->isEof();
    perf.feedEndUs = steadyNowUs();

    perf.dequeueStartUs = perf.feedEndUs;
    if (!primaryTrack_->dequeueFrame(frameCtx.decodedFrame, 30000)) {
        perf.dequeueEndUs = steadyNowUs();
        return false;
    }
    perf.dequeueEndUs = steadyNowUs();

    frameCtx.framePtsUs = av_rescale_q(frameCtx.decodedFrame.pts, tb, {1, 1000000});
    if (primaryTrack_->isFrameBeforeTrim(frameCtx.framePtsUs)) {
        primaryTrack_->releaseFrame(frameCtx.decodedFrame.bufferIndex, false);
        return false;
    }

    if (primaryTrack_->isFrameAfterTrim(frameCtx.framePtsUs)) {
        primaryTrack_->releaseFrame(frameCtx.decodedFrame.bufferIndex, false);
        int next = primaryTrack_->activeClipIndex() + 1;
        if (next < primaryTrack_->timeline().clipCount()) {
            uint32_t gen = beginTimelineTransition();
            switchToClip(next, primaryTrack_->timeline().clipAt(next).trimIn, ctx.env);
            ctx.syncController.reset();
            ctx.eof = false;
            endTimelineTransition(gen);
        } else {
            ctx.eof = true;
        }
        return false;
    }

    primaryTrack_->releaseFrame(frameCtx.decodedFrame.bufferIndex, true);
    perf.waitStartUs = perf.dequeueEndUs;
    primaryTrack_->consumeRenderedFrame(ctx.env, 50);
    perf.waitEndUs = steadyNowUs();

    perf.updateStartUs = perf.waitEndUs;
    frameCtx.globalPosUs = primaryTrack_->mapSourcePtsToTimelineUs(frameCtx.framePtsUs);
    currentPositionUs_.store(frameCtx.globalPosUs);
    perf.updateEndUs = steadyNowUs();
    return true;
}

void PlaybackSession::updateOverlayForPlayback(RenderLoopContext& ctx, int64_t timelineUs) {
    if (!hasOverlay_ || !overlayTrack_ || !overlayTrack_->sourceNode()) {
        return;
    }

    do {
        ClipLookup oLookup = overlayTrack_->timeline().resolve(timelineUs);
        if (oLookup.clipIndex < 0) {
            overlayTrack_->sourceNode()->setActive(false);
            break;
        }

        if (oLookup.clipIndex != overlayTrack_->activeClipIndex()) {
            if (!overlayTrack_->switchToClip(oLookup.clipIndex, oLookup.sourcePositionUs, ctx.env)) {
                overlayTrack_->sourceNode()->setActive(false);
                break;
            }
        }

        if (!overlayTrack_->isEof()) {
            for (int i = 0; i < 3; i++) {
                if (!overlayTrack_->pumpAvailablePackets()) {
                    break;
                }
            }
        }

        HwDecoder::DecodedFrame oFrame;
        if (overlayTrack_->dequeueFrame(oFrame, 5000)) {
            int64_t oPtsUs = av_rescale_q(oFrame.pts, overlayTrack_->videoTimeBase(), {1, 1000000});

            if (overlayTrack_->isFrameBeforeTrim(oPtsUs)) {
                overlayTrack_->releaseFrame(oFrame.bufferIndex, false);
            } else if (overlayTrack_->isFrameAfterTrim(oPtsUs)) {
                overlayTrack_->releaseFrame(oFrame.bufferIndex, false);
                int oNext = overlayTrack_->activeClipIndex() + 1;
                if (oNext >= overlayTrack_->timeline().clipCount()) {
                    overlayTrack_->sourceNode()->setActive(false);
                }
            } else {
                overlayTrack_->releaseFrame(oFrame.bufferIndex, true);
                overlayTrack_->consumeRenderedFrame(ctx.env, 30);
                overlayTrack_->sourceNode()->setActive(true);
            }
        }
    } while (false);
}

VideoSyncDecision PlaybackSession::syncAndPresent(RenderLoopContext& ctx,
                                                  FrameTickContext& frameCtx,
                                                  FramePerfCounters& perf) {
    frameCtx.audioClockUs = getAudioClockUs();
    int64_t wallNowUs = steadyNowUs();
    VideoSyncDecision preparedDecision = ctx.syncController.prepareFrame(
        frameCtx.framePtsUs,
        frameCtx.nominalDurationUs,
        frameCtx.audioClockUs,
        wallNowUs);

    if (preparedDecision.timelineReset) {
        LOGI("AVSYNC video-timeline-reset framePtsUs=%lld audioClockUs=%lld frameTimerUs=%lld wallNowUs=%lld",
             (long long)frameCtx.framePtsUs,
             (long long)(frameCtx.audioClockUs >= 0 ? frameCtx.audioClockUs : -1),
             (long long)preparedDecision.frameTimerUs,
             (long long)wallNowUs);
    }

    if (preparedDecision.sleepUs > 0) {
        usleep((useconds_t)preparedDecision.sleepUs);
    }

    VideoSyncDecision finalizedDecision;
    ctx.syncController.finalizeFrame(
        preparedDecision,
        frameCtx.audioClockUs,
        steadyNowUs(),
        finalizedDecision);

    perf.executeStartUs = steadyNowUs();
    if (finalizedDecision.shouldRender) {
        if (auto* blendNode = renderGraphBuilder_ ? renderGraphBuilder_->blendNode() : nullptr) {
            blendNode->overlayAlpha = overlayAlpha_.load();
        }
        if (auto* outputNode = renderGraphBuilder_ ? renderGraphBuilder_->outputNode() : nullptr) {
            outputNode->outputWidth = ctx.surfaceWidth;
            outputNode->outputHeight = ctx.surfaceHeight;
            outputNode->execute(frameCtx.framePtsUs);
        }
    }
    perf.executeEndUs = steadyNowUs();

    perf.swapStartUs = perf.executeEndUs;
    if (finalizedDecision.shouldRender) {
        eglCore_.swapBuffers(eglSurface_);
    }
    perf.swapEndUs = steadyNowUs();
    return finalizedDecision;
}

void PlaybackSession::emitDiagnostics(RenderLoopContext& ctx,
                                      const FrameTickContext& frameCtx,
                                      const FramePerfCounters& perf,
                                      const VideoSyncDecision& decision) {
    int64_t diagNowUs = steadyNowUs();
    if (diagNowUs - ctx.lastVideoDiagLogUs >= kAvSyncDiagIntervalUs) {
        ctx.lastVideoDiagLogUs = diagNowUs;
        LOGI("AVSYNC video-diag framePtsUs=%lld audioClockUs=%lld diffUs=%lld delayUs=%lld frameDurUs=%lld render=%d drops=%d posUs=%lld",
             (long long)frameCtx.framePtsUs,
             (long long)frameCtx.audioClockUs,
             (long long)decision.diffUs,
             (long long)decision.delayUs,
             (long long)decision.frameDurationUs,
             decision.shouldRender ? 1 : 0,
             decision.consecutiveDrops,
             (long long)currentPositionUs_.load());
    }

    if (diagNowUs - ctx.lastPerfDiagLogUs >= kAvSyncDiagIntervalUs) {
        ctx.lastPerfDiagLogUs = diagNowUs;
        LOGI("AVPERF framePtsUs=%lld audioClockUs=%lld diffUs=%lld feedUs=%lld dequeueUs=%lld waitUs=%lld updateUs=%lld executeUs=%lld swapUs=%lld totalUs=%lld render=%d",
             (long long)frameCtx.framePtsUs,
             (long long)frameCtx.audioClockUs,
             (long long)(frameCtx.audioClockUs >= 0 ? (frameCtx.framePtsUs - frameCtx.audioClockUs) : 0),
             (long long)(perf.feedEndUs - perf.feedStartUs),
             (long long)(perf.dequeueEndUs - perf.dequeueStartUs),
             (long long)(perf.waitEndUs - perf.waitStartUs),
             (long long)(perf.updateEndUs - perf.updateStartUs),
             (long long)(perf.executeEndUs - perf.executeStartUs),
             (long long)(perf.swapEndUs - perf.swapStartUs),
             (long long)(perf.swapEndUs - perf.perfCycleStartUs),
             decision.shouldRender ? 1 : 0);
    }
}

bool PlaybackSession::executeSeekToTimelinePosition(RenderLoopContext& ctx,
                                                    int64_t targetUs,
                                                    SeekMode mode) {
    int64_t sourceTargetUs = targetUs;
    if (!seekPrimaryTrackTo(ctx, targetUs, mode, sourceTargetUs)) {
        return false;
    }

    ctx.syncController.reset();
    ctx.eof = false;

    FrameTickContext frameCtx;
    int skipCount = 0;
    bool gotFrame = acquireSeekFrame(ctx, sourceTargetUs, mode, frameCtx, skipCount);
    if (gotFrame) {
        updateOverlayForSeek(ctx, targetUs, mode);
        presentSeekFrame(ctx, frameCtx, mode, targetUs);
    } else if (mode == SeekMode::ExactFrame) {
        currentPositionUs_.store(targetUs);
    }

    if (mode == SeekMode::FastPreview) {
        LOGI("AVSYNC transition seekFast-ready generation=%u currentPosUs=%lld",
             timelineGeneration_.load(),
             (long long)currentPositionUs_.load());
    } else {
        LOGI("AVSYNC transition seek-ready generation=%u requestedUs=%lld sourceTargetUs=%lld skipCount=%d audioClockUs=%lld",
             timelineGeneration_.load(),
             (long long)targetUs,
             (long long)sourceTargetUs,
             skipCount,
             (long long)getAudioClockUs());
    }
    return gotFrame;
}

bool PlaybackSession::seekPrimaryTrackTo(RenderLoopContext& ctx,
                                         int64_t targetUs,
                                         SeekMode mode,
                                         int64_t& sourceTargetUs) {
    (void)mode;
    sourceTargetUs = targetUs;
    if (!primaryTrack_) {
        return false;
    }

    if (!primaryTrack_->timeline().isEmpty()) {
        ClipLookup lookup = primaryTrack_->timeline().resolve(targetUs);
        if (lookup.clipIndex < 0 && primaryTrack_->timeline().durationUs() > 0) {
            lookup = primaryTrack_->timeline().resolve(primaryTrack_->timeline().durationUs() - 1);
        }
        if (lookup.clipIndex < 0) {
            return false;
        }
        sourceTargetUs = lookup.sourcePositionUs;
        if (lookup.clipIndex != primaryTrack_->activeClipIndex()) {
            return switchToClip(lookup.clipIndex, lookup.sourcePositionUs, ctx.env);
        }
        return primaryTrack_->seekInCurrentClip(lookup.sourcePositionUs);
    }

    return primaryTrack_->seekInCurrentClip(sourceTargetUs);
}

bool PlaybackSession::acquireSeekFrame(RenderLoopContext& ctx,
                                       int64_t sourceTargetUs,
                                       SeekMode mode,
                                       FrameTickContext& frameCtx,
                                       int& skipCount) {
    AVRational tb = primaryTrack_->videoTimeBase();

    if (mode == SeekMode::FastPreview) {
        for (int attempt = 0; attempt < 100 && running_.load(); attempt++) {
            primaryTrack_->queueVideoPacketWithTimeout(50000);

            if (primaryTrack_->dequeueFrame(frameCtx.decodedFrame, 5000)) {
                frameCtx.framePtsUs = av_rescale_q(frameCtx.decodedFrame.pts, tb, {1, 1000000});
                frameCtx.globalPosUs = primaryTrack_->mapSourcePtsToTimelineUs(frameCtx.framePtsUs);
                primaryTrack_->releaseFrame(frameCtx.decodedFrame.bufferIndex, true);
                primaryTrack_->consumeRenderedFrame(ctx.env, 50);
                return true;
            }
        }
        return false;
    }

    while (running_.load()) {
        while (!ctx.eof) {
            if (!primaryTrack_->queueVideoPacketWithTimeout(50000)) {
                if (primaryTrack_->isEof()) {
                    ctx.eof = true;
                }
                break;
            }
        }

        if (!primaryTrack_->dequeueFrame(frameCtx.decodedFrame, 30000)) {
            if (ctx.eof) {
                break;
            }
            continue;
        }

        frameCtx.framePtsUs = av_rescale_q(frameCtx.decodedFrame.pts, tb, {1, 1000000});
        frameCtx.globalPosUs = primaryTrack_->mapSourcePtsToTimelineUs(frameCtx.framePtsUs);
        if (frameCtx.framePtsUs >= sourceTargetUs) {
            primaryTrack_->releaseFrame(frameCtx.decodedFrame.bufferIndex, true);
            primaryTrack_->consumeRenderedFrame(ctx.env, 50);
            return true;
        }

        primaryTrack_->releaseFrame(frameCtx.decodedFrame.bufferIndex, true);
        primaryTrack_->consumeRenderedFrame(ctx.env, 50);
        skipCount++;
    }

    return false;
}

void PlaybackSession::presentSeekFrame(RenderLoopContext& ctx,
                                       const FrameTickContext& frameCtx,
                                       SeekMode mode,
                                       int64_t requestedTimelineUs) {
    if (auto* blendNode = renderGraphBuilder_ ? renderGraphBuilder_->blendNode() : nullptr) {
        blendNode->overlayAlpha = overlayAlpha_.load();
    }
    if (auto* outputNode = renderGraphBuilder_ ? renderGraphBuilder_->outputNode() : nullptr) {
        outputNode->outputWidth = ctx.surfaceWidth;
        outputNode->outputHeight = ctx.surfaceHeight;
        outputNode->execute(frameCtx.framePtsUs);
    }
    eglCore_.swapBuffers(eglSurface_);

    if (mode == SeekMode::FastPreview) {
        currentPositionUs_.store(frameCtx.globalPosUs);
    } else {
        currentPositionUs_.store(requestedTimelineUs);
    }
}

void PlaybackSession::updateOverlayForSeek(RenderLoopContext& ctx,
                                           int64_t targetUs,
                                           SeekMode mode) {
    (void)mode;
    if (!hasOverlay_ || !overlayTrack_ || !overlayTrack_->sourceNode()) {
        return;
    }

    ClipLookup oLookup = overlayTrack_->timeline().resolve(targetUs);
    if (oLookup.clipIndex < 0) {
        overlayTrack_->sourceNode()->setActive(false);
        return;
    }

    bool overlayReady = (oLookup.clipIndex != overlayTrack_->activeClipIndex())
        ? overlayTrack_->switchToClip(oLookup.clipIndex, oLookup.sourcePositionUs, ctx.env)
        : overlayTrack_->seekInCurrentClip(oLookup.sourcePositionUs);
    for (int oa = 0; overlayReady && oa < 50; oa++) {
        overlayTrack_->queueVideoPacketWithTimeout(10000);
        HwDecoder::DecodedFrame oFrame;
        if (overlayTrack_->dequeueFrame(oFrame, 5000)) {
            int64_t oPtsUs = av_rescale_q(oFrame.pts, overlayTrack_->videoTimeBase(), {1, 1000000});
            if (overlayTrack_->isFrameBeforeTrim(oPtsUs)) {
                overlayTrack_->releaseFrame(oFrame.bufferIndex, false);
                continue;
            }
            if (overlayTrack_->isFrameAfterTrim(oPtsUs)) {
                overlayTrack_->releaseFrame(oFrame.bufferIndex, false);
                break;
            }
            overlayTrack_->releaseFrame(oFrame.bufferIndex, true);
            overlayTrack_->consumeRenderedFrame(ctx.env, 50);
            overlayTrack_->sourceNode()->setActive(true);
            break;
        }
    }
}
