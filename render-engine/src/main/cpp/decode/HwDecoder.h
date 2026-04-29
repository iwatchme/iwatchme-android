#pragma once

#include <cstdint>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <android/native_window.h>

extern "C" {
#include <libavcodec/avcodec.h>
}

class HwDecoder {
public:
    HwDecoder() = default;
    ~HwDecoder();

    // Initialize with codec params and output surface (Surface mode)
    bool init(AVCodecParameters* params, ANativeWindow* outputSurface);
    void release();

    // Feed a compressed packet. Returns true if accepted.
    bool queuePacket(AVPacket* packet);

    // Signal end of stream
    void queueEOS();

    // Dequeue an output buffer. In Surface mode, releaseOutputBuffer is called internally.
    // Returns the PTS in stream timebase, or -1 if no frame available.
    // When render=true, the frame is sent to the output Surface.
    int64_t dequeueAndRender(int64_t timeoutUs = 10000);

    // Flush decoder (for seek)
    void flush();

    bool isInitialized() const { return codec_ != nullptr; }

private:
    bool configureCsd(AVCodecParameters* params, AMediaFormat* format);

    AMediaCodec* codec_ = nullptr;
    int queuedCount_ = 0;
    int dequeuedCount_ = 0;
};
