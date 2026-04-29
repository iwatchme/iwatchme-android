#pragma once

#include <cstdint>
#include <string>

extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libavcodec/bsf.h>
}

class Demuxer {
public:
    Demuxer() = default;
    ~Demuxer();

    bool open(const char* filePath);
    void close();

    // Read next video packet (Annex B format for H.264/HEVC). Returns false at EOF or error.
    bool readVideoPacket(AVPacket* packet);

    // Seek to position (microseconds)
    bool seek(int64_t positionUs);

    // Media info
    int videoWidth() const;
    int videoHeight() const;
    int64_t durationUs() const;
    double fps() const;
    AVRational timeBase() const;

    // Returns codec parameters — Annex B extradata if bsf is active
    AVCodecParameters* codecParameters() const;

    bool isOpen() const { return formatCtx_ != nullptr; }

private:
    bool initBitstreamFilter();

    AVFormatContext* formatCtx_ = nullptr;
    AVBSFContext* bsfCtx_ = nullptr;
    int videoStreamIndex_ = -1;
};
