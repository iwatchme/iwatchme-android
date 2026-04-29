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
    JNIEnv* env = nullptr;
    jvm_->AttachCurrentThread(&env, nullptr);

    if (!eglCore_.init()) {
        LOGE("PlaybackSession: EGL init failed");
        jvm_->DetachCurrentThread();
        return;
    }

    LOGI("PlaybackSession: render thread started");

    int surfaceWidth = 0, surfaceHeight = 0;
    bool eof = false;

    VideoSyncController syncController;
    int64_t lastVideoDiagLogUs = 0;
    int64_t lastPerfDiagLogUs = 0;

    while (running_.load()) {
        if (windowChanged_.load()) {
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
                    surfaceWidth = ANativeWindow_getWidth(window_);
                    surfaceHeight = ANativeWindow_getHeight(window_);
                    glViewport(0, 0, surfaceWidth, surfaceHeight);
                    LOGI("PlaybackSession: EGL surface %dx%d", surfaceWidth, surfaceHeight);
                }
            }
        }

        if (videoSourceChanged_.load()) {
            if (eglSurface_ != EGL_NO_SURFACE) {
                uint32_t transitionGeneration = timelineGeneration_.load();
                videoSourceChanged_.store(false);
                if (initDecodePipeline(env)) {
                    buildRenderTree(surfaceWidth, surfaceHeight);
                    syncController.reset();
                    eof = false;
                    LOGI("PlaybackSession: ready, fps=%.1f, overlay=%s",
                         primaryTrack_->fps(),
                         (hasOverlay_ && overlayTrack_ && overlayTrack_->sourceNode()) ? "yes" : "no");
                    LOGI("AVSYNC transition source-ready generation=%u audio=%d",
                         transitionGeneration,
                         (audioPipeline_ && audioPipeline_->hasAudio()) ? 1 : 0);
                }
                endTimelineTransition(transitionGeneration);
            }
        }

        int64_t fastTarget = seekFastTargetUs_.exchange(-1);
        if (fastTarget >= 0 && pipelineInitialized_) {
            uint32_t transitionGeneration = timelineGeneration_.load();

            int64_t sourceSeekPos = fastTarget;
            if (primaryTrack_ && !primaryTrack_->timeline().isEmpty()) {
                ClipLookup lookup = primaryTrack_->timeline().resolve(fastTarget);
                if (lookup.clipIndex < 0) lookup = primaryTrack_->timeline().resolve(primaryTrack_->timeline().durationUs() - 1);
                if (lookup.clipIndex != primaryTrack_->activeClipIndex()) {
                    switchToClip(lookup.clipIndex, lookup.sourcePositionUs, env);
                } else {
                    primaryTrack_->seekInCurrentClip(lookup.sourcePositionUs);
                }
                sourceSeekPos = lookup.sourcePositionUs;
            } else if (primaryTrack_) {
                primaryTrack_->seekInCurrentClip(sourceSeekPos);
            }
            syncController.reset();
            eof = false;

            AVRational tb = primaryTrack_->videoTimeBase();
            bool gotFrame = false;
            for (int attempt = 0; attempt < 100 && !gotFrame && running_.load(); attempt++) {
                primaryTrack_->queueVideoPacketWithTimeout(50000);

                HwDecoder::DecodedFrame frame;
                if (primaryTrack_->dequeueFrame(frame, 5000)) {
                    int64_t framePtsUs = av_rescale_q(frame.pts, tb, {1, 1000000});

                    primaryTrack_->releaseFrame(frame.bufferIndex, true);
                    primaryTrack_->consumeRenderedFrame(env, 50);
                    currentPositionUs_.store(primaryTrack_->mapSourcePtsToTimelineUs(framePtsUs));

                    if (hasOverlay_ && overlayTrack_ && overlayTrack_->sourceNode()) {
                        ClipLookup oLookup = overlayTrack_->timeline().resolve(fastTarget);
                        if (oLookup.clipIndex >= 0) {
                            bool overlayReady = (oLookup.clipIndex != overlayTrack_->activeClipIndex())
                                ? overlayTrack_->switchToClip(oLookup.clipIndex, oLookup.sourcePositionUs, env)
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
                                    overlayTrack_->consumeRenderedFrame(env, 50);
                                    overlayTrack_->sourceNode()->setActive(true);
                                    break;
                                }
                            }
                        } else {
                            overlayTrack_->sourceNode()->setActive(false);
                        }
                    }

                    if (auto* blendNode = renderGraphBuilder_ ? renderGraphBuilder_->blendNode() : nullptr) {
                        blendNode->overlayAlpha = overlayAlpha_.load();
                    }
                    if (auto* outputNode = renderGraphBuilder_ ? renderGraphBuilder_->outputNode() : nullptr) {
                        outputNode->outputWidth = surfaceWidth;
                        outputNode->outputHeight = surfaceHeight;
                        outputNode->execute(framePtsUs);
                    }
                    eglCore_.swapBuffers(eglSurface_);
                    gotFrame = true;
                }
            }
            if (seekFastTargetUs_.load() >= 0) continue;
            LOGI("AVSYNC transition seekFast-ready generation=%u currentPosUs=%lld",
                 transitionGeneration, (long long)currentPositionUs_.load());
            endTimelineTransition(transitionGeneration);
        }

        int64_t seekTarget = seekTargetUs_.exchange(-1);
        if (seekTarget >= 0 && pipelineInitialized_) {
            uint32_t transitionGeneration = timelineGeneration_.load();

            int64_t sourceSeekTarget = seekTarget;
            if (primaryTrack_ && !primaryTrack_->timeline().isEmpty()) {
                ClipLookup lookup = primaryTrack_->timeline().resolve(seekTarget);
                if (lookup.clipIndex < 0) lookup = primaryTrack_->timeline().resolve(primaryTrack_->timeline().durationUs() - 1);
                if (lookup.clipIndex != primaryTrack_->activeClipIndex()) {
                    switchToClip(lookup.clipIndex, lookup.sourcePositionUs, env);
                } else {
                    primaryTrack_->seekInCurrentClip(lookup.sourcePositionUs);
                }
                sourceSeekTarget = lookup.sourcePositionUs;
            } else if (primaryTrack_) {
                primaryTrack_->seekInCurrentClip(sourceSeekTarget);
            }
            syncController.reset();
            eof = false;

            AVRational tb = primaryTrack_->videoTimeBase();
            bool reachedTarget = false;
            int skipCount = 0;

            while (!reachedTarget && running_.load()) {
                while (!eof) {
                    if (!primaryTrack_->queueVideoPacketWithTimeout(50000)) {
                        if (primaryTrack_->isEof()) {
                            eof = true;
                        }
                        break;
                    }
                }

                HwDecoder::DecodedFrame frame;
                if (!primaryTrack_->dequeueFrame(frame, 30000)) {
                    if (eof) break;
                    continue;
                }

                int64_t framePtsUs = av_rescale_q(frame.pts, tb, {1, 1000000});

                if (framePtsUs >= sourceSeekTarget) {
                    primaryTrack_->releaseFrame(frame.bufferIndex, true);
                    primaryTrack_->consumeRenderedFrame(env, 50);
                    currentPositionUs_.store(primaryTrack_->mapSourcePtsToTimelineUs(framePtsUs));

                    if (hasOverlay_ && overlayTrack_ && overlayTrack_->sourceNode()) {
                        ClipLookup oLookup = overlayTrack_->timeline().resolve(seekTarget);
                        if (oLookup.clipIndex >= 0) {
                            bool overlayReady = (oLookup.clipIndex != overlayTrack_->activeClipIndex())
                                ? overlayTrack_->switchToClip(oLookup.clipIndex, oLookup.sourcePositionUs, env)
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
                                    overlayTrack_->consumeRenderedFrame(env, 50);
                                    overlayTrack_->sourceNode()->setActive(true);
                                    break;
                                }
                            }
                        } else {
                            overlayTrack_->sourceNode()->setActive(false);
                        }
                    }

                    if (auto* blendNode = renderGraphBuilder_ ? renderGraphBuilder_->blendNode() : nullptr) {
                        blendNode->overlayAlpha = overlayAlpha_.load();
                    }
                    if (auto* outputNode = renderGraphBuilder_ ? renderGraphBuilder_->outputNode() : nullptr) {
                        outputNode->outputWidth = surfaceWidth;
                        outputNode->outputHeight = surfaceHeight;
                        outputNode->execute(framePtsUs);
                    }
                    eglCore_.swapBuffers(eglSurface_);

                    reachedTarget = true;
                } else {
                    primaryTrack_->releaseFrame(frame.bufferIndex, true);
                    primaryTrack_->consumeRenderedFrame(env, 50);
                    skipCount++;
                }
            }
            currentPositionUs_.store(seekTarget);
            LOGI("AVSYNC transition seek-ready generation=%u requestedUs=%lld sourceTargetUs=%lld skipCount=%d audioClockUs=%lld",
                 transitionGeneration,
                 (long long)seekTarget,
                 (long long)sourceSeekTarget,
                 skipCount,
                 (long long)getAudioClockUs());
            endTimelineTransition(transitionGeneration);
        }

        if (!playing_.load() || eglSurface_ == EGL_NO_SURFACE || !pipelineInitialized_) {
            syncController.reset();
            std::unique_lock<std::mutex> lock(cmdMutex_);
            cmdCond_.wait_for(lock, std::chrono::milliseconds(50));
            continue;
        }

        if (eof) {
            int next = primaryTrack_->activeClipIndex() + 1;
            if (primaryTrack_ && !primaryTrack_->timeline().isEmpty() && next < primaryTrack_->timeline().clipCount()) {
                uint32_t gen = beginTimelineTransition();
                switchToClip(next, primaryTrack_->timeline().clipAt(next).trimIn, env);
                syncController.reset();
                eof = false;
                endTimelineTransition(gen);
                LOGI("PlaybackSession: clip EOF -> switch to clip %d", next);
                continue;
            }

            uint32_t transitionGeneration = beginTimelineTransition();
            if (primaryTrack_ && !primaryTrack_->timeline().isEmpty() && primaryTrack_->timeline().clipCount() > 1) {
                switchToClip(0, primaryTrack_->timeline().clipAt(0).trimIn, env);
            } else {
                int64_t startPos = (primaryTrack_ && !primaryTrack_->timeline().isEmpty())
                    ? primaryTrack_->timeline().clipAt(0).trimIn : 0;
                if (audioPipeline_) audioPipeline_->notifySeek(startPos);
                if (primaryTrack_) {
                    primaryTrack_->seekInCurrentClip(startPos);
                }
                if (audioPipeline_ && audioPipeline_->hasAudio()) audioPipeline_->flush();
            }
            if (hasOverlay_ && overlayTrack_ && !overlayTrack_->timeline().isEmpty()) {
                const Clip& oFirst = overlayTrack_->timeline().clipAt(0);
                if (overlayTrack_->activeClipIndex() != 0) {
                    overlayTrack_->switchToClip(0, oFirst.trimIn, env);
                } else {
                    overlayTrack_->seekInCurrentClip(oFirst.trimIn);
                }
                if (overlayTrack_->sourceNode()) {
                    overlayTrack_->sourceNode()->setActive(false);
                }
            }
            currentPositionUs_.store(0);
            syncController.reset();
            eof = false;
            endTimelineTransition(transitionGeneration);
            LOGI("PlaybackSession: restart from beginning");
            continue;
        }

        AVRational tb = primaryTrack_->videoTimeBase();
        double fps = primaryTrack_->fps();
        int64_t nominalDurationUs = (fps > 0) ? (int64_t)(1000000.0 / fps) : 33333;
        int64_t perfCycleStartUs = steadyNowUs();
        int64_t feedStartUs = perfCycleStartUs;

        primaryTrack_->pumpAvailablePackets();
        eof = primaryTrack_->isEof();
        int64_t feedEndUs = steadyNowUs();

        int64_t dequeueStartUs = feedEndUs;
        HwDecoder::DecodedFrame decodedFrame;
        bool gotFrame = primaryTrack_->dequeueFrame(decodedFrame, 30000);
        int64_t dequeueEndUs = steadyNowUs();
        if (!gotFrame) continue;

        int64_t framePtsUs = av_rescale_q(decodedFrame.pts, tb, {1, 1000000});

        if (primaryTrack_->isFrameBeforeTrim(framePtsUs)) {
            primaryTrack_->releaseFrame(decodedFrame.bufferIndex, false);
            continue;
        }
        if (primaryTrack_->isFrameAfterTrim(framePtsUs)) {
            primaryTrack_->releaseFrame(decodedFrame.bufferIndex, false);
            int next = primaryTrack_->activeClipIndex() + 1;
            if (next < primaryTrack_->timeline().clipCount()) {
                uint32_t gen = beginTimelineTransition();
                switchToClip(next, primaryTrack_->timeline().clipAt(next).trimIn, env);
                syncController.reset();
                eof = false;
                endTimelineTransition(gen);
                continue;
            } else {
                eof = true;
                continue;
            }
        }

        primaryTrack_->releaseFrame(decodedFrame.bufferIndex, true);

        int64_t waitStartUs = dequeueEndUs;
        primaryTrack_->consumeRenderedFrame(env, 50);
        int64_t waitEndUs = steadyNowUs();
        int64_t updateStartUs = waitEndUs;
        int64_t updateEndUs = steadyNowUs();

        int64_t globalPosUs = primaryTrack_->mapSourcePtsToTimelineUs(framePtsUs);
        currentPositionUs_.store(globalPosUs);

        if (hasOverlay_ && overlayTrack_ && overlayTrack_->sourceNode()) {
            do {
                ClipLookup oLookup = overlayTrack_->timeline().resolve(globalPosUs);
                if (oLookup.clipIndex < 0) {
                    overlayTrack_->sourceNode()->setActive(false);
                    break;
                }

                if (oLookup.clipIndex != overlayTrack_->activeClipIndex()) {
                    if (!overlayTrack_->switchToClip(oLookup.clipIndex, oLookup.sourcePositionUs, env)) {
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
                        overlayTrack_->consumeRenderedFrame(env, 30);
                        overlayTrack_->sourceNode()->setActive(true);
                    }
                }
            } while (false);
        }

        int64_t audioClockUs = getAudioClockUs();
        int64_t wallNowUs = steadyNowUs();
        VideoSyncDecision syncDecision = syncController.prepareFrame(
            framePtsUs,
            nominalDurationUs,
            audioClockUs,
            wallNowUs);

        if (syncDecision.timelineReset) {
            LOGI("AVSYNC video-timeline-reset framePtsUs=%lld audioClockUs=%lld frameTimerUs=%lld wallNowUs=%lld",
                 (long long)framePtsUs,
                 (long long)(audioClockUs >= 0 ? audioClockUs : -1),
                 (long long)syncDecision.frameTimerUs,
                 (long long)wallNowUs);
        }

        if (syncDecision.sleepUs > 0) {
            usleep((useconds_t)syncDecision.sleepUs);
        }

        VideoSyncDecision finalizedDecision;
        syncController.finalizeFrame(syncDecision, audioClockUs, steadyNowUs(), finalizedDecision);
        bool shouldRender = finalizedDecision.shouldRender;

        int64_t diagNowUs = steadyNowUs();
        if (diagNowUs - lastVideoDiagLogUs >= kAvSyncDiagIntervalUs) {
            lastVideoDiagLogUs = diagNowUs;
            LOGI("AVSYNC video-diag framePtsUs=%lld audioClockUs=%lld diffUs=%lld delayUs=%lld frameDurUs=%lld render=%d drops=%d posUs=%lld",
                 (long long)framePtsUs,
                 (long long)audioClockUs,
                 (long long)finalizedDecision.diffUs,
                 (long long)finalizedDecision.delayUs,
                 (long long)finalizedDecision.frameDurationUs,
                 shouldRender ? 1 : 0,
                 finalizedDecision.consecutiveDrops,
                 (long long)currentPositionUs_.load());
        }

        int64_t executeStartUs = steadyNowUs();
        if (shouldRender) {
            if (auto* blendNode = renderGraphBuilder_ ? renderGraphBuilder_->blendNode() : nullptr) {
                blendNode->overlayAlpha = overlayAlpha_.load();
            }
            if (auto* outputNode = renderGraphBuilder_ ? renderGraphBuilder_->outputNode() : nullptr) {
                outputNode->outputWidth = surfaceWidth;
                outputNode->outputHeight = surfaceHeight;
                outputNode->execute(framePtsUs);
            }
        }
        int64_t executeEndUs = steadyNowUs();

        int64_t swapStartUs = executeEndUs;
        if (shouldRender) {
            eglCore_.swapBuffers(eglSurface_);
        }
        int64_t swapEndUs = steadyNowUs();

        if (diagNowUs - lastPerfDiagLogUs >= kAvSyncDiagIntervalUs) {
            lastPerfDiagLogUs = diagNowUs;
            LOGI("AVPERF framePtsUs=%lld audioClockUs=%lld diffUs=%lld feedUs=%lld dequeueUs=%lld waitUs=%lld updateUs=%lld executeUs=%lld swapUs=%lld totalUs=%lld render=%d",
                 (long long)framePtsUs,
                 (long long)audioClockUs,
                 (long long)(audioClockUs >= 0 ? (framePtsUs - audioClockUs) : 0),
                 (long long)(feedEndUs - feedStartUs),
                 (long long)(dequeueEndUs - dequeueStartUs),
                 (long long)(waitEndUs - waitStartUs),
                 (long long)(updateEndUs - updateStartUs),
                 (long long)(executeEndUs - executeStartUs),
                 (long long)(swapEndUs - swapStartUs),
                 (long long)(swapEndUs - perfCycleStartUs),
                 shouldRender ? 1 : 0);
        }

        if (eof) {
            int next = primaryTrack_->activeClipIndex() + 1;
            if (!primaryTrack_ || primaryTrack_->timeline().isEmpty() || next >= primaryTrack_->timeline().clipCount()) {
                playing_.store(false);
                if (audioPipeline_ && audioPipeline_->hasAudio()) audioPipeline_->pause();
                LOGI("PlaybackSession: playback completed (EOF)");
                if (playbackCompletedHandler_) {
                    playbackCompletedHandler_();
                }
            } else {
                LOGI("PlaybackSession: clip %d EOF, next clip %d pending", primaryTrack_->activeClipIndex(), next);
            }
        }
    }

    releaseDecodePipeline(env);

    if (eglSurface_ != EGL_NO_SURFACE) {
        eglCore_.makeNothingCurrent();
        eglCore_.destroySurface(eglSurface_);
        eglSurface_ = EGL_NO_SURFACE;
    }
    eglCore_.release();

    {
        std::lock_guard<std::mutex> lock(windowMutex_);
        if (window_) { ANativeWindow_release(window_); window_ = nullptr; }
    }

    jvm_->DetachCurrentThread();
    LOGI("PlaybackSession: render thread stopped");
}
