#include "RenderEngine.h"
#include "common/log.h"
#include <GLES3/gl3.h>
#include <unistd.h>
#include <chrono>

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

RenderEngine::RenderEngine(JavaVM* jvm) : jvm_(jvm) {
    LOGI("RenderEngine created");
    startRenderThread();
}

RenderEngine::~RenderEngine() {
    stopRenderThread();
    LOGI("RenderEngine destroyed");
}

void RenderEngine::setSurface(ANativeWindow* window) {
    std::lock_guard<std::mutex> lock(windowMutex_);
    if (window_) ANativeWindow_release(window_);
    window_ = window;
    if (window_) ANativeWindow_acquire(window_);
    windowChanged_.store(true);
    cmdCond_.notify_one();
    LOGI("RenderEngine: surface %s", window ? "set" : "cleared");
}

bool RenderEngine::setVideoSource(const std::string& filePath) {
    // 向后兼容：创建单片段 timeline
    Clip c;
    c.sourcePath = filePath;
    c.trimIn = 0;
    c.trimOut = -1;  // setTimeline 中 probe 文件获取真实时长
    std::vector<Clip> clips;
    clips.push_back(std::move(c));
    return setTimeline(std::move(clips));
}

void RenderEngine::play() {
    playing_.store(true);
    if (hasAudio_) audioOutput_.resume();
    cmdCond_.notify_one();
    LOGI("RenderEngine: play");
}

void RenderEngine::pause() {
    playing_.store(false);
    if (hasAudio_) audioOutput_.pause();
    LOGI("RenderEngine: pause");
}

void RenderEngine::seek(int64_t positionUs) {
    uint32_t generation = beginTimelineTransition();
    seekTargetUs_.store(positionUs);
    seekFastTargetUs_.store(-1);
    // 通知音频线程也需要 seek
    audioSeekTargetUs_.store(positionUs);
    cmdCond_.notify_one();
    LOGI("AVSYNC transition seek generation=%u targetUs=%lld",
         generation, (long long)positionUs);
}

void RenderEngine::seekFast(int64_t positionUs) {
    uint32_t generation = beginTimelineTransition();
    seekFastTargetUs_.store(positionUs);
    audioSeekTargetUs_.store(positionUs);
    cmdCond_.notify_one();
    LOGI("AVSYNC transition seekFast generation=%u targetUs=%lld",
         generation, (long long)positionUs);
}

int64_t RenderEngine::getDuration() const {
    if (!timeline_.isEmpty()) return timeline_.durationUs();
    return demuxer_.durationUs();
}
int64_t RenderEngine::getPosition() const { return currentPositionUs_.load(); }
int RenderEngine::getVideoWidth() const { return demuxer_.videoWidth(); }
int RenderEngine::getVideoHeight() const { return demuxer_.videoHeight(); }

int64_t RenderEngine::getAudioClockUs() const {
    if (hasAudio_ && audioOutput_.isOpen()) {
        return audioOutput_.getPlaybackPositionUs();
    }
    // 无音频时返回 -1，视频侧回退到系统时钟
    return -1;
}

uint32_t RenderEngine::beginTimelineTransition() {
    timelineTransitioning_.store(true);
    return timelineGeneration_.fetch_add(1) + 1;
}

void RenderEngine::endTimelineTransition(uint32_t generation) {
    if (timelineGeneration_.load() == generation) {
        timelineTransitioning_.store(false);
    }
}

bool RenderEngine::switchToClip(int clipIndex, int64_t sourcePositionUs, JNIEnv* env) {
    if (clipIndex < 0 || clipIndex >= timeline_.clipCount()) return false;
    const Clip& clip = timeline_.clipAt(clipIndex);
    LOGI("RenderEngine: switchToClip %d → %d, sourcePos=%lld, path=%s",
         activeClipIndex_, clipIndex, (long long)sourcePositionUs, clip.sourcePath.c_str());

    // 1. 停止音频线程（显式 join，确保退出 decode/write 临界区）
    audioRunning_.store(false);
    if (audioThread_.joinable()) audioThread_.join();

    // 2. close 旧 Demuxer，open 新文件
    demuxer_.close();
    if (!demuxer_.open(clip.sourcePath.c_str())) {
        LOGE("switchToClip: failed to open %s", clip.sourcePath.c_str());
        return false;
    }

    // 3. 比较完整解码配置，决定 flush 还是重建
    DecoderConfig newConfig = DecoderConfig::fromCodecParameters(demuxer_.videoCodecParameters());

    if (newConfig == activeDecoderConfig_) {
        hwDecoder_.flush();
        LOGI("switchToClip: same decoder config, flush only");
    } else {
        hwDecoder_.release();
        if (!hwDecoder_.init(demuxer_.videoCodecParameters(), stHelper_.nativeWindow())) {
            LOGE("switchToClip: HwDecoder re-init failed");
            return false;
        }
        activeDecoderConfig_ = newConfig;
        LOGI("switchToClip: decoder config changed, re-init codec");
    }

    // 4. 分辨率变化时重建 SourceNode GL 资源
    int newW = demuxer_.videoWidth();
    int newH = demuxer_.videoHeight();
    if (newW != sourceNode_->outputWidth || newH != sourceNode_->outputHeight) {
        sourceNode_->releaseGL();
        sourceNode_->initGL(newW, newH);
        LOGI("switchToClip: SourceNode GL re-init %dx%d", newW, newH);
    }

    // 5. 重置 SurfaceTexture frameAvailable 标记，不主动 updateTexImage
    stHelper_.consumeFrameAvailable();

    // 6. Seek 到源文件位置
    if (sourcePositionUs > 0) {
        demuxer_.seek(sourcePositionUs);
    }

    // 7. 重建音频管线（音频线程已 join，安全操作）
    hasAudio_ = false;
    audioDecoder_.release();

    // 统一 flush：无论新片段是否有音频，都先清掉旧 PCM 残留
    if (audioOutput_.isOpen()) {
        audioOutput_.flush();
    }

    if (demuxer_.hasAudio()) {
        // AudioOutput 按需打开：首片段无音频、后续片段有音频的场景
        if (!audioOutput_.isOpen()) {
            int sr = demuxer_.audioSampleRate() > 0 ? demuxer_.audioSampleRate() : 48000;
            if (!audioOutput_.open(sr, 2)) {
                LOGW("switchToClip: AudioOutput open failed, skip audio");
                goto skip_audio;
            }
        }
        if (audioDecoder_.init(
                demuxer_.audioCodecParameters(),
                demuxer_.audioTimeBase(),
                audioOutput_.sampleRate(),
                audioOutput_.channels())) {
            hasAudio_ = true;
        } else {
            LOGW("switchToClip: AudioDecoder init failed, skip audio");
        }
    }
    skip_audio:

    // 8. 重启音频线程
    if (hasAudio_) {
        audioRunning_.store(true);
        audioThread_ = std::thread(&RenderEngine::audioThreadFunc, this);
    }

    // 9. 更新状态
    activeClipIndex_ = clipIndex;
    skipUntilPtsUs_ = sourcePositionUs;

    LOGI("AVSYNC clip-switch clipIndex=%d sourcePos=%lld audio=%d",
         clipIndex, (long long)sourcePositionUs, hasAudio_ ? 1 : 0);
    return true;
}

bool RenderEngine::setTimeline(std::vector<Clip> clips) {
    uint32_t generation = beginTimelineTransition();

    // 对 trimOut == -1 的片段，probe 文件获取真实时长
    for (auto& clip : clips) {
        if (clip.trimOut < 0) {
            Demuxer probe;
            if (probe.open(clip.sourcePath.c_str())) {
                clip.trimOut = probe.durationUs();
                probe.close();
            } else {
                LOGE("RenderEngine: setTimeline: failed to probe %s", clip.sourcePath.c_str());
                return false;
            }
        }
    }

    {
        std::lock_guard<std::mutex> lock(videoSourceMutex_);
        timeline_.setClips(std::move(clips));
        videoSourceChanged_.store(true);
    }

    cmdCond_.notify_one();
    LOGI("RenderEngine: timeline set %d clips, duration=%.2fs, generation=%u",
         timeline_.clipCount(), timeline_.durationUs() / 1000000.0, generation);
    return true;
}

void RenderEngine::startRenderThread() {
    running_.store(true);
    renderThread_ = std::thread(&RenderEngine::renderThreadFunc, this);
}

void RenderEngine::stopRenderThread() {
    running_.store(false);
    playing_.store(false);
    audioRunning_.store(false);
    cmdCond_.notify_one();
    if (audioThread_.joinable()) audioThread_.join();
    if (renderThread_.joinable()) renderThread_.join();
}

bool RenderEngine::initDecodePipeline(JNIEnv* env) {
    releaseDecodePipeline(env);

    // 从 timeline 第一个片段初始化
    if (timeline_.isEmpty()) {
        LOGE("RenderEngine: initDecodePipeline: timeline is empty");
        return false;
    }

    const Clip& firstClip = timeline_.clipAt(0);
    if (!demuxer_.open(firstClip.sourcePath.c_str())) {
        LOGE("RenderEngine: failed to open video: %s", firstClip.sourcePath.c_str());
        return false;
    }

    if (!stHelper_.create(env)) {
        LOGE("RenderEngine: failed to create SurfaceTexture");
        return false;
    }

    if (!hwDecoder_.init(demuxer_.videoCodecParameters(), stHelper_.nativeWindow())) {
        LOGE("RenderEngine: failed to init HwDecoder");
        stHelper_.release(env);
        return false;
    }

    // 记录解码配置指纹
    activeDecoderConfig_ = DecoderConfig::fromCodecParameters(demuxer_.videoCodecParameters());
    activeClipIndex_ = 0;
    skipUntilPtsUs_ = firstClip.trimIn;

    int w = demuxer_.videoWidth();
    int h = demuxer_.videoHeight();

    sourceNode_ = new SourceNode(&stHelper_, env);
    if (!sourceNode_->initGL(w, h)) {
        LOGE("RenderEngine: SourceNode GL init failed");
        return false;
    }

    outputNode_ = new OutputNode();
    if (!outputNode_->initGL()) {
        LOGE("RenderEngine: OutputNode GL init failed");
        return false;
    }
    outputNode_->inputs.push_back(sourceNode_);

    // 如果 trimIn > 0，seek 到起始位置
    if (firstClip.trimIn > 0) {
        demuxer_.seek(firstClip.trimIn);
    }

    // 初始化音频管线（如果有音频流）
    hasAudio_ = false;
    if (demuxer_.hasAudio()) {
        int requestedSampleRate = demuxer_.audioSampleRate() > 0 ? demuxer_.audioSampleRate() : 48000;
        int requestedChannels = 2;
        if (audioOutput_.open(requestedSampleRate, requestedChannels)) {
            if (audioDecoder_.init(
                    demuxer_.audioCodecParameters(),
                    demuxer_.audioTimeBase(),
                    audioOutput_.sampleRate(),
                    audioOutput_.channels())) {
                hasAudio_ = true;

                // 启动音频线程
                audioRunning_.store(true);
                audioThread_ = std::thread(&RenderEngine::audioThreadFunc, this);

                LOGI("RenderEngine: audio pipeline initialized");
            } else {
                audioOutput_.close();
                LOGW("RenderEngine: AudioDecoder init failed, playing without audio");
            }
        } else {
            LOGW("RenderEngine: AudioOutput open failed, playing without audio");
        }
    }

    currentPositionUs_.store(0);
    pipelineInitialized_ = true;
    LOGI("RenderEngine: pipeline initialized %dx%d, audio=%s, clip=%d/%d",
         w, h, hasAudio_ ? "yes" : "no", activeClipIndex_, timeline_.clipCount());
    return true;
}

void RenderEngine::releaseDecodePipeline(JNIEnv* env) {
    if (!pipelineInitialized_) return;

    // 先停止音频线程
    audioRunning_.store(false);
    if (audioThread_.joinable()) audioThread_.join();

    audioOutput_.close();
    audioDecoder_.release();
    hasAudio_ = false;

    delete outputNode_;  outputNode_ = nullptr;
    delete sourceNode_;  sourceNode_ = nullptr;

    hwDecoder_.release();
    stHelper_.release(env);
    demuxer_.close();

    activeClipIndex_ = -1;
    activeDecoderConfig_ = DecoderConfig{};
    skipUntilPtsUs_ = 0;

    pipelineInitialized_ = false;
    LOGI("RenderEngine: pipeline released");
}

// ============================================================
// 音频线程：独立读取音频 packet，解码为 PCM，写入 AudioOutput
// ============================================================
void RenderEngine::audioThreadFunc() {
    LOGI("RenderEngine: audio thread started");

    AVPacket* pkt = av_packet_alloc();
    std::vector<uint8_t> pcmBuffer;
    int64_t lastAudioDiagLogUs = 0;
    int audioWriteCount = 0;

    while (audioRunning_.load() && running_.load()) {
        // 处理音频 seek
        int64_t audioSeek = audioSeekTargetUs_.exchange(-1);
        if (audioSeek >= 0) {
            audioDecoder_.flush();
            audioOutput_.flush();
            av_packet_unref(pkt);
            LOGI("AVSYNC audio-thread seek-handled targetUs=%lld generation=%u",
                 (long long)audioSeek, timelineGeneration_.load());
            // Demuxer seek 由视频线程完成，音频线程只 flush 自己的状态
        }

        if (timelineTransitioning_.load()) {
            std::this_thread::sleep_for(std::chrono::milliseconds(5));
            continue;
        }

        // 未播放时等待
        if (!playing_.load()) {
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
            continue;
        }

        uint32_t generation = timelineGeneration_.load();

        // 从 Demuxer 读取音频 packet（需要加锁，因为视频线程也在读）
        bool gotPacket = false;
        {
            std::lock_guard<std::mutex> lock(demuxer_.readMutex());
            gotPacket = demuxer_.readAudioPacket(pkt);
        }

        if (!gotPacket) {
            // EOF 或没有更多音频数据
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
            continue;
        }

        if (timelineTransitioning_.load() || generation != timelineGeneration_.load()) {
            av_packet_unref(pkt);
            continue;
        }

        // 解码 + 重采样为 PCM
        pcmBuffer.clear();
        AudioDecoder::DecodeResult decodeResult = audioDecoder_.decode(pkt, pcmBuffer);
        av_packet_unref(pkt);

        if (timelineTransitioning_.load() || generation != timelineGeneration_.load()) {
            continue;
        }

        if (decodeResult.frames > 0 && !pcmBuffer.empty()) {
            // 写入 AAudio（阻塞直到写完）
            audioOutput_.write(
                pcmBuffer.data(),
                decodeResult.frames,
                decodeResult.hasValidPts ? decodeResult.startPtsUs : -1
            );
            audioWriteCount++;
            int64_t nowUs = steadyNowUs();
            if (nowUs - lastAudioDiagLogUs >= kAvSyncDiagIntervalUs) {
                lastAudioDiagLogUs = nowUs;
                LOGI("AVSYNC audio-diag count=%d gen=%u startPtsUs=%lld hasPts=%d frames=%d clockUs=%lld currentPosUs=%lld",
                     audioWriteCount,
                     generation,
                     (long long)decodeResult.startPtsUs,
                     decodeResult.hasValidPts ? 1 : 0,
                     decodeResult.frames,
                     (long long)audioOutput_.getPlaybackPositionUs(),
                     (long long)currentPositionUs_.load());
            }
        }
    }

    av_packet_free(&pkt);
    LOGI("RenderEngine: audio thread stopped");
}

// ============================================================
// 渲染线程（视频）
// ============================================================
void RenderEngine::renderThreadFunc() {
    JNIEnv* env = nullptr;
    jvm_->AttachCurrentThread(&env, nullptr);

    if (!eglCore_.init()) {
        LOGE("RenderEngine: EGL init failed");
        jvm_->DetachCurrentThread();
        return;
    }

    LOGI("RenderEngine: render thread started");

    int surfaceWidth = 0, surfaceHeight = 0;
    bool eof = false;

    // 持久化 packet —— 不会被丢弃，如果 codec 无法接收则下轮重试
    AVPacket* pkt = av_packet_alloc();
    bool hasPendingPkt = false;

    // ============================================================
    // FFplay frame_timer + compute_target_delay 同步变量
    //
    // frame_timer: 累积式显示时间戳（绝对墙钟时间，微秒）
    //   每帧推进 frame_timer += adjusted_delay
    //   当前帧应在 frame_timer 时刻显示
    //   不依赖 usleep(diff) 的精度，天然补偿调度延迟
    //
    // 移植自 FFplay video_refresh() + compute_target_delay()
    // ============================================================
    int64_t frameTimerUs = 0;         // FFplay frame_timer
    int64_t lastFramePtsUs = -1;      // 上一帧 PTS（计算帧间隔用）
    bool frameTimerValid = false;     // 首帧标志
    int consecutiveDrops = 0;         // 连续丢帧计数（防止画面冻结）
    int64_t lastVideoDiagLogUs = 0;
    int64_t lastPerfDiagLogUs = 0;

    while (running_.load()) {
        // --- 处理 Surface 变更 ---
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
                    LOGI("RenderEngine: EGL surface %dx%d", surfaceWidth, surfaceHeight);
                }
            }
        }

        // --- 处理视频源变更（仅在 EGL surface 就绪时） ---
        if (videoSourceChanged_.load()) {
            if (eglSurface_ != EGL_NO_SURFACE) {
                uint32_t transitionGeneration = timelineGeneration_.load();
                videoSourceChanged_.store(false);
                if (hasPendingPkt) {
                    av_packet_unref(pkt);
                    hasPendingPkt = false;
                }
                if (initDecodePipeline(env)) {
                    frameTimerValid = false;
                    lastFramePtsUs = -1;
                    consecutiveDrops = 0;
                    eof = false;
                    LOGI("RenderEngine: ready, fps=%.1f", demuxer_.fps());
                    LOGI("AVSYNC transition source-ready generation=%u audio=%d",
                         transitionGeneration, hasAudio_ ? 1 : 0);
                }
                endTimelineTransition(transitionGeneration);
            }
        }

        // --- 处理快速 Seek（拖动中：只显示最近关键帧） ---
        int64_t fastTarget = seekFastTargetUs_.exchange(-1);
        if (fastTarget >= 0 && pipelineInitialized_) {
            uint32_t transitionGeneration = timelineGeneration_.load();
            if (hasPendingPkt) {
                av_packet_unref(pkt);
                hasPendingPkt = false;
            }

            // 解析全局位置 → 片段 + 源位置
            int64_t sourceSeekPos = fastTarget;
            if (!timeline_.isEmpty()) {
                ClipLookup lookup = timeline_.resolve(fastTarget);
                if (lookup.clipIndex < 0) lookup = timeline_.resolve(timeline_.durationUs() - 1);
                if (lookup.clipIndex != activeClipIndex_) {
                    switchToClip(lookup.clipIndex, lookup.sourcePositionUs, env);
                } else {
                    skipUntilPtsUs_ = lookup.sourcePositionUs;
                }
                sourceSeekPos = lookup.sourcePositionUs;
            }

            {
                std::lock_guard<std::mutex> lock(demuxer_.readMutex());
                demuxer_.seek(sourceSeekPos);
            }
            hwDecoder_.flush();
            frameTimerValid = false;
            lastFramePtsUs = -1;
            consecutiveDrops = 0;
            eof = false;

            AVRational tb = demuxer_.videoTimeBase();
            bool gotFrame = false;
            for (int attempt = 0; attempt < 100 && !gotFrame && running_.load(); attempt++) {
                if (!hasPendingPkt) {
                    std::lock_guard<std::mutex> lock(demuxer_.readMutex());
                    if (!demuxer_.readVideoPacket(pkt)) break;
                    hasPendingPkt = true;
                }
                if (hwDecoder_.queuePacketWithTimeout(pkt, 50000)) {
                    av_packet_unref(pkt);
                    hasPendingPkt = false;
                }

                HwDecoder::DecodedFrame frame;
                if (hwDecoder_.dequeueOutput(frame, 5000)) {
                    int64_t framePtsUs = av_rescale_q(frame.pts, tb, {1, 1000000});

                    // 快速 seek 取第一帧即可，直接渲染
                    hwDecoder_.releaseOutput(frame.bufferIndex, true);
                    stHelper_.waitForFrame(50);
                    stHelper_.consumeFrameAvailable();
                    stHelper_.updateTexImage(env);

                    // 转换为全局位置
                    if (!timeline_.isEmpty() && activeClipIndex_ >= 0) {
                        const Clip& clip = timeline_.clipAt(activeClipIndex_);
                        currentPositionUs_.store(clip.inPoint + (framePtsUs - clip.trimIn));
                    } else {
                        currentPositionUs_.store(framePtsUs);
                    }

                    outputNode_->outputWidth = surfaceWidth;
                    outputNode_->outputHeight = surfaceHeight;
                    outputNode_->execute(framePtsUs);
                    eglCore_.swapBuffers(eglSurface_);
                    gotFrame = true;
                }
            }
            if (seekFastTargetUs_.load() >= 0) continue;
            LOGI("AVSYNC transition seekFast-ready generation=%u currentPosUs=%lld",
                 transitionGeneration, (long long)currentPositionUs_.load());
            endTimelineTransition(transitionGeneration);
        }

        // --- 处理精确 Seek（松手后：解码到目标帧） ---
        int64_t seekTarget = seekTargetUs_.exchange(-1);
        if (seekTarget >= 0 && pipelineInitialized_) {
            uint32_t transitionGeneration = timelineGeneration_.load();
            if (hasPendingPkt) {
                av_packet_unref(pkt);
                hasPendingPkt = false;
            }

            // 解析全局位置 → 片段 + 源位置
            int64_t sourceSeekTarget = seekTarget;
            if (!timeline_.isEmpty()) {
                ClipLookup lookup = timeline_.resolve(seekTarget);
                if (lookup.clipIndex < 0) lookup = timeline_.resolve(timeline_.durationUs() - 1);
                if (lookup.clipIndex != activeClipIndex_) {
                    switchToClip(lookup.clipIndex, lookup.sourcePositionUs, env);
                } else {
                    skipUntilPtsUs_ = lookup.sourcePositionUs;
                }
                sourceSeekTarget = lookup.sourcePositionUs;
            }

            {
                std::lock_guard<std::mutex> lock(demuxer_.readMutex());
                demuxer_.seek(sourceSeekTarget);
            }
            hwDecoder_.flush();
            frameTimerValid = false;
            lastFramePtsUs = -1;
            consecutiveDrops = 0;
            eof = false;

            AVRational tb = demuxer_.videoTimeBase();
            bool reachedTarget = false;
            int skipCount = 0;

            while (!reachedTarget && running_.load()) {
                while (!eof) {
                    if (!hasPendingPkt) {
                        std::lock_guard<std::mutex> lock(demuxer_.readMutex());
                        if (!demuxer_.readVideoPacket(pkt)) {
                            eof = true;
                            hwDecoder_.queueEOS();
                            break;
                        }
                        hasPendingPkt = true;
                    }
                    if (hwDecoder_.queuePacketWithTimeout(pkt, 50000)) {
                        av_packet_unref(pkt);
                        hasPendingPkt = false;
                    } else {
                        break;
                    }
                }

                HwDecoder::DecodedFrame frame;
                if (!hwDecoder_.dequeueOutput(frame, 30000)) {
                    if (eof) break;
                    continue;
                }

                int64_t framePtsUs = av_rescale_q(frame.pts, tb, {1, 1000000});

                if (framePtsUs >= sourceSeekTarget) {
                    // 目标帧：渲染
                    hwDecoder_.releaseOutput(frame.bufferIndex, true);
                    stHelper_.waitForFrame(50);
                    stHelper_.consumeFrameAvailable();
                    stHelper_.updateTexImage(env);

                    if (!timeline_.isEmpty() && activeClipIndex_ >= 0) {
                        const Clip& clip = timeline_.clipAt(activeClipIndex_);
                        currentPositionUs_.store(clip.inPoint + (framePtsUs - clip.trimIn));
                    } else {
                        currentPositionUs_.store(framePtsUs);
                    }

                    outputNode_->outputWidth = surfaceWidth;
                    outputNode_->outputHeight = surfaceHeight;
                    outputNode_->execute(framePtsUs);
                    eglCore_.swapBuffers(eglSurface_);

                    reachedTarget = true;
                } else {
                    // seek 路径的跳过帧：render=true 驱动 SurfaceTexture 更新
                    hwDecoder_.releaseOutput(frame.bufferIndex, true);
                    stHelper_.waitForFrame(50);
                    stHelper_.consumeFrameAvailable();
                    stHelper_.updateTexImage(env);
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

        // --- 空闲等待（未播放时） ---
        if (!playing_.load() || eglSurface_ == EGL_NO_SURFACE || !pipelineInitialized_) {
            // 暂停后重置 frame_timer，恢复播放时重新校准
            frameTimerValid = false;
            lastFramePtsUs = -1;
            std::unique_lock<std::mutex> lock(cmdMutex_);
            cmdCond_.wait_for(lock, std::chrono::milliseconds(50));
            continue;
        }

        if (eof) {
            // Demuxer EOF：可能是片段结束，也可能是真正的时间线结束。
            // 先检查是否还有后续片段。
            int next = activeClipIndex_ + 1;
            if (!timeline_.isEmpty() && next < timeline_.clipCount()) {
                // 片段边界：切到下一个片段
                uint32_t gen = beginTimelineTransition();
                if (hasPendingPkt) { av_packet_unref(pkt); hasPendingPkt = false; }
                switchToClip(next, timeline_.clipAt(next).trimIn, env);
                frameTimerValid = false;
                lastFramePtsUs = -1;
                consecutiveDrops = 0;
                eof = false;
                endTimelineTransition(gen);
                LOGI("RenderEngine: clip EOF → switch to clip %d", next);
                continue;
            }

            // 真正的时间线 EOF，再次 Play → 从头开始
            uint32_t transitionGeneration = beginTimelineTransition();
            if (hasPendingPkt) {
                av_packet_unref(pkt);
                hasPendingPkt = false;
            }
            if (!timeline_.isEmpty() && timeline_.clipCount() > 1) {
                // 多片段：切回第一个片段
                switchToClip(0, timeline_.clipAt(0).trimIn, env);
            } else {
                // 单片段 / 兼容路径：seek 到起点
                int64_t startPos = timeline_.isEmpty() ? 0 : timeline_.clipAt(0).trimIn;
                audioSeekTargetUs_.store(startPos);
                {
                    std::lock_guard<std::mutex> lock(demuxer_.readMutex());
                    demuxer_.seek(startPos);
                }
                hwDecoder_.flush();
                if (hasAudio_) {
                    audioDecoder_.flush();
                    audioOutput_.flush();
                }
                skipUntilPtsUs_ = startPos;
            }
            currentPositionUs_.store(0);
            frameTimerValid = false;
            lastFramePtsUs = -1;
            consecutiveDrops = 0;
            eof = false;
            endTimelineTransition(transitionGeneration);
            LOGI("RenderEngine: restart from beginning");
            continue;
        }

        // ========================================
        // 主播放循环
        // ========================================

        AVRational tb = demuxer_.videoTimeBase();
        double fps = demuxer_.fps();
        int64_t nominalDurationUs = (fps > 0) ? (int64_t)(1000000.0 / fps) : 33333;
        int64_t perfCycleStartUs = steadyNowUs();
        int64_t feedStartUs = perfCycleStartUs;

        // 第 1 步：正常播放态只做非阻塞喂包。
        // 一旦 MediaCodec 当前没有可用 input buffer，就立刻退出喂包阶段，
        // 避免像之前那样在 steady-state 下被 input timeout 卡住整帧循环。
        while (!eof) {
            if (!hasPendingPkt) {
                std::lock_guard<std::mutex> lock(demuxer_.readMutex());
                if (!demuxer_.readVideoPacket(pkt)) {
                    eof = true;
                    hwDecoder_.queueEOS();
                    break;
                }
                hasPendingPkt = true;
            }
            if (hwDecoder_.tryQueuePacket(pkt)) {
                av_packet_unref(pkt);
                hasPendingPkt = false;
            } else {
                break;
            }
        }
        int64_t feedEndUs = steadyNowUs();

        // 第 2 步：取出解码帧（不渲染），先判 PTS 再决定是否送 SurfaceTexture
        int64_t dequeueStartUs = feedEndUs;
        HwDecoder::DecodedFrame decodedFrame;
        bool gotFrame = hwDecoder_.dequeueOutput(decodedFrame, 30000);
        int64_t dequeueEndUs = steadyNowUs();
        if (!gotFrame) continue;

        int64_t framePtsUs = av_rescale_q(decodedFrame.pts, tb, {1, 1000000});

        // 第 2.5 步：trimIn/trimOut 边界判断（先判后渲染）
        if (framePtsUs < skipUntilPtsUs_) {
            // trimIn 之前的帧：丢弃（keyframe seek 可能解出更早的帧）
            hwDecoder_.releaseOutput(decodedFrame.bufferIndex, false);
            continue;
        }
        if (!timeline_.isEmpty() && activeClipIndex_ >= 0
            && framePtsUs >= timeline_.clipAt(activeClipIndex_).trimOut) {
            // 超过片段边界：丢弃，触发片段切换（Phase D 实现）
            hwDecoder_.releaseOutput(decodedFrame.bufferIndex, false);
            int next = activeClipIndex_ + 1;
            if (next < timeline_.clipCount()) {
                uint32_t gen = beginTimelineTransition();
                if (hasPendingPkt) { av_packet_unref(pkt); hasPendingPkt = false; }
                switchToClip(next, timeline_.clipAt(next).trimIn, env);
                frameTimerValid = false;
                lastFramePtsUs = -1;
                consecutiveDrops = 0;
                eof = false;
                endTimelineTransition(gen);
                continue;
            } else {
                eof = true;
                continue;
            }
        }

        // 第 3 步：帧在有效范围内，送入 SurfaceTexture
        hwDecoder_.releaseOutput(decodedFrame.bufferIndex, true);

        int64_t waitStartUs = dequeueEndUs;
        stHelper_.waitForFrame(50);
        int64_t waitEndUs = steadyNowUs();
        stHelper_.consumeFrameAvailable();
        int64_t updateStartUs = waitEndUs;
        stHelper_.updateTexImage(env);
        int64_t updateEndUs = steadyNowUs();

        // 更新全局位置
        if (!timeline_.isEmpty() && activeClipIndex_ >= 0) {
            const Clip& clip = timeline_.clipAt(activeClipIndex_);
            currentPositionUs_.store(clip.inPoint + (framePtsUs - clip.trimIn));
        } else {
            currentPositionUs_.store(framePtsUs);
        }

        // =============================================
        // 第 4 步：A/V 同步 — FFplay frame_timer + compute_target_delay
        //
        // 移植自 FFplay (ffplay.c) 的 video_refresh() 函数。
        // 累积式 frame_timer 不受单次 sleep 精度影响，
        // 自适应阈值避免高/低帧率下的抖动。
        // =============================================

        // --- 4a: 计算帧间隔 (last_duration) ---
        int64_t frameDurationUs;
        if (lastFramePtsUs >= 0 && framePtsUs > lastFramePtsUs) {
            frameDurationUs = framePtsUs - lastFramePtsUs;
            if (frameDurationUs < 1000) frameDurationUs = 1000;
            if (frameDurationUs > 1000000) frameDurationUs = 1000000;
        } else {
            frameDurationUs = nominalDurationUs;
        }
        lastFramePtsUs = framePtsUs;

        int64_t audioClockUs = getAudioClockUs();

        // --- 4b: 初始化 frame_timer（首帧/恢复后） ---
        auto wallNowUs = std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();

        if (!frameTimerValid) {
            if (audioClockUs >= 0) {
                int64_t avOffsetUs = audioClockUs - framePtsUs;
                if (avOffsetUs < -500000) avOffsetUs = -500000;
                if (avOffsetUs > 500000) avOffsetUs = 500000;
                frameTimerUs = wallNowUs - avOffsetUs;
            } else {
                frameTimerUs = wallNowUs;
            }
            frameTimerValid = true;
            LOGI("AVSYNC video-timeline-reset framePtsUs=%lld audioClockUs=%lld frameTimerUs=%lld wallNowUs=%lld",
                 (long long)framePtsUs,
                 (long long)(audioClockUs >= 0 ? audioClockUs : -1),
                 (long long)frameTimerUs,
                 (long long)wallNowUs);
        }

        // --- 4c: compute_target_delay (FFplay 核心算法) ---
        int64_t delay = frameDurationUs;
        if (audioClockUs >= 0) {
            int64_t diff = framePtsUs - audioClockUs;

            int64_t syncThresholdUs = frameDurationUs;
            if (syncThresholdUs < 40000)  syncThresholdUs = 40000;
            if (syncThresholdUs > 100000) syncThresholdUs = 100000;

            if (diff <= -syncThresholdUs) {
                delay = delay + diff;
                if (delay < 0) delay = 0;
            } else if (diff >= syncThresholdUs) {
                if (delay > 100000) {
                    delay = delay + diff;
                } else {
                    delay = 2 * delay;
                }
            }
        }

        // --- 4d: 基于 frame_timer 调度显示 ---
        int64_t targetTimeUs = frameTimerUs + delay;
        wallNowUs = std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();

        int64_t remainingUs = targetTimeUs - wallNowUs;
        if (remainingUs > 1000) {
            if (remainingUs > 200000) remainingUs = 200000;
            usleep((useconds_t)remainingUs);
        }

        // 推进 frame_timer（累积式）
        frameTimerUs += delay;

        // 如果太落后（>100ms），重置防止追赶风暴
        wallNowUs = std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
        if (wallNowUs - frameTimerUs > 100000) {
            frameTimerUs = wallNowUs;
        }

        // --- 4e: 丢帧判断（保守模式）---
        bool shouldRender = true;
        if (audioClockUs >= 0) {
            wallNowUs = std::chrono::duration_cast<std::chrono::microseconds>(
                std::chrono::steady_clock::now().time_since_epoch()).count();
            if (wallNowUs > frameTimerUs + frameDurationUs
                && consecutiveDrops < 1) {
                shouldRender = false;
                consecutiveDrops++;
            }
        }

        // 诊断日志
        int64_t diagNowUs = steadyNowUs();
        if (diagNowUs - lastVideoDiagLogUs >= kAvSyncDiagIntervalUs) {
            lastVideoDiagLogUs = diagNowUs;
            LOGI("AVSYNC video-diag framePtsUs=%lld audioClockUs=%lld diffUs=%lld delayUs=%lld frameDurUs=%lld render=%d drops=%d posUs=%lld",
                 (long long)framePtsUs,
                 (long long)audioClockUs,
                 (long long)(audioClockUs >= 0 ? (framePtsUs - audioClockUs) : 0),
                 (long long)delay,
                 (long long)frameDurationUs,
                 shouldRender ? 1 : 0,
                 consecutiveDrops,
                 (long long)currentPositionUs_.load());
        }

        // 第 5 步：渲染（通过渲染树）
        int64_t executeStartUs = steadyNowUs();
        if (shouldRender) {
            outputNode_->outputWidth = surfaceWidth;
            outputNode_->outputHeight = surfaceHeight;
            outputNode_->execute(framePtsUs);
        }
        int64_t executeEndUs = steadyNowUs();

        int64_t swapStartUs = executeEndUs;
        if (shouldRender) {
            eglCore_.swapBuffers(eglSurface_);
            consecutiveDrops = 0;
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

        // EOF 后：如果还有后续片段，不暂停，让下一轮循环走片段切换逻辑
        if (eof) {
            int next = activeClipIndex_ + 1;
            if (timeline_.isEmpty() || next >= timeline_.clipCount()) {
                // 真正的时间线 EOF：暂停并通知上层
                playing_.store(false);
                if (hasAudio_) audioOutput_.pause();
                LOGI("RenderEngine: playback completed (EOF)");
                if (callback_) {
                    callback_->onPlaybackCompleted();
                }
            } else {
                // 片段 EOF 但还有下一个：不暂停，直接进入下一轮切换
                LOGI("RenderEngine: clip %d EOF, next clip %d pending", activeClipIndex_, next);
            }
        }
    }

    // 清理资源
    if (hasPendingPkt) av_packet_unref(pkt);
    av_packet_free(&pkt);

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
    LOGI("RenderEngine: render thread stopped");
}
