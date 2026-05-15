#pragma once

#include <jni.h>
#include <memory>
#include <string>
#include <vector>
#include <android/native_window.h>
#include "engine/Timeline.h"

class PlaybackSession;

// 播放事件回调接口
class PlaybackCallback {
public:
    virtual ~PlaybackCallback() = default;
    virtual void onPlaybackCompleted() = 0;
};

class RenderEngine {
public:
    explicit RenderEngine(JavaVM* jvm);
    ~RenderEngine();

    void setSurface(ANativeWindow* window);
    bool setVideoSource(const std::string& filePath);
    bool setTimeline(std::vector<Clip> clips);
    bool setMultiTrackTimeline(std::vector<Clip> primaryClips,
                               std::vector<Clip> overlayClips,
                               float overlayAlpha);

    void play();
    void pause();
    void seek(int64_t positionUs);       // 精确 seek：解码到目标帧（松手时调用）
    void seekFast(int64_t positionUs);   // 快速 seek：只显示最近关键帧（拖动中调用）

    int64_t getDuration() const;
    int64_t getPosition() const;
    int getVideoWidth() const;
    int getVideoHeight() const;

    void setOverlayAlpha(float alpha);
    void setSubtitle(const std::string& srtPath,
                     const std::string& fontPath,
                     int fontSizePx);
    void setSubtitleEnabled(bool enabled);
    void setCallback(PlaybackCallback* cb) { callback_ = cb; }

private:
    std::unique_ptr<PlaybackSession> session_;
    PlaybackCallback* callback_ = nullptr;
};
