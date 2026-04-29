#include "AudioOutput.h"
#include "common/log.h"
#include <time.h>

namespace {
int64_t monotonicNowUs() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000LL + ts.tv_nsec / 1000LL;
}
}

AudioOutput::~AudioOutput() {
    close();
}

bool AudioOutput::open(int sampleRate, int channels) {
    close();

    AAudioStreamBuilder* builder = nullptr;
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    if (result != AAUDIO_OK) {
        LOGE("AudioOutput: AAudio_createStreamBuilder failed: %d", result);
        return false;
    }

    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
    AAudioStreamBuilder_setSampleRate(builder, sampleRate);
    AAudioStreamBuilder_setChannelCount(builder, channels);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);

    result = AAudioStreamBuilder_openStream(builder, &stream_);
    AAudioStreamBuilder_delete(builder);

    if (result != AAUDIO_OK) {
        LOGE("AudioOutput: openStream failed: %d", result);
        stream_ = nullptr;
        return false;
    }

    result = AAudioStream_requestStart(stream_);
    if (result != AAUDIO_OK) {
        LOGE("AudioOutput: requestStart failed: %d", result);
        close();
        return false;
    }

    sampleRate_ = AAudioStream_getSampleRate(stream_);
    channels_ = AAudioStream_getChannelCount(stream_);

    clockValid_.store(false);

    LOGI("AudioOutput: opened %dHz %dch, bufferSize=%d",
         AAudioStream_getSampleRate(stream_),
         AAudioStream_getChannelCount(stream_),
         AAudioStream_getBufferSizeInFrames(stream_));
    return true;
}

void AudioOutput::close() {
    if (stream_) {
        AAudioStream_requestStop(stream_);
        AAudioStream_close(stream_);
        stream_ = nullptr;
    }
    clockValid_.store(false);
}

int AudioOutput::write(const void* data, int numFrames, int64_t ptsUs) {
    if (!stream_) return 0;

    const uint8_t* ptr = (const uint8_t*)data;
    int bytesPerFrame = channels_ * 2;  // int16_t
    int framesWritten = 0;
    int64_t anchorFramesRead = 0;
    bool establishAnchor = false;

    // flush/open 后第一次拿到有效 PTS 时建立锚点。
    // 之后时钟只跟随 framesRead 推进，不再每次 write 都重设基准，
    // 避免把“写入进度”误当成“真实播放进度”。
    if (ptsUs >= 0 && !clockValid_.load()) {
        anchorFramesRead = AAudioStream_getFramesRead(stream_);
        establishAnchor = true;
    }

    while (framesWritten < numFrames) {
        int remaining = numFrames - framesWritten;
        aaudio_result_t result = AAudioStream_write(
            stream_,
            ptr + framesWritten * bytesPerFrame,
            remaining,
            100000000  // 100ms 超时
        );

        if (result < 0) {
            LOGE("AudioOutput: write error: %d", result);
            break;
        }
        framesWritten += result;
    }

    if (framesWritten > 0 && ptsUs >= 0 && establishAnchor) {
        basePtsUs_.store(ptsUs);
        baseFramesRead_.store(anchorFramesRead);
        clockValid_.store(true);
        LOGI("AVSYNC audio-anchor ptsUs=%lld baseFramesRead=%lld sampleRate=%d channels=%d",
             (long long)ptsUs,
             (long long)anchorFramesRead,
             sampleRate_,
             channels_);
    }

    return framesWritten;
}

int64_t AudioOutput::getPlaybackPositionUs() const {
    if (!clockValid_.load()) return -1;
    // AAudioStream_getFramesRead() 反映的是设备已经真实消费的帧数。
    // 这是当前 RenderEngine 的音频主时钟来源。
    int64_t currentFramesRead = AAudioStream_getFramesRead(stream_);
    int64_t deltaFrames = currentFramesRead - baseFramesRead_.load();
    if (deltaFrames < 0) deltaFrames = 0;
    return basePtsUs_.load() + deltaFrames * 1000000LL / sampleRate_;
}

void AudioOutput::pause() {
    if (stream_) {
        AAudioStream_requestPause(stream_);
        LOGI("AVSYNC audio-pause clockUs=%lld framesRead=%lld",
             (long long)getPlaybackPositionUs(),
             (long long)AAudioStream_getFramesRead(stream_));
    }
}

void AudioOutput::resume() {
    if (stream_) {
        AAudioStream_requestStart(stream_);
        LOGI("AVSYNC audio-resume clockUs=%lld framesRead=%lld nowUs=%lld",
             (long long)getPlaybackPositionUs(),
             (long long)AAudioStream_getFramesRead(stream_),
             (long long)monotonicNowUs());
    }
}

void AudioOutput::flush() {
    if (stream_) {
        LOGI("AVSYNC audio-flush before clockUs=%lld framesRead=%lld",
             (long long)getPlaybackPositionUs(),
             (long long)AAudioStream_getFramesRead(stream_));
        AAudioStream_requestPause(stream_);
        AAudioStream_requestFlush(stream_);
        AAudioStream_requestStart(stream_);
    }
    clockValid_.store(false);
}
