#include "DecoderConfig.h"

bool DecoderConfig::operator==(const DecoderConfig& other) const {
    return codecId == other.codecId
        && width == other.width
        && height == other.height
        && extradata == other.extradata;
}

bool DecoderConfig::operator!=(const DecoderConfig& other) const {
    return !(*this == other);
}

DecoderConfig DecoderConfig::fromCodecParameters(const AVCodecParameters* params) {
    DecoderConfig cfg;
    if (!params) return cfg;
    cfg.codecId = params->codec_id;
    cfg.width = params->width;
    cfg.height = params->height;
    if (params->extradata && params->extradata_size > 0) {
        cfg.extradata.assign(params->extradata,
                             params->extradata + params->extradata_size);
    }
    return cfg;
}
