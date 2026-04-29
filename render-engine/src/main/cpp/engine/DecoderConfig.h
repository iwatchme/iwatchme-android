#pragma once

#include <cstdint>
#include <vector>

extern "C" {
#include <libavcodec/avcodec.h>
}

// 解码配置指纹，用于判断 HwDecoder 是否可以 flush 复用。
// 只有 codecId + width + height + extradata 完全一致时才能 flush，
// 否则必须 release + re-init MediaCodec。
struct DecoderConfig {
    AVCodecID codecId = AV_CODEC_ID_NONE;
    int width = 0;
    int height = 0;
    std::vector<uint8_t> extradata;

    bool operator==(const DecoderConfig& other) const;
    bool operator!=(const DecoderConfig& other) const;

    static DecoderConfig fromCodecParameters(const AVCodecParameters* params);
};
