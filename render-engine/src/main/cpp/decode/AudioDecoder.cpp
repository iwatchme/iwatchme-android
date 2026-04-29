#include "AudioDecoder.h"
#include "common/log.h"

AudioDecoder::~AudioDecoder() {
    release();
}

bool AudioDecoder::init(AVCodecParameters* params, AVRational timeBase, int outputSampleRate, int outputChannels) {
    release();
    timeBase_ = timeBase;
    outputSampleRate_ = outputSampleRate;
    outputChannels_ = outputChannels;

    const AVCodec* codec = avcodec_find_decoder(params->codec_id);
    if (!codec) {
        LOGE("AudioDecoder: 找不到解码器 codec_id=%d", params->codec_id);
        return false;
    }

    codecCtx_ = avcodec_alloc_context3(codec);
    if (!codecCtx_) return false;

    if (avcodec_parameters_to_context(codecCtx_, params) < 0) {
        release();
        return false;
    }

    if (avcodec_open2(codecCtx_, codec, nullptr) < 0) {
        LOGE("AudioDecoder: avcodec_open2 failed");
        release();
        return false;
    }

    // 初始化重采样器：源格式 → 设备实际输出格式（s16, outputChannels, outputSampleRate）
    AVChannelLayout outLayout;
    av_channel_layout_default(&outLayout, outputChannels_);
    int ret = swr_alloc_set_opts2(
        &swrCtx_,
        &outLayout,                      // 输出：立体声
        AV_SAMPLE_FMT_S16,              // 输出：16-bit signed
        outputSampleRate_,              // 输出：设备实际采样率
        &codecCtx_->ch_layout,           // 输入：源声道布局
        codecCtx_->sample_fmt,           // 输入：源采样格式
        codecCtx_->sample_rate,          // 输入：源采样率
        0, nullptr
    );
    av_channel_layout_uninit(&outLayout);
    if (ret < 0 || !swrCtx_) {
        LOGE("AudioDecoder: swr_alloc_set_opts2 failed");
        release();
        return false;
    }

    if (swr_init(swrCtx_) < 0) {
        LOGE("AudioDecoder: swr_init failed");
        release();
        return false;
    }

    frame_ = av_frame_alloc();

    LOGI("AudioDecoder: initialized %s, %dHz %dch -> %dHz %dch s16",
         codec->name, codecCtx_->sample_rate,
         codecCtx_->ch_layout.nb_channels,
         outputSampleRate_, outputChannels_);
    return true;
}

void AudioDecoder::release() {
    if (frame_) { av_frame_free(&frame_); frame_ = nullptr; }
    if (swrCtx_) { swr_free(&swrCtx_); swrCtx_ = nullptr; }
    if (codecCtx_) { avcodec_free_context(&codecCtx_); codecCtx_ = nullptr; }
}

AudioDecoder::DecodeResult AudioDecoder::decode(AVPacket* packet, std::vector<uint8_t>& outBuffer) {
    DecodeResult result;
    if (!codecCtx_) return result;

    int ret = avcodec_send_packet(codecCtx_, packet);
    if (ret < 0 && ret != AVERROR(EAGAIN)) return result;

    while (true) {
        ret = avcodec_receive_frame(codecCtx_, frame_);
        if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) break;
        if (ret < 0) break;

        int64_t framePts = frame_->best_effort_timestamp;
        if (framePts == AV_NOPTS_VALUE) framePts = frame_->pts;
        int64_t framePtsUs = -1;
        if (framePts != AV_NOPTS_VALUE) {
            framePtsUs = av_rescale_q(framePts, timeBase_, {1, 1000000});
        }

        // 计算重采样后的输出帧数
        int outSamples = swr_get_out_samples(swrCtx_, frame_->nb_samples);
        if (outSamples <= 0) continue;

        // 分配输出缓冲区
        size_t prevSize = outBuffer.size();
        outBuffer.resize(prevSize + outSamples * bytesPerFrame());
        uint8_t* outPtr = outBuffer.data() + prevSize;

        // 重采样
        int converted = swr_convert(
            swrCtx_,
            &outPtr, outSamples,
            (const uint8_t**)frame_->extended_data, frame_->nb_samples
        );
        if (converted > 0) {
            // 修正实际大小
            outBuffer.resize(prevSize + converted * bytesPerFrame());
            if (!result.hasValidPts && result.frames == 0 && framePtsUs >= 0) {
                result.startPtsUs = framePtsUs;
                result.hasValidPts = true;
            }
            result.frames += converted;
        } else {
            outBuffer.resize(prevSize);
        }
    }

    return result;
}

void AudioDecoder::flush() {
    if (codecCtx_) {
        avcodec_flush_buffers(codecCtx_);
    }
    if (swrCtx_) {
        // 清空 swresample 内部缓冲
        int64_t delay = swr_get_delay(swrCtx_, outputSampleRate_);
        if (delay > 0) {
            // 丢弃残留数据
            uint8_t dummy[4096];
            uint8_t* dummyPtr = dummy;
            swr_convert(swrCtx_, &dummyPtr, (int)(sizeof(dummy) / bytesPerFrame()),
                       nullptr, 0);
        }
    }
}
