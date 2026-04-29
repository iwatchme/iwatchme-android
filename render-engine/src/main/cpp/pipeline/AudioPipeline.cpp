#include "pipeline/AudioPipeline.h"
#include "pipeline/VideoTrackPipeline.h"
#include "common/log.h"
#include <chrono>

namespace {
constexpr int64_t kAvSyncDiagIntervalUs = 500000;
int64_t steadyNowUs() {
    return std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}
}

AudioPipeline::~AudioPipeline() {
    release();
}

void AudioPipeline::bindContext(VideoTrackPipeline* track,
                                std::atomic<bool>* engineRunning,
                                std::atomic<bool>* playing,
                                std::atomic<bool>* transitioning,
                                std::atomic<uint32_t>* generation,
                                std::atomic<int64_t>* currentPositionUs) {
    track_ = track;
    engineRunning_ = engineRunning;
    playing_ = playing;
    transitioning_ = transitioning;
    generation_ = generation;
    currentPositionUs_ = currentPositionUs;
}

bool AudioPipeline::configureFromTrack(VideoTrackPipeline* track) {
    track_ = track;
    hasAudio_ = false;
    decoder_.release();

    if (output_.isOpen()) {
        output_.flush();
    }

    if (!track_ || !track_->hasAudio()) {
        return false;
    }

    if (!output_.isOpen()) {
        int requestedSampleRate = track_->audioSampleRate() > 0 ? track_->audioSampleRate() : 48000;
        if (!output_.open(requestedSampleRate, 2)) {
            LOGW("AudioPipeline: AudioOutput open failed");
            return false;
        }
    }

    if (!decoder_.init(track_->audioCodecParameters(),
                       track_->audioTimeBase(),
                       output_.sampleRate(),
                       output_.channels())) {
        LOGW("AudioPipeline: AudioDecoder init failed");
        return false;
    }

    hasAudio_ = true;
    return true;
}

void AudioPipeline::start() {
    std::lock_guard<std::mutex> lock(threadMutex_);
    if (!hasAudio_ || running_.load()) return;
    running_.store(true);
    thread_ = std::thread(&AudioPipeline::threadFunc, this);
}

void AudioPipeline::stopAndJoin() {
    std::thread t;
    {
        std::lock_guard<std::mutex> lock(threadMutex_);
        running_.store(false);
        t = std::move(thread_);  // 取走线程，后续调用者看到的 thread_ 不再 joinable
    }
    if (t.joinable()) t.join();
}

void AudioPipeline::release() {
    stopAndJoin();
    decoder_.release();
    output_.close();
    hasAudio_ = false;
}

void AudioPipeline::pause() {
    if (hasAudio_) output_.pause();
}

void AudioPipeline::resume() {
    if (hasAudio_) output_.resume();
}

void AudioPipeline::flush() {
    decoder_.flush();
    output_.flush();
}

void AudioPipeline::notifySeek(int64_t targetUs) {
    seekTargetUs_.store(targetUs);
}

int64_t AudioPipeline::getAudioClockUs() const {
    if (hasAudio_ && output_.isOpen()) {
        return output_.getPlaybackPositionUs();
    }
    return -1;
}

void AudioPipeline::threadFunc() {
    LOGI("AudioPipeline: audio thread started");

    AVPacket* pkt = av_packet_alloc();
    std::vector<uint8_t> pcmBuffer;
    int64_t lastAudioDiagLogUs = 0;
    int audioWriteCount = 0;

    while (running_.load() && engineRunning_ && engineRunning_->load()) {
        int64_t audioSeek = seekTargetUs_.exchange(-1);
        if (audioSeek >= 0) {
            decoder_.flush();
            output_.flush();
            av_packet_unref(pkt);
            LOGI("AVSYNC audio-thread seek-handled targetUs=%lld generation=%u",
                 (long long)audioSeek,
                 generation_ ? generation_->load() : 0);
        }

        if (transitioning_ && transitioning_->load()) {
            std::this_thread::sleep_for(std::chrono::milliseconds(5));
            continue;
        }

        if (!playing_ || !playing_->load()) {
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
            continue;
        }

        uint32_t generation = generation_ ? generation_->load() : 0;
        bool gotPacket = track_ && track_->popAudioPacket(pkt);
        if (!gotPacket) {
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
            continue;
        }

        if ((transitioning_ && transitioning_->load())
            || (generation_ && generation != generation_->load())) {
            av_packet_unref(pkt);
            continue;
        }

        pcmBuffer.clear();
        AudioDecoder::DecodeResult decodeResult = decoder_.decode(pkt, pcmBuffer);
        av_packet_unref(pkt);

        if ((transitioning_ && transitioning_->load())
            || (generation_ && generation != generation_->load())) {
            continue;
        }

        if (decodeResult.frames > 0 && !pcmBuffer.empty()) {
            output_.write(
                pcmBuffer.data(),
                decodeResult.frames,
                decodeResult.hasValidPts ? decodeResult.startPtsUs : -1
            );
            audioWriteCount++;
            int64_t nowUs = steadyNowUs();
            if (nowUs - lastAudioDiagLogUs >= kAvSyncDiagIntervalUs) {
                lastAudioDiagLogUs = nowUs;
                LOGI("AVSYNC audio-diag count=%d gen=%u startPtsUs=%lld hasPts=%d frames=%d clockUs=%lld currentPosUs=%lld",
                     audioWriteCount,
                     generation,
                     (long long)decodeResult.startPtsUs,
                     decodeResult.hasValidPts ? 1 : 0,
                     decodeResult.frames,
                     (long long)output_.getPlaybackPositionUs(),
                     (long long)(currentPositionUs_ ? currentPositionUs_->load() : 0));
            }
        }
    }

    av_packet_free(&pkt);
    LOGI("AudioPipeline: audio thread stopped");
}
