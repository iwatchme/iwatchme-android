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
        LOGI("Demuxer: no bitstream filter needed");
        return true;
    }

    const AVBitStreamFilter* bsf = av_bsf_get_by_name(bsfName);
    if (!bsf) {
        LOGE("Demuxer: bitstream filter '%s' not found", bsfName);
        return false;
    }

    int ret = av_bsf_alloc(bsf, &bsfCtx_);
    if (ret < 0) return false;

    ret = avcodec_parameters_copy(bsfCtx_->par_in, par);
    if (ret < 0) { av_bsf_free(&bsfCtx_); return false; }

    bsfCtx_->time_base_in = formatCtx_->streams[videoStreamIndex_]->time_base;

    ret = av_bsf_init(bsfCtx_);
    if (ret < 0) { av_bsf_free(&bsfCtx_); return false; }

    LOGI("Demuxer: bitstream filter '%s' initialized", bsfName);
    return true;
}

bool Demuxer::applyBsf(AVPacket* packet) {
    if (!bsfCtx_) return true;

    int ret = av_bsf_send_packet(bsfCtx_, packet);
    if (ret < 0) return false;

    ret = av_bsf_receive_packet(bsfCtx_, packet);
    return ret >= 0;
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
    if (ret < 0) { close(); return false; }

    // 查找视频流
    videoStreamIndex_ = av_find_best_stream(formatCtx_, AVMEDIA_TYPE_VIDEO, -1, -1, nullptr, 0);
    if (videoStreamIndex_ < 0) {
        LOGE("Demuxer: no video stream found");
        close();
        return false;
    }

    // 查找音频流
    audioStreamIndex_ = av_find_best_stream(formatCtx_, AVMEDIA_TYPE_AUDIO, -1, -1, nullptr, 0);

    AVStream* vs = formatCtx_->streams[videoStreamIndex_];
    LOGI("Demuxer: opened %s", filePath);
    LOGI("  video: %dx%d, codec=%d, duration=%.2fs, fps=%.2f",
         vs->codecpar->width, vs->codecpar->height,
         vs->codecpar->codec_id,
         (double)durationUs() / 1000000.0, fps());

    if (audioStreamIndex_ >= 0) {
        AVStream* as = formatCtx_->streams[audioStreamIndex_];
        LOGI("  audio: %dHz, %dch, codec=%d",
             as->codecpar->sample_rate, as->codecpar->ch_layout.nb_channels,
             as->codecpar->codec_id);
    } else {
        LOGI("  audio: none");
    }

    if (!initBitstreamFilter()) { close(); return false; }

    return true;
}

void Demuxer::close() {
    flushPacketQueues();
    if (bsfCtx_) { av_bsf_free(&bsfCtx_); bsfCtx_ = nullptr; }
    if (formatCtx_) { avformat_close_input(&formatCtx_); formatCtx_ = nullptr; }
    videoStreamIndex_ = -1;
    audioStreamIndex_ = -1;
}

void Demuxer::flushPacketQueues() {
    for (auto* pkt : videoPacketQueue_) {
        av_packet_unref(pkt);
        av_packet_free(&pkt);
    }
    videoPacketQueue_.clear();
    for (auto* pkt : audioPacketQueue_) {
        av_packet_unref(pkt);
        av_packet_free(&pkt);
    }
    audioPacketQueue_.clear();
}

Demuxer::PacketType Demuxer::readPacket(AVPacket* packet) {
    while (true) {
        int ret = av_read_frame(formatCtx_, packet);
        if (ret < 0) return PacketType::Eof;

        if (packet->stream_index == videoStreamIndex_) {
            // 对视频 packet 应用 BSF（AVCC → Annex B）
            if (bsfCtx_) {
                ret = av_bsf_send_packet(bsfCtx_, packet);
                if (ret < 0) { av_packet_unref(packet); continue; }
                ret = av_bsf_receive_packet(bsfCtx_, packet);
                if (ret < 0) {
                    if (ret == AVERROR(EAGAIN)) continue;
                    return PacketType::Eof;
                }
            }
            return PacketType::Video;
        }

        if (packet->stream_index == audioStreamIndex_) {
            return PacketType::Audio;
        }

        av_packet_unref(packet);  // 跳过其他流
    }
}

bool Demuxer::readVideoPacket(AVPacket* packet) {
    // 先从缓存队列取
    if (!videoPacketQueue_.empty()) {
        AVPacket* cached = videoPacketQueue_.front();
        videoPacketQueue_.pop_front();
        av_packet_move_ref(packet, cached);
        av_packet_free(&cached);
        return true;
    }

    // 从文件读，遇到音频包则缓存到 audioQueue_
    while (true) {
        int ret = av_read_frame(formatCtx_, packet);
        if (ret < 0) return false;

        if (packet->stream_index == videoStreamIndex_) {
            if (bsfCtx_) {
                ret = av_bsf_send_packet(bsfCtx_, packet);
                if (ret < 0) { av_packet_unref(packet); continue; }
                ret = av_bsf_receive_packet(bsfCtx_, packet);
                if (ret < 0) {
                    if (ret == AVERROR(EAGAIN)) continue;
                    return false;
                }
            }
            return true;
        }

        if (packet->stream_index == audioStreamIndex_) {
            // 缓存音频包，给音频线程用
            AVPacket* cached = av_packet_alloc();
            av_packet_move_ref(cached, packet);
            audioPacketQueue_.push_back(cached);
            continue;
        }

        av_packet_unref(packet);  // 跳过其他流
    }
}

bool Demuxer::readAudioPacket(AVPacket* packet) {
    // 先从缓存队列取
    if (!audioPacketQueue_.empty()) {
        AVPacket* cached = audioPacketQueue_.front();
        audioPacketQueue_.pop_front();
        av_packet_move_ref(packet, cached);
        av_packet_free(&cached);
        return true;
    }

    // 从文件读，遇到视频包则缓存到 videoQueue_
    while (true) {
        int ret = av_read_frame(formatCtx_, packet);
        if (ret < 0) return false;

        if (packet->stream_index == audioStreamIndex_) {
            return true;
        }

        if (packet->stream_index == videoStreamIndex_) {
            // 缓存视频包（含 BSF 转换），给视频线程用
            if (bsfCtx_) {
                ret = av_bsf_send_packet(bsfCtx_, packet);
                if (ret < 0) { av_packet_unref(packet); continue; }
                ret = av_bsf_receive_packet(bsfCtx_, packet);
                if (ret < 0) {
                    if (ret == AVERROR(EAGAIN)) continue;
                    av_packet_unref(packet);
                    continue;
                }
            }
            AVPacket* cached = av_packet_alloc();
            av_packet_move_ref(cached, packet);
            videoPacketQueue_.push_back(cached);
            continue;
        }

        av_packet_unref(packet);  // 跳过其他流
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

    if (bsfCtx_) av_bsf_flush(bsfCtx_);

    // seek 后清空缓存队列，旧数据已无效
    flushPacketQueues();

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
    if (stream->avg_frame_rate.den > 0) return av_q2d(stream->avg_frame_rate);
    if (stream->r_frame_rate.den > 0) return av_q2d(stream->r_frame_rate);
    return 30.0;
}

AVRational Demuxer::videoTimeBase() const {
    if (!formatCtx_ || videoStreamIndex_ < 0) return {1, 1000000};
    return formatCtx_->streams[videoStreamIndex_]->time_base;
}

AVCodecParameters* Demuxer::videoCodecParameters() const {
    if (bsfCtx_) return bsfCtx_->par_out;
    if (!formatCtx_ || videoStreamIndex_ < 0) return nullptr;
    return formatCtx_->streams[videoStreamIndex_]->codecpar;
}

int Demuxer::audioSampleRate() const {
    if (!formatCtx_ || audioStreamIndex_ < 0) return 0;
    return formatCtx_->streams[audioStreamIndex_]->codecpar->sample_rate;
}

int Demuxer::audioChannels() const {
    if (!formatCtx_ || audioStreamIndex_ < 0) return 0;
    return formatCtx_->streams[audioStreamIndex_]->codecpar->ch_layout.nb_channels;
}

AVRational Demuxer::audioTimeBase() const {
    if (!formatCtx_ || audioStreamIndex_ < 0) return {1, 1000000};
    return formatCtx_->streams[audioStreamIndex_]->time_base;
}

AVCodecParameters* Demuxer::audioCodecParameters() const {
    if (!formatCtx_ || audioStreamIndex_ < 0) return nullptr;
    return formatCtx_->streams[audioStreamIndex_]->codecpar;
}
