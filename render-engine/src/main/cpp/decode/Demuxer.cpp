#include "Demuxer.h"
#include "common/log.h"

Demuxer::~Demuxer() {
    close();
}

bool Demuxer::initBitstreamFilter() {
    if (!formatCtx_ || videoStreamIndex_ < 0) return false;

    AVCodecParameters* par = formatCtx_->streams[videoStreamIndex_]->codecpar;
    const char* bsfName = nullptr;

    if (par->codec_id == AV_CODEC_ID_H264) {
        // Check if extradata is AVCC format (not Annex B)
        if (par->extradata && par->extradata_size >= 4 &&
            !(par->extradata[0] == 0 && par->extradata[1] == 0 &&
              (par->extradata[2] == 1 || (par->extradata[2] == 0 && par->extradata[3] == 1)))) {
            bsfName = "h264_mp4toannexb";
        }
    } else if (par->codec_id == AV_CODEC_ID_HEVC) {
        if (par->extradata && par->extradata_size >= 4 &&
            !(par->extradata[0] == 0 && par->extradata[1] == 0 &&
              (par->extradata[2] == 1 || (par->extradata[2] == 0 && par->extradata[3] == 1)))) {
            bsfName = "hevc_mp4toannexb";
        }
    }

    if (!bsfName) {
        LOGI("Demuxer: no bitstream filter needed (already Annex B or unsupported codec)");
        return true;  // Not an error — stream may already be Annex B
    }

    const AVBitStreamFilter* bsf = av_bsf_get_by_name(bsfName);
    if (!bsf) {
        LOGE("Demuxer: bitstream filter '%s' not found", bsfName);
        return false;
    }

    int ret = av_bsf_alloc(bsf, &bsfCtx_);
    if (ret < 0) {
        LOGE("Demuxer: av_bsf_alloc failed: %d", ret);
        return false;
    }

    ret = avcodec_parameters_copy(bsfCtx_->par_in, par);
    if (ret < 0) {
        LOGE("Demuxer: avcodec_parameters_copy to bsf failed: %d", ret);
        av_bsf_free(&bsfCtx_);
        return false;
    }

    bsfCtx_->time_base_in = formatCtx_->streams[videoStreamIndex_]->time_base;

    ret = av_bsf_init(bsfCtx_);
    if (ret < 0) {
        LOGE("Demuxer: av_bsf_init failed: %d", ret);
        av_bsf_free(&bsfCtx_);
        return false;
    }

    LOGI("Demuxer: bitstream filter '%s' initialized, extradata_size: %d -> %d",
         bsfName, par->extradata_size, bsfCtx_->par_out->extradata_size);
    return true;
}

bool Demuxer::open(const char* filePath) {
    close();

    int ret = avformat_open_input(&formatCtx_, filePath, nullptr, nullptr);
    if (ret < 0) {
        char errBuf[AV_ERROR_MAX_STRING_SIZE];
        av_strerror(ret, errBuf, sizeof(errBuf));
        LOGE("Demuxer: avformat_open_input failed: %s", errBuf);
        return false;
    }

    ret = avformat_find_stream_info(formatCtx_, nullptr);
    if (ret < 0) {
        LOGE("Demuxer: avformat_find_stream_info failed");
        close();
        return false;
    }

    videoStreamIndex_ = av_find_best_stream(formatCtx_, AVMEDIA_TYPE_VIDEO,
                                             -1, -1, nullptr, 0);
    if (videoStreamIndex_ < 0) {
        LOGE("Demuxer: no video stream found");
        close();
        return false;
    }

    AVStream* stream = formatCtx_->streams[videoStreamIndex_];
    LOGI("Demuxer: opened %s", filePath);
    LOGI("  video: %dx%d, codec=%d, duration=%.2fs, fps=%.2f",
         stream->codecpar->width, stream->codecpar->height,
         stream->codecpar->codec_id,
         (double)durationUs() / 1000000.0, fps());

    // Initialize bitstream filter for AVCC -> Annex B conversion
    if (!initBitstreamFilter()) {
        LOGE("Demuxer: failed to init bitstream filter");
        close();
        return false;
    }

    return true;
}

void Demuxer::close() {
    if (bsfCtx_) {
        av_bsf_free(&bsfCtx_);
        bsfCtx_ = nullptr;
    }
    if (formatCtx_) {
        avformat_close_input(&formatCtx_);
        formatCtx_ = nullptr;
    }
    videoStreamIndex_ = -1;
}

bool Demuxer::readVideoPacket(AVPacket* packet) {
    while (true) {
        int ret = av_read_frame(formatCtx_, packet);
        if (ret < 0) {
            return false;  // EOF or error
        }
        if (packet->stream_index != videoStreamIndex_) {
            av_packet_unref(packet);  // Skip non-video packets
            continue;
        }

        // Apply bitstream filter if active (AVCC -> Annex B)
        if (bsfCtx_) {
            ret = av_bsf_send_packet(bsfCtx_, packet);
            if (ret < 0) {
                av_packet_unref(packet);
                continue;
            }
            ret = av_bsf_receive_packet(bsfCtx_, packet);
            if (ret < 0) {
                // EAGAIN means bsf needs more input; loop to read next frame
                if (ret == AVERROR(EAGAIN)) continue;
                return false;
            }
        }

        return true;
    }
}

bool Demuxer::seek(int64_t positionUs) {
    if (!formatCtx_) return false;

    AVStream* stream = formatCtx_->streams[videoStreamIndex_];
    int64_t ts = av_rescale_q(positionUs, {1, 1000000}, stream->time_base);

    int ret = av_seek_frame(formatCtx_, videoStreamIndex_, ts, AVSEEK_FLAG_BACKWARD);
    if (ret < 0) {
        LOGE("Demuxer: seek failed to %lld us", (long long)positionUs);
        return false;
    }

    // Flush bitstream filter state after seek
    if (bsfCtx_) {
        av_bsf_flush(bsfCtx_);
    }

    return true;
}

int Demuxer::videoWidth() const {
    if (!formatCtx_ || videoStreamIndex_ < 0) return 0;
    return formatCtx_->streams[videoStreamIndex_]->codecpar->width;
}

int Demuxer::videoHeight() const {
    if (!formatCtx_ || videoStreamIndex_ < 0) return 0;
    return formatCtx_->streams[videoStreamIndex_]->codecpar->height;
}

int64_t Demuxer::durationUs() const {
    if (!formatCtx_) return 0;
    if (formatCtx_->duration != AV_NOPTS_VALUE) {
        return av_rescale_q(formatCtx_->duration, {1, AV_TIME_BASE}, {1, 1000000});
    }
    return 0;
}

double Demuxer::fps() const {
    if (!formatCtx_ || videoStreamIndex_ < 0) return 0.0;
    AVStream* stream = formatCtx_->streams[videoStreamIndex_];
    if (stream->avg_frame_rate.den > 0) {
        return av_q2d(stream->avg_frame_rate);
    }
    if (stream->r_frame_rate.den > 0) {
        return av_q2d(stream->r_frame_rate);
    }
    return 30.0;  // fallback
}

AVRational Demuxer::timeBase() const {
    if (!formatCtx_ || videoStreamIndex_ < 0) return {1, 1000000};
    return formatCtx_->streams[videoStreamIndex_]->time_base;
}

AVCodecParameters* Demuxer::codecParameters() const {
    // Return filtered params if bsf is active (has Annex B extradata)
    if (bsfCtx_) {
        return bsfCtx_->par_out;
    }
    if (!formatCtx_ || videoStreamIndex_ < 0) return nullptr;
    return formatCtx_->streams[videoStreamIndex_]->codecpar;
}
