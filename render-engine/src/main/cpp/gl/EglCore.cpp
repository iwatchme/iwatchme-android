#include "EglCore.h"
#include "common/log.h"

EglCore::~EglCore() {
    release();
}

bool EglCore::init() {
    display_ = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display_ == EGL_NO_DISPLAY) {
        LOGE("EglCore: eglGetDisplay failed");
        return false;
    }

    EGLint major, minor;
    if (!eglInitialize(display_, &major, &minor)) {
        LOGE("EglCore: eglInitialize failed");
        display_ = EGL_NO_DISPLAY;
        return false;
    }
    LOGI("EglCore: EGL %d.%d initialized", major, minor);

    if (!chooseConfig()) {
        release();
        return false;
    }

    EGLint contextAttribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 3,  // OpenGL ES 3.0
        EGL_NONE
    };
    context_ = eglCreateContext(display_, config_, EGL_NO_CONTEXT, contextAttribs);
    if (context_ == EGL_NO_CONTEXT) {
        LOGE("EglCore: eglCreateContext failed: 0x%x", eglGetError());
        release();
        return false;
    }

    // Get the presentation time extension (optional, used for export)
    eglPresentationTimeANDROID_ = reinterpret_cast<EglPresentationTimeProc>(
        eglGetProcAddress("eglPresentationTimeANDROID"));

    LOGI("EglCore: initialized successfully");
    return true;
}

bool EglCore::chooseConfig() {
    EGLint configAttribs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
        EGL_SURFACE_TYPE,    EGL_WINDOW_BIT | EGL_PBUFFER_BIT,
        EGL_RED_SIZE,        8,
        EGL_GREEN_SIZE,      8,
        EGL_BLUE_SIZE,       8,
        EGL_ALPHA_SIZE,      8,
        EGL_NONE
    };

    EGLint numConfigs;
    if (!eglChooseConfig(display_, configAttribs, &config_, 1, &numConfigs) || numConfigs == 0) {
        LOGE("EglCore: eglChooseConfig failed");
        return false;
    }
    return true;
}

void EglCore::release() {
    if (display_ != EGL_NO_DISPLAY) {
        eglMakeCurrent(display_, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (context_ != EGL_NO_CONTEXT) {
            eglDestroyContext(display_, context_);
            context_ = EGL_NO_CONTEXT;
        }
        eglTerminate(display_);
        display_ = EGL_NO_DISPLAY;
    }
    config_ = nullptr;
    eglPresentationTimeANDROID_ = nullptr;
}

EGLSurface EglCore::createWindowSurface(ANativeWindow* window) {
    EGLint surfaceAttribs[] = { EGL_NONE };
    EGLSurface surface = eglCreateWindowSurface(display_, config_, window, surfaceAttribs);
    if (surface == EGL_NO_SURFACE) {
        LOGE("EglCore: eglCreateWindowSurface failed: 0x%x", eglGetError());
    }
    return surface;
}

EGLSurface EglCore::createOffscreenSurface(int width, int height) {
    EGLint surfaceAttribs[] = {
        EGL_WIDTH,  width,
        EGL_HEIGHT, height,
        EGL_NONE
    };
    EGLSurface surface = eglCreatePbufferSurface(display_, config_, surfaceAttribs);
    if (surface == EGL_NO_SURFACE) {
        LOGE("EglCore: eglCreatePbufferSurface failed: 0x%x", eglGetError());
    }
    return surface;
}

void EglCore::destroySurface(EGLSurface surface) {
    if (surface != EGL_NO_SURFACE) {
        eglDestroySurface(display_, surface);
    }
}

bool EglCore::makeCurrent(EGLSurface surface) {
    if (!eglMakeCurrent(display_, surface, surface, context_)) {
        LOGE("EglCore: eglMakeCurrent failed: 0x%x", eglGetError());
        return false;
    }
    return true;
}

void EglCore::makeNothingCurrent() {
    eglMakeCurrent(display_, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
}

bool EglCore::swapBuffers(EGLSurface surface) {
    return eglSwapBuffers(display_, surface);
}

void EglCore::setPresentationTime(EGLSurface surface, int64_t nsecs) {
    if (eglPresentationTimeANDROID_) {
        eglPresentationTimeANDROID_(display_, surface, nsecs);
    }
}
