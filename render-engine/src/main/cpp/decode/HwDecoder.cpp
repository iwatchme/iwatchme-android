#include "HwDecoder.h"
#include "common/log.h"
#include <cstring>
#include <vector>

HwDecoder::~HwDecoder() {
    release();
}

bool HwDecoder::configureCsd(AVCodecParameters* params, AMediaFormat* format) {
    if (!params->extradata || params->extradata_size < 4) {
        LOGE("HwDecoder: no extradata for CSD");
        return false;
    }

    uint8_t* data = params->extradata;
    int size = params->extradata_size;

    // Annex B format (from bitstream filter)
    if (data[0] == 0 && data[1] == 0 && (data[2] == 1 || (data[2] == 0 && data[3] == 1))) {
        if (params->codec_id == AV_CODEC_ID_H264) {
            // Split SPS and PPS into csd-0 and csd-1
            int splitPos = -1;
            for (int i = 4; i < size - 3; i++) {
                if (data[i] == 0 && data[i+1] == 0 &&
                    (data[i+2] == 1 || (data[i+2] == 0 && i+3 < size && data[i+3] == 1))) {
                    splitPos = i;
                    break;
                }
            }
            if (splitPos > 0) {
                AMediaFormat_setBuffer(format, "csd-0", data, splitPos);
                AMediaFormat_setBuffer(format, "csd-1", data + splitPos, size - splitPos);
                LOGI("HwDecoder: H.264 Annex B: csd-0=%d, csd-1=%d bytes", splitPos, size - splitPos);
            } else {
                AMediaFormat_setBuffer(format, "csd-0", data, size);
            }
        } else {
            AMediaFormat_setBuffer(format, "csd-0", data, size);
        }
        return true;
    }

    // AVCC format fallback
    if (params->codec_id == AV_CODEC_ID_H264 && size >= 7) {
        int numSPS = data[5] & 0x1F;
        int offset = 6;
        std::vector<uint8_t> sps, pps;

        for (int i = 0; i < numSPS && offset + 2 <= size; i++) {
            int len = (data[offset] << 8) | data[offset+1]; offset += 2;
            if (offset + len > size) break;
            sps.insert(sps.end(), {0,0,0,1});
            sps.insert(sps.end(), data+offset, data+offset+len);
            offset += len;
        }
        if (offset < size) {
            int numPPS = data[offset++];
            for (int i = 0; i < numPPS && offset + 2 <= size; i++) {
                int len = (data[offset] << 8) | data[offset+1]; offset += 2;
                if (offset + len > size) break;
                pps.insert(pps.end(), {0,0,0,1});
                pps.insert(pps.end(), data+offset, data+offset+len);
                offset += len;
            }
        }
        if (!sps.empty()) AMediaFormat_setBuffer(format, "csd-0", sps.data(), sps.size());
        if (!pps.empty()) AMediaFormat_setBuffer(format, "csd-1", pps.data(), pps.size());
        LOGI("HwDecoder: AVCC parsed: SPS=%zu, PPS=%zu", sps.size(), pps.size());
        return true;
    }

    if (params->codec_id == AV_CODEC_ID_HEVC) {
        AMediaFormat_setBuffer(format, "csd-0", data, size);
        return true;
    }

    return false;
}

bool HwDecoder::init(AVCodecParameters* params, ANativeWindow* outputSurface) {
    release();

    const char* mime = nullptr;
    switch (params->codec_id) {
        case AV_CODEC_ID_H264:  mime = "video/avc"; break;
        case AV_CODEC_ID_HEVC:  mime = "video/hevc"; break;
        case AV_CODEC_ID_VP9:   mime = "video/x-vnd.on2.vp9"; break;
        default:
            LOGE("HwDecoder: unsupported codec: %d", params->codec_id);
            return false;
    }

    codec_ = AMediaCodec_createDecoderByType(mime);
    if (!codec_) {
        LOGE("HwDecoder: cannot create decoder for %s", mime);
        return false;
    }

    AMediaFormat* format = AMediaFormat_new();
    AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, mime);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_WIDTH, params->width);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_HEIGHT, params->height);

    if (params->extradata && params->extradata_size > 0) {
        configureCsd(params, format);
    }

    const char* fmtStr = AMediaFormat_toString(format);
    LOGI("HwDecoder: configure format: %s", fmtStr ? fmtStr : "?");
    LOGI("HwDecoder: output surface: %p", outputSurface);

    // Configure WITH surface — Surface mode (zero-copy to SurfaceTexture)
    media_status_t status = AMediaCodec_configure(codec_, format, outputSurface, nullptr, 0);
    AMediaFormat_delete(format);

    if (status != AMEDIA_OK) {
        LOGE("HwDecoder: configure failed: %d", status);
        release();
        return false;
    }

    status = AMediaCodec_start(codec_);
    if (status != AMEDIA_OK) {
        LOGE("HwDecoder: start failed: %d", status);
        release();
        return false;
    }

    LOGI("HwDecoder: started %s %dx%d (Surface mode)", mime, params->width, params->height);
    return true;
}

void HwDecoder::release() {
    if (codec_) {
        AMediaCodec_stop(codec_);
        AMediaCodec_delete(codec_);
        codec_ = nullptr;
    }
    queuedCount_ = 0;
    dequeuedCount_ = 0;
}

bool HwDecoder::tryQueuePacket(AVPacket* packet) {
    // steady-state 播放态必须是 opportunistic pump：
    // 能喂就喂，不能喂就立刻返回，把时间留给 drain/output/render。
    return queuePacketInternal(packet, 0);
}

bool HwDecoder::queuePacketWithTimeout(AVPacket* packet, int64_t timeoutUs) {
    // seek / 首帧预热允许 bounded blocking，因为目标是尽快拿到目标帧，
    // 而不是维持稳定帧节奏。
    return queuePacketInternal(packet, timeoutUs);
}

bool HwDecoder::queuePacketInternal(AVPacket* packet, int64_t timeoutUs) {
    if (!codec_) return false;

    ssize_t bufIdx = AMediaCodec_dequeueInputBuffer(codec_, timeoutUs);
    if (bufIdx < 0) return false;

    size_t bufSize = 0;
    uint8_t* buf = AMediaCodec_getInputBuffer(codec_, bufIdx, &bufSize);
    if (!buf || bufSize < (size_t)packet->size) {
        AMediaCodec_queueInputBuffer(codec_, bufIdx, 0, 0, 0, 0);
        return false;
    }

    memcpy(buf, packet->data, packet->size);

    int64_t pts = packet->pts;
    if (pts == AV_NOPTS_VALUE) pts = packet->dts;
    if (pts == AV_NOPTS_VALUE) pts = 0;

    AMediaCodec_queueInputBuffer(codec_, bufIdx, 0, packet->size, pts, 0);

    if (queuedCount_ < 5) {
        LOGI("HwDecoder: queued #%d size=%d pts=%lld [%02x %02x %02x %02x]",
             queuedCount_, packet->size, (long long)pts,
             packet->data[0], packet->data[1], packet->data[2], packet->data[3]);
    }
    queuedCount_++;
    return true;
}

void HwDecoder::queueEOS() {
    if (!codec_) return;
    ssize_t bufIdx = AMediaCodec_dequeueInputBuffer(codec_, 10000);
    if (bufIdx >= 0) {
        AMediaCodec_queueInputBuffer(codec_, bufIdx, 0, 0, 0,
                                      AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
    }
}

// ---- 两步 API ----

bool HwDecoder::dequeueOutput(DecodedFrame& outFrame, int64_t timeoutUs) {
    if (!codec_) return false;

    for (int attempt = 0; attempt < 3; attempt++) {
        AMediaCodecBufferInfo info;
        ssize_t bufIdx = AMediaCodec_dequeueOutputBuffer(codec_, &info, timeoutUs);

        if (bufIdx >= 0) {
            outFrame.bufferIndex = (int32_t)bufIdx;
            outFrame.pts = info.presentationTimeUs;
            dequeuedCount_++;
            return true;
        }

        if (bufIdx == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            AMediaFormat* fmt = AMediaCodec_getOutputFormat(codec_);
            if (fmt) {
                LOGI("HwDecoder: format changed: %s", AMediaFormat_toString(fmt));
                AMediaFormat_delete(fmt);
            }
            timeoutUs = 10000;
            continue;
        }

        if (bufIdx == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
            continue;
        }

        // TRY_AGAIN_LATER
        break;
    }

    return false;
}

void HwDecoder::releaseOutput(int32_t bufferIndex, bool render) {
    if (!codec_ || bufferIndex < 0) return;
    AMediaCodec_releaseOutputBuffer(codec_, bufferIndex, render);
}

// ---- 便捷 API（始终 render=true）----

int64_t HwDecoder::dequeueAndRender(int64_t timeoutUs) {
    DecodedFrame frame;
    if (!dequeueOutput(frame, timeoutUs)) return -1;
    releaseOutput(frame.bufferIndex, true);
    return frame.pts;
}

void HwDecoder::flush() {
    if (codec_) {
        AMediaCodec_flush(codec_);
    }
}
