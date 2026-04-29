#pragma once

#include <cstdint>
#include <deque>
#include <memory>
#include <mutex>
#include "decode/Demuxer.h"
#include "decode/HwDecoder.h"
#include "decode/SurfaceTextureHelper.h"
#include "core/SourceNode.h"
#include "engine/DecoderConfig.h"
#include "engine/Timeline.h"

class AudioPipeline;

// 单轨视频管线：拥有 timeline / demux / decode / surface / source node 状态。
class VideoTrackPipeline {
public:
    VideoTrackPipeline();
    ~VideoTrackPipeline();

    void configureTimeline(Timeline timeline);
    const Timeline& timeline() const { return timeline_; }

    // 仅 GL 线程
    bool init(JNIEnv* env);
    // 仅 GL 线程
    void release(JNIEnv* env);

    // 仅渲染线程
    bool switchToClip(int clipIndex, int64_t sourcePositionUs, JNIEnv* env);
    // 仅渲染线程
    bool seekInCurrentClip(int64_t sourcePositionUs);

    // 正常播放态：非阻塞 pump 视频包，同时把音频包缓存到内部队列。
    // 仅渲染线程。
    bool pumpAvailablePackets();

    // seek / 预热：允许 bounded blocking 送一个视频包，同时继续缓存音频包。
    // 仅渲染线程。
    bool queueVideoPacketWithTimeout(int64_t timeoutUs);

    // 仅渲染线程
    bool dequeueFrame(HwDecoder::DecodedFrame& frame, int64_t timeoutUs);
    // 仅渲染线程
    void releaseFrame(int32_t bufferIndex, bool render);

    // 仅 GL 线程
    bool consumeRenderedFrame(JNIEnv* env, int64_t timeoutMs);

    // Phase 2 过渡接口：旧音频线程从轨道内部音频队列取包。
    bool popAudioPacket(AVPacket* outPacket);
    void setAudioPacketCachingEnabled(bool enabled);

    // 任意线程只读
    int64_t mapSourcePtsToTimelineUs(int64_t sourcePtsUs) const;
    bool isFrameBeforeTrim(int64_t sourcePtsUs) const;
    bool isFrameAfterTrim(int64_t sourcePtsUs) const;
    bool isEof() const { return eof_; }

    int activeClipIndex() const { return activeClipIndex_; }
    int64_t skipUntilPtsUs() const { return skipUntilPtsUs_; }
    void setSkipUntilPtsUs(int64_t value) { skipUntilPtsUs_ = value; }

    SourceNode* sourceNode() const { return sourceNode_.get(); }
    int videoWidth() const { return demuxer_.videoWidth(); }
    int videoHeight() const { return demuxer_.videoHeight(); }
    int64_t durationUs() const { return !timeline_.isEmpty() ? timeline_.durationUs() : demuxer_.durationUs(); }
    double fps() const { return demuxer_.fps(); }
    AVRational videoTimeBase() const { return demuxer_.videoTimeBase(); }

    bool hasAudio() const { return demuxer_.hasAudio(); }
    int audioSampleRate() const { return demuxer_.audioSampleRate(); }
    AVRational audioTimeBase() const { return demuxer_.audioTimeBase(); }
    AVCodecParameters* audioCodecParameters() const { return demuxer_.audioCodecParameters(); }

    void flushDecoder();
    void queueEos();

private:
    bool enqueueAudioPacket(const AVPacket* packet);
    void clearAudioPacketQueue();
    bool queuePendingVideoPacket(int64_t timeoutUs, bool blocking);

    Timeline timeline_;
    int activeClipIndex_ = -1;
    DecoderConfig activeDecoderConfig_;
    int64_t skipUntilPtsUs_ = 0;

    Demuxer demuxer_;
    HwDecoder decoder_;
    SurfaceTextureHelper stHelper_;
    std::unique_ptr<SourceNode> sourceNode_;

    AVPacket* pendingVideoPkt_ = nullptr;
    bool hasPendingVideoPkt_ = false;
    AVPacket* readPkt_ = nullptr;
    bool eof_ = false;

    mutable std::mutex audioQueueMutex_;
    std::deque<AVPacket*> audioPacketQueue_;
    bool cacheAudioPackets_ = true;
};
