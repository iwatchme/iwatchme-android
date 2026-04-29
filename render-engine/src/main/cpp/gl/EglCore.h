#pragma once

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <android/native_window.h>

class EglCore {
public:
    EglCore() = default;
    ~EglCore();

    bool init();
    void release();

    // Surface management
    EGLSurface createWindowSurface(ANativeWindow* window);
    EGLSurface createOffscreenSurface(int width, int height);
    void destroySurface(EGLSurface surface);

    // Context management
    bool makeCurrent(EGLSurface surface);
    void makeNothingCurrent();

    // Buffer swap
    bool swapBuffers(EGLSurface surface);

    // Presentation time for encoder (export use case)
    void setPresentationTime(EGLSurface surface, int64_t nsecs);

    bool isInitialized() const { return display_ != EGL_NO_DISPLAY; }

private:
    bool chooseConfig();

    EGLDisplay display_ = EGL_NO_DISPLAY;
    EGLContext context_ = EGL_NO_CONTEXT;
    EGLConfig  config_  = nullptr;

    using EglPresentationTimeProc = void (*)(EGLDisplay, EGLSurface, EGLnsecsANDROID);
    EglPresentationTimeProc eglPresentationTimeANDROID_ = nullptr;
};
