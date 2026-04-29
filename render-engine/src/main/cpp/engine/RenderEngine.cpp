#include "RenderEngine.h"
#include "common/log.h"
#include <GLES3/gl3.h>
#include <unistd.h>
#include <chrono>

extern "C" {
#include <libavcodec/avcodec.h>
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
    std::lock_guard<std::mutex> lock(videoSourceMutex_);
    videoPath_ = filePath;
    videoSourceChanged_.store(true);
    cmdCond_.notify_one();
    LOGI("RenderEngine: video source queued: %s", filePath.c_str());
    return true;
}

void RenderEngine::play() {
    playing_.store(true);
    cmdCond_.notify_one();
    LOGI("RenderEngine: play");
}

void RenderEngine::pause() {
    playing_.store(false);
    LOGI("RenderEngine: pause");
}

void RenderEngine::seek(int64_t positionUs) {
    seekTargetUs_.store(positionUs);
    seekFastTargetUs_.store(-1);  // 精确 seek 取消任何未完成的快速 seek
    cmdCond_.notify_one();
}

void RenderEngine::seekFast(int64_t positionUs) {
    seekFastTargetUs_.store(positionUs);
    cmdCond_.notify_one();
}

int64_t RenderEngine::getDuration() const { return demuxer_.durationUs(); }
int64_t RenderEngine::getPosition() const { return currentPositionUs_.load(); }
int RenderEngine::getVideoWidth() const { return demuxer_.videoWidth(); }
int RenderEngine::getVideoHeight() const { return demuxer_.videoHeight(); }

void RenderEngine::startRenderThread() {
    running_.store(true);
    renderThread_ = std::thread(&RenderEngine::renderThreadFunc, this);
}

void RenderEngine::stopRenderThread() {
    running_.store(false);
    playing_.store(false);
    cmdCond_.notify_one();
    if (renderThread_.joinable()) renderThread_.join();
}

bool RenderEngine::initDecodePipeline(JNIEnv* env) {
    releaseDecodePipeline(env);

    std::string path;
    {
        std::lock_guard<std::mutex> lock(videoSourceMutex_);
        path = videoPath_;
    }

    if (!demuxer_.open(path.c_str())) {
        LOGE("RenderEngine: failed to open video: %s", path.c_str());
        return false;
    }

    if (!stHelper_.create(env)) {
        LOGE("RenderEngine: failed to create SurfaceTexture");
        return false;
    }

    if (!hwDecoder_.init(demuxer_.codecParameters(), stHelper_.nativeWindow())) {
        LOGE("RenderEngine: failed to init HwDecoder");
        stHelper_.release(env);
        return false;
    }

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

    currentPositionUs_.store(0);
    pipelineInitialized_ = true;
    LOGI("RenderEngine: pipeline initialized %dx%d", w, h);
    return true;
}

void RenderEngine::releaseDecodePipeline(JNIEnv* env) {
    if (!pipelineInitialized_) return;

    delete outputNode_;  outputNode_ = nullptr;
    delete sourceNode_;  sourceNode_ = nullptr;

    hwDecoder_.release();
    stHelper_.release(env);
    demuxer_.close();

    pipelineInitialized_ = false;
    LOGI("RenderEngine: pipeline released");
}

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
    double videoFps = 30.0;

    // 持久化 packet —— 不会被丢弃，如果 codec 无法接收则下轮重试
    AVPacket* pkt = av_packet_alloc();
    bool hasPendingPkt = false;

    // PTS 锚定同步：建立系统时钟和视频时间线之间的对应关系
    int64_t anchorWallUs = 0;   // 播放开始/恢复时的系统时间
    int64_t anchorPtsUs = -1;   // 播放开始/恢复后第一帧的 PTS

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
                videoSourceChanged_.store(false);
                // 重新初始化前清理残留的 pending packet
                if (hasPendingPkt) {
                    av_packet_unref(pkt);
                    hasPendingPkt = false;
                }
                if (initDecodePipeline(env)) {
                    videoFps = demuxer_.fps();
                    if (videoFps <= 0) videoFps = 30.0;
                    anchorPtsUs = -1;  // 重置同步锚点
                    eof = false;
                    LOGI("RenderEngine: ready, fps=%.1f", videoFps);
                }
            }
        }

        // --- 处理快速 Seek（拖动中：只显示最近关键帧） ---
        int64_t fastTarget = seekFastTargetUs_.exchange(-1);
        if (fastTarget >= 0 && pipelineInitialized_) {
            if (hasPendingPkt) {
                av_packet_unref(pkt);
                hasPendingPkt = false;
            }
            demuxer_.seek(fastTarget);
            hwDecoder_.flush();
            anchorPtsUs = -1;
            eof = false;

            // 只喂入 packet 直到解出第一帧（关键帧），立即渲染
            AVRational tb = demuxer_.timeBase();
            bool gotFrame = false;
            for (int attempt = 0; attempt < 100 && !gotFrame && running_.load(); attempt++) {
                // 喂入数据
                if (!hasPendingPkt) {
                    if (!demuxer_.readVideoPacket(pkt)) break;
                    hasPendingPkt = true;
                }
                if (hwDecoder_.queuePacket(pkt)) {
                    av_packet_unref(pkt);
                    hasPendingPkt = false;
                }

                // 尝试取出一帧
                int64_t pts = hwDecoder_.dequeueAndRender(5000);
                if (pts >= 0) {
                    stHelper_.waitForFrame(50);
                    stHelper_.consumeFrameAvailable();
                    stHelper_.updateTexImage(env);

                    int64_t framePtsUs = av_rescale_q(pts, tb, {1, 1000000});
                    currentPositionUs_.store(framePtsUs);

                    outputNode_->outputWidth = surfaceWidth;
                    outputNode_->outputHeight = surfaceHeight;
                    outputNode_->execute(framePtsUs);
                    eglCore_.swapBuffers(eglSurface_);
                    gotFrame = true;
                }
            }
            // 如果在快速 seek 过程中又来了新的快速 seek 请求，跳过后续直接回到循环顶部处理
            if (seekFastTargetUs_.load() >= 0) continue;
        }

        // --- 处理精确 Seek（松手后：解码到目标帧） ---
        int64_t seekTarget = seekTargetUs_.exchange(-1);
        if (seekTarget >= 0 && pipelineInitialized_) {
            if (hasPendingPkt) {
                av_packet_unref(pkt);
                hasPendingPkt = false;
            }
            demuxer_.seek(seekTarget);
            hwDecoder_.flush();
            anchorPtsUs = -1;  // 重置同步锚点
            eof = false;

            // 精确 Seek：从关键帧开始向前解码，直到 PTS >= 目标位置
            AVRational tb = demuxer_.timeBase();
            bool reachedTarget = false;
            int skipCount = 0;

            while (!reachedTarget && running_.load()) {
                // 喂入压缩包
                while (!eof) {
                    if (!hasPendingPkt) {
                        if (!demuxer_.readVideoPacket(pkt)) {
                            eof = true;
                            hwDecoder_.queueEOS();
                            break;
                        }
                        hasPendingPkt = true;
                    }
                    if (hwDecoder_.queuePacket(pkt)) {
                        av_packet_unref(pkt);
                        hasPendingPkt = false;
                    } else {
                        break;
                    }
                }

                // 尝试取出一帧解码后的数据
                int64_t pts = hwDecoder_.dequeueAndRender(30000);
                if (pts < 0) {
                    if (eof) break;  // 没有更多帧了
                    continue;        // 还没有帧就绪，继续喂入数据
                }

                int64_t framePtsUs = av_rescale_q(pts, tb, {1, 1000000});

                if (framePtsUs >= seekTarget) {
                    // 已到达或超过目标位置 —— 这是要显示的帧
                    stHelper_.waitForFrame(50);
                    stHelper_.consumeFrameAvailable();
                    stHelper_.updateTexImage(env);
                    currentPositionUs_.store(framePtsUs);

                    // 渲染这一帧到屏幕
                    outputNode_->outputWidth = surfaceWidth;
                    outputNode_->outputHeight = surfaceHeight;
                    outputNode_->execute(framePtsUs);
                    eglCore_.swapBuffers(eglSurface_);

                    reachedTarget = true;
                    LOGI("RenderEngine: precise seek done, skipped %d frames, target=%lld actual=%lld",
                         skipCount, (long long)seekTarget, (long long)framePtsUs);
                } else {
                    // 还没到目标位置 —— 消费这帧但不渲染到屏幕
                    stHelper_.waitForFrame(50);
                    stHelper_.consumeFrameAvailable();
                    stHelper_.updateTexImage(env);
                    skipCount++;
                }
            }

            currentPositionUs_.store(seekTarget);
        }

        // --- 空闲等待（未播放时） ---
        if (!playing_.load() || eglSurface_ == EGL_NO_SURFACE || !pipelineInitialized_) {
            anchorPtsUs = -1;  // 重置锚点，恢复播放时重新同步
            std::unique_lock<std::mutex> lock(cmdMutex_);
            cmdCond_.wait_for(lock, std::chrono::milliseconds(50));
            continue;
        }

        if (eof) {
            // EOF 状态下再次 Play → 自动从头开始
            if (hasPendingPkt) {
                av_packet_unref(pkt);
                hasPendingPkt = false;
            }
            demuxer_.seek(0);
            hwDecoder_.flush();
            currentPositionUs_.store(0);
            anchorPtsUs = -1;
            eof = false;
            LOGI("RenderEngine: restart from beginning");
            continue;
        }

        // ========================================
        // 主播放循环
        // ========================================

        // 第 1 步：持续喂入压缩包，直到 codec 表示"满了"
        while (!eof) {
            if (!hasPendingPkt) {
                if (!demuxer_.readVideoPacket(pkt)) {
                    eof = true;
                    hwDecoder_.queueEOS();
                    break;
                }
                hasPendingPkt = true;
            }
            if (hwDecoder_.queuePacket(pkt)) {
                av_packet_unref(pkt);
                hasPendingPkt = false;
            } else {
                break;  // codec 输入已满 —— 保留 packet 下轮重试
            }
        }

        // 第 2 步：取出一帧解码后的数据
        int64_t pts = hwDecoder_.dequeueAndRender(30000);
        if (pts < 0) continue;  // 还没有帧就绪 —— 回到循环顶部继续喂数据

        // 等待帧到达 SurfaceTexture
        stHelper_.waitForFrame(50);
        stHelper_.consumeFrameAvailable();
        stHelper_.updateTexImage(env);

        AVRational tb = demuxer_.timeBase();
        int64_t framePtsUs = av_rescale_q(pts, tb, {1, 1000000});
        currentPositionUs_.store(framePtsUs);

        // 第 3 步：基于 PTS 的帧节奏控制
        // 锚点在播放/seek 后的第一帧时建立
        auto nowUs = std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();

        if (anchorPtsUs < 0) {
            // 第一帧 —— 建立锚点
            anchorPtsUs = framePtsUs;
            anchorWallUs = nowUs;
        } else {
            // 计算视频时间和系统时间各自从锚点经过了多久
            int64_t videoDeltaUs = framePtsUs - anchorPtsUs;
            int64_t wallDeltaUs  = nowUs - anchorWallUs;
            int64_t sleepUs = videoDeltaUs - wallDeltaUs;

            if (sleepUs > 1000) {
                // 帧超前于进度 —— 等待
                usleep(sleepUs);
            }
            // sleepUs < 0 说明落后了 —— 立即渲染（不丢帧）
        }

        // 第 4 步：渲染（通过渲染树）
        outputNode_->outputWidth = surfaceWidth;
        outputNode_->outputHeight = surfaceHeight;
        outputNode_->execute(framePtsUs);
        eglCore_.swapBuffers(eglSurface_);
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
