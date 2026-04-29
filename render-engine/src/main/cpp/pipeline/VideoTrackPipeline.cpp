#include "pipeline/VideoTrackPipeline.h"
#include "common/log.h"

VideoTrackPipeline::VideoTrackPipeline() {
    pendingVideoPkt_ = av_packet_alloc();
    readPkt_ = av_packet_alloc();
}

VideoTrackPipeline::~VideoTrackPipeline() {
    if (hasPendingVideoPkt_ && pendingVideoPkt_) {
        av_packet_unref(pendingVideoPkt_);
    }
    if (pendingVideoPkt_) {
        av_packet_free(&pendingVideoPkt_);
    }
    if (readPkt_) {
        av_packet_free(&readPkt_);
    }
    clearAudioPacketQueue();
}

void VideoTrackPipeline::configureTimeline(Timeline timeline) {
    timeline_ = std::move(timeline);
}

bool VideoTrackPipeline::init(JNIEnv* env) {
    release(env);

    if (timeline_.isEmpty()) {
        LOGE("VideoTrackPipeline: init failed, timeline is empty");
        return false;
    }

    const Clip& firstClip = timeline_.clipAt(0);
    if (!demuxer_.open(firstClip.sourcePath.c_str())) {
        LOGE("VideoTrackPipeline: failed to open %s", firstClip.sourcePath.c_str());
        return false;
    }

    if (!stHelper_.create(env)) {
        LOGE("VideoTrackPipeline: SurfaceTexture create failed");
        demuxer_.close();
        return false;
    }

    if (!decoder_.init(demuxer_.videoCodecParameters(), stHelper_.nativeWindow())) {
        LOGE("VideoTrackPipeline: decoder init failed");
        stHelper_.release(env);
        demuxer_.close();
        return false;
    }

    activeDecoderConfig_ = DecoderConfig::fromCodecParameters(demuxer_.videoCodecParameters());
    activeClipIndex_ = 0;
    skipUntilPtsUs_ = firstClip.trimIn;

    int w = demuxer_.videoWidth();
    int h = demuxer_.videoHeight();
    sourceNode_ = std::make_unique<SourceNode>(&stHelper_, env);
    if (!sourceNode_->initGL(w, h)) {
        LOGE("VideoTrackPipeline: SourceNode GL init failed");
        release(env);
        return false;
    }

    if (firstClip.trimIn > 0) {
        demuxer_.seek(firstClip.trimIn);
    }

    eof_ = false;
    clearAudioPacketQueue();
    hasPendingVideoPkt_ = false;
    LOGI("VideoTrackPipeline: initialized %dx%d clip=0/%d", w, h, timeline_.clipCount());
    return true;
}

void VideoTrackPipeline::release(JNIEnv* env) {
    clearAudioPacketQueue();

    if (hasPendingVideoPkt_ && pendingVideoPkt_) {
        av_packet_unref(pendingVideoPkt_);
        hasPendingVideoPkt_ = false;
    }

    if (sourceNode_) {
        sourceNode_.reset();
    }

    decoder_.release();
    stHelper_.release(env);
    demuxer_.close();

    activeClipIndex_ = -1;
    activeDecoderConfig_ = DecoderConfig{};
    skipUntilPtsUs_ = 0;
    eof_ = false;
}

bool VideoTrackPipeline::switchToClip(int clipIndex, int64_t sourcePositionUs, JNIEnv* /*env*/) {
    if (clipIndex < 0 || clipIndex >= timeline_.clipCount()) return false;
    const Clip& clip = timeline_.clipAt(clipIndex);

    demuxer_.close();
    if (!demuxer_.open(clip.sourcePath.c_str())) {
        LOGE("VideoTrackPipeline: switchToClip open failed: %s", clip.sourcePath.c_str());
        return false;
    }

    DecoderConfig newConfig = DecoderConfig::fromCodecParameters(demuxer_.videoCodecParameters());
    if (newConfig == activeDecoderConfig_) {
        decoder_.flush();
    } else {
        decoder_.release();
        if (!decoder_.init(demuxer_.videoCodecParameters(), stHelper_.nativeWindow())) {
            LOGE("VideoTrackPipeline: switchToClip decoder re-init failed");
            return false;
        }
        activeDecoderConfig_ = newConfig;
    }

    int newW = demuxer_.videoWidth();
    int newH = demuxer_.videoHeight();
    if (sourceNode_ && (newW != sourceNode_->outputWidth || newH != sourceNode_->outputHeight)) {
        sourceNode_->releaseGL();
        sourceNode_->initGL(newW, newH);
    }

    stHelper_.consumeFrameAvailable();

    if (sourcePositionUs > 0) {
        demuxer_.seek(sourcePositionUs);
    }

    clearAudioPacketQueue();
    if (hasPendingVideoPkt_) {
        av_packet_unref(pendingVideoPkt_);
        hasPendingVideoPkt_ = false;
    }

    activeClipIndex_ = clipIndex;
    skipUntilPtsUs_ = sourcePositionUs;
    eof_ = false;
    return true;
}

bool VideoTrackPipeline::seekInCurrentClip(int64_t sourcePositionUs) {
    if (!demuxer_.isOpen()) return false;
    if (!demuxer_.seek(sourcePositionUs)) return false;
    decoder_.flush();
    clearAudioPacketQueue();
    if (hasPendingVideoPkt_) {
        av_packet_unref(pendingVideoPkt_);
        hasPendingVideoPkt_ = false;
    }
    skipUntilPtsUs_ = sourcePositionUs;
    eof_ = false;
    return true;
}

bool VideoTrackPipeline::queuePendingVideoPacket(int64_t timeoutUs, bool blocking) {
    if (!hasPendingVideoPkt_) return true;
    bool queued = blocking
        ? decoder_.queuePacketWithTimeout(pendingVideoPkt_, timeoutUs)
        : decoder_.tryQueuePacket(pendingVideoPkt_);
    if (queued) {
        av_packet_unref(pendingVideoPkt_);
        hasPendingVideoPkt_ = false;
    }
    return queued;
}

bool VideoTrackPipeline::enqueueAudioPacket(const AVPacket* packet) {
    if (!cacheAudioPackets_) {
        return true;
    }
    AVPacket* cached = av_packet_alloc();
    if (!cached) return false;
    if (av_packet_ref(cached, packet) < 0) {
        av_packet_free(&cached);
        return false;
    }
    std::lock_guard<std::mutex> lock(audioQueueMutex_);
    audioPacketQueue_.push_back(cached);
    return true;
}

void VideoTrackPipeline::clearAudioPacketQueue() {
    std::lock_guard<std::mutex> lock(audioQueueMutex_);
    for (auto* pkt : audioPacketQueue_) {
        av_packet_unref(pkt);
        av_packet_free(&pkt);
    }
    audioPacketQueue_.clear();
}

bool VideoTrackPipeline::pumpAvailablePackets() {
    bool progressed = false;
    if (!queuePendingVideoPacket(0, false)) {
        return false;
    }
    if (hasPendingVideoPkt_) return false;

    while (!eof_) {
        Demuxer::PacketType type = demuxer_.readPacket(readPkt_);
        if (type == Demuxer::PacketType::Eof) {
            eof_ = true;
            decoder_.queueEOS();
            break;
        }

        if (type == Demuxer::PacketType::Audio) {
            enqueueAudioPacket(readPkt_);
            av_packet_unref(readPkt_);
            progressed = true;
            continue;
        }

        av_packet_move_ref(pendingVideoPkt_, readPkt_);
        hasPendingVideoPkt_ = true;
        if (!queuePendingVideoPacket(0, false)) {
            break;
        }
        progressed = true;
    }
    return progressed;
}

bool VideoTrackPipeline::queueVideoPacketWithTimeout(int64_t timeoutUs) {
    if (!queuePendingVideoPacket(timeoutUs, true)) {
        return false;
    }
    if (hasPendingVideoPkt_) return false;

    while (!eof_) {
        Demuxer::PacketType type = demuxer_.readPacket(readPkt_);
        if (type == Demuxer::PacketType::Eof) {
            eof_ = true;
            decoder_.queueEOS();
            return false;
        }

        if (type == Demuxer::PacketType::Audio) {
            enqueueAudioPacket(readPkt_);
            av_packet_unref(readPkt_);
            continue;
        }

        av_packet_move_ref(pendingVideoPkt_, readPkt_);
        hasPendingVideoPkt_ = true;
        return queuePendingVideoPacket(timeoutUs, true);
    }
    return false;
}

bool VideoTrackPipeline::dequeueFrame(HwDecoder::DecodedFrame& frame, int64_t timeoutUs) {
    return decoder_.dequeueOutput(frame, timeoutUs);
}

void VideoTrackPipeline::releaseFrame(int32_t bufferIndex, bool render) {
    decoder_.releaseOutput(bufferIndex, render);
}

bool VideoTrackPipeline::consumeRenderedFrame(JNIEnv* env, int64_t timeoutMs) {
    stHelper_.waitForFrame(timeoutMs);
    stHelper_.consumeFrameAvailable();
    stHelper_.updateTexImage(env);
    return true;
}

bool VideoTrackPipeline::popAudioPacket(AVPacket* outPacket) {
    std::lock_guard<std::mutex> lock(audioQueueMutex_);
    if (audioPacketQueue_.empty()) return false;
    AVPacket* cached = audioPacketQueue_.front();
    audioPacketQueue_.pop_front();
    av_packet_move_ref(outPacket, cached);
    av_packet_free(&cached);
    return true;
}

void VideoTrackPipeline::setAudioPacketCachingEnabled(bool enabled) {
    cacheAudioPackets_ = enabled;
    if (!enabled) {
        clearAudioPacketQueue();
    }
}

int64_t VideoTrackPipeline::mapSourcePtsToTimelineUs(int64_t sourcePtsUs) const {
    if (timeline_.isEmpty() || activeClipIndex_ < 0) return sourcePtsUs;
    const Clip& clip = timeline_.clipAt(activeClipIndex_);
    return clip.inPoint + (sourcePtsUs - clip.trimIn);
}

bool VideoTrackPipeline::isFrameBeforeTrim(int64_t sourcePtsUs) const {
    return sourcePtsUs < skipUntilPtsUs_;
}

bool VideoTrackPipeline::isFrameAfterTrim(int64_t sourcePtsUs) const {
    return !timeline_.isEmpty() && activeClipIndex_ >= 0
        && sourcePtsUs >= timeline_.clipAt(activeClipIndex_).trimOut;
}

void VideoTrackPipeline::flushDecoder() {
    decoder_.flush();
}

void VideoTrackPipeline::queueEos() {
    decoder_.queueEOS();
}
