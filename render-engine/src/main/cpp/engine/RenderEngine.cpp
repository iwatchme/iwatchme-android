#include "engine/RenderEngine.h"
#include "engine/PlaybackSession.h"

RenderEngine::RenderEngine(JavaVM* jvm)
    : session_(std::make_unique<PlaybackSession>(jvm)) {
    session_->setPlaybackCompletedHandler([this]() {
        if (callback_) {
            callback_->onPlaybackCompleted();
        }
    });
}

RenderEngine::~RenderEngine() = default;

void RenderEngine::setSurface(ANativeWindow* window) { session_->setSurface(window); }
bool RenderEngine::setVideoSource(const std::string& filePath) { return session_->setVideoSource(filePath); }
bool RenderEngine::setTimeline(std::vector<Clip> clips) { return session_->setTimeline(std::move(clips)); }
bool RenderEngine::setMultiTrackTimeline(std::vector<Clip> primaryClips,
                                         std::vector<Clip> overlayClips,
                                         float overlayAlpha) {
    return session_->setMultiTrackTimeline(std::move(primaryClips), std::move(overlayClips), overlayAlpha);
}
void RenderEngine::play() { session_->play(); }
void RenderEngine::pause() { session_->pause(); }
void RenderEngine::seek(int64_t positionUs) { session_->seek(positionUs); }
void RenderEngine::seekFast(int64_t positionUs) { session_->seekFast(positionUs); }
int64_t RenderEngine::getDuration() const { return session_->getDuration(); }
int64_t RenderEngine::getPosition() const { return session_->getPosition(); }
int RenderEngine::getVideoWidth() const { return session_->getVideoWidth(); }
int RenderEngine::getVideoHeight() const { return session_->getVideoHeight(); }
void RenderEngine::setOverlayAlpha(float alpha) { session_->setOverlayAlpha(alpha); }
void RenderEngine::setSubtitle(const std::string& srtPath,
                               const std::string& fontPath,
                               int fontSizePx) {
    session_->setSubtitle(srtPath, fontPath, fontSizePx);
}
void RenderEngine::setSubtitleEnabled(bool enabled) {
    session_->setSubtitleEnabled(enabled);
}
