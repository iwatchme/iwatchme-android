#pragma once

#include <jni.h>
#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>
#include <android/native_window.h>
#include <atomic>
#include <mutex>
#include <condition_variable>

class SurfaceTextureHelper {
public:
    SurfaceTextureHelper() = default;
    ~SurfaceTextureHelper();

    // 创建 OES 纹理 + Java SurfaceTexture + Surface + ANativeWindow。
    // 必须在 GL 线程上调用（需要活跃的 EGL 上下文）。
    bool create(JNIEnv* env);
    void release(JNIEnv* env);

    // 调用 SurfaceTexture.updateTexImage() —— 将最新帧锁存到 OES 纹理。
    // 必须在 GL 线程上调用。
    void updateTexImage(JNIEnv* env);

    // 从 SurfaceTexture 获取 4x4 纹理坐标变换矩阵。
    void getTransformMatrix(JNIEnv* env, float* matrix4x4);

    GLuint oesTexId() const { return oesTexId_; }
    ANativeWindow* nativeWindow() const { return window_; }

    // 帧就绪信号（由 OnFrameAvailableListener 的 JNI 回调设置）
    void onFrameAvailable();
    bool consumeFrameAvailable() { return frameAvailable_.exchange(false); }
    void waitForFrame(int64_t timeoutMs);

    // 必须在 JNI_OnLoad（主线程）中调用，缓存应用类引用
    static void cacheJavaClasses(JNIEnv* env);
    static void releaseJavaClasses(JNIEnv* env);

private:
    bool setupOnFrameAvailableListener(JNIEnv* env);

    // 在 JNI_OnLoad 中缓存的类引用（主线程 ClassLoader 才能找到应用类）
    static jclass sCachedListenerClass_;

    GLuint oesTexId_ = 0;
    jobject surfaceTexture_ = nullptr;   // 全局引用
    jobject surface_ = nullptr;          // 全局引用
    jobject listener_ = nullptr;         // OnFrameAvailableListener 的全局引用
    ANativeWindow* window_ = nullptr;

    jmethodID updateTexImageMethod_ = nullptr;
    jmethodID getTransformMatrixMethod_ = nullptr;

    std::atomic<bool> frameAvailable_{false};
    std::mutex frameMutex_;
    std::condition_variable frameCond_;
};
