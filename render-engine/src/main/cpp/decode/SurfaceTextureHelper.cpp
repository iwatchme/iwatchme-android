#include "SurfaceTextureHelper.h"
#include "common/log.h"
#include <android/native_window_jni.h>

// ---------- OnFrameAvailableListener 的 JNI 回调 ----------

// 静态指针：将 SurfaceTextureHelper* 存储在全局变量中，供 JNI 回调查找。
// 每个 RenderEngine 只有一个 SurfaceTextureHelper，且同时只有一个引擎实例，所以用单个全局变量即可。
static SurfaceTextureHelper* g_activeHelper = nullptr;

extern "C" JNIEXPORT void JNICALL
Java_com_iwatchme_renderengine_FrameAvailableListener_nativeOnFrameAvailable(
        JNIEnv* /* env */, jobject /* thiz */, jlong nativePtr) {
    auto* helper = reinterpret_cast<SurfaceTextureHelper*>(nativePtr);
    if (helper) {
        helper->onFrameAvailable();
    }
}

// ---------- SurfaceTextureHelper 实现 ----------

// 静态成员：在 JNI_OnLoad 中通过主线程 ClassLoader 缓存的类引用
jclass SurfaceTextureHelper::sCachedListenerClass_ = nullptr;

void SurfaceTextureHelper::cacheJavaClasses(JNIEnv* env) {
    jclass localClass = env->FindClass("com/iwatchme/renderengine/FrameAvailableListener");
    if (localClass) {
        sCachedListenerClass_ = (jclass)env->NewGlobalRef(localClass);
        env->DeleteLocalRef(localClass);
        LOGI("SurfaceTextureHelper: FrameAvailableListener 类已缓存");
    } else {
        LOGW("SurfaceTextureHelper: 无法在 JNI_OnLoad 中找到 FrameAvailableListener 类");
    }
}

void SurfaceTextureHelper::releaseJavaClasses(JNIEnv* env) {
    if (sCachedListenerClass_) {
        env->DeleteGlobalRef(sCachedListenerClass_);
        sCachedListenerClass_ = nullptr;
    }
}

SurfaceTextureHelper::~SurfaceTextureHelper() {
    // release() 必须通过传入 JNIEnv 显式调用
}

bool SurfaceTextureHelper::create(JNIEnv* env) {
    // 1. 创建 OES 纹理
    glGenTextures(1, &oesTexId_);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, oesTexId_);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);

    LOGI("SurfaceTextureHelper: OES texture created, id=%u", oesTexId_);

    // 2. 通过 JNI 创建 Java SurfaceTexture(texId)
    jclass stClass = env->FindClass("android/graphics/SurfaceTexture");
    if (!stClass) {
        LOGE("SurfaceTextureHelper: cannot find SurfaceTexture class");
        return false;
    }

    jmethodID stCtor = env->GetMethodID(stClass, "<init>", "(I)V");
    jobject localST = env->NewObject(stClass, stCtor, (jint)oesTexId_);
    surfaceTexture_ = env->NewGlobalRef(localST);
    env->DeleteLocalRef(localST);

    // 缓存常用方法 ID
    updateTexImageMethod_ = env->GetMethodID(stClass, "updateTexImage", "()V");
    getTransformMatrixMethod_ = env->GetMethodID(stClass, "getTransformMatrix", "([F)V");

    env->DeleteLocalRef(stClass);

    // 3. 设置 OnFrameAvailableListener 回调
    setupOnFrameAvailableListener(env);

    // 4. 通过 JNI 创建 Java Surface(SurfaceTexture)
    jclass surfaceClass = env->FindClass("android/view/Surface");
    jmethodID surfaceCtor = env->GetMethodID(surfaceClass, "<init>",
                                              "(Landroid/graphics/SurfaceTexture;)V");
    jobject localSurface = env->NewObject(surfaceClass, surfaceCtor, surfaceTexture_);
    surface_ = env->NewGlobalRef(localSurface);
    env->DeleteLocalRef(localSurface);
    env->DeleteLocalRef(surfaceClass);

    // 5. 从 Surface 获取 ANativeWindow（给 MediaCodec 作为输出目标）
    window_ = ANativeWindow_fromSurface(env, surface_);

    g_activeHelper = this;

    LOGI("SurfaceTextureHelper: created successfully, oesTexId=%u, window=%p",
         oesTexId_, window_);
    return true;
}

bool SurfaceTextureHelper::setupOnFrameAvailableListener(JNIEnv* env) {
    // 使用在 JNI_OnLoad 中缓存的类引用（渲染线程的 ClassLoader 无法找到应用类）
    if (!sCachedListenerClass_) {
        LOGW("SurfaceTextureHelper: FrameAvailableListener 类未缓存，跳过 listener 设置");
        return false;
    }

    // 创建 listener 实例，传入 native 指针
    jmethodID ctor = env->GetMethodID(sCachedListenerClass_, "<init>", "(J)V");
    jobject localListener = env->NewObject(sCachedListenerClass_, ctor, reinterpret_cast<jlong>(this));
    listener_ = env->NewGlobalRef(localListener);
    env->DeleteLocalRef(localListener);

    // 调用 surfaceTexture.setOnFrameAvailableListener(listener)
    jclass stClass = env->GetObjectClass(surfaceTexture_);
    jmethodID setListenerMethod = env->GetMethodID(stClass, "setOnFrameAvailableListener",
            "(Landroid/graphics/SurfaceTexture$OnFrameAvailableListener;)V");
    env->CallVoidMethod(surfaceTexture_, setListenerMethod, listener_);
    env->DeleteLocalRef(stClass);

    LOGI("SurfaceTextureHelper: OnFrameAvailableListener connected");
    return true;
}

void SurfaceTextureHelper::release(JNIEnv* env) {
    g_activeHelper = nullptr;

    if (window_) {
        ANativeWindow_release(window_);
        window_ = nullptr;
    }

    if (surface_) {
        jclass surfaceClass = env->GetObjectClass(surface_);
        jmethodID releaseMethod = env->GetMethodID(surfaceClass, "release", "()V");
        env->CallVoidMethod(surface_, releaseMethod);
        env->DeleteLocalRef(surfaceClass);
        env->DeleteGlobalRef(surface_);
        surface_ = nullptr;
    }

    if (surfaceTexture_) {
        // 释放前先清除 listener
        jclass stClass = env->GetObjectClass(surfaceTexture_);
        jmethodID setListenerMethod = env->GetMethodID(stClass, "setOnFrameAvailableListener",
                "(Landroid/graphics/SurfaceTexture$OnFrameAvailableListener;)V");
        env->CallVoidMethod(surfaceTexture_, setListenerMethod, nullptr);

        jmethodID releaseMethod = env->GetMethodID(stClass, "release", "()V");
        env->CallVoidMethod(surfaceTexture_, releaseMethod);
        env->DeleteLocalRef(stClass);
        env->DeleteGlobalRef(surfaceTexture_);
        surfaceTexture_ = nullptr;
    }

    if (listener_) {
        env->DeleteGlobalRef(listener_);
        listener_ = nullptr;
    }

    if (oesTexId_) {
        glDeleteTextures(1, &oesTexId_);
        oesTexId_ = 0;
    }

    // 唤醒所有等待中的线程
    frameCond_.notify_all();
}

void SurfaceTextureHelper::updateTexImage(JNIEnv* env) {
    if (surfaceTexture_) {
        env->CallVoidMethod(surfaceTexture_, updateTexImageMethod_);
    }
}

void SurfaceTextureHelper::getTransformMatrix(JNIEnv* env, float* matrix4x4) {
    if (!surfaceTexture_) return;

    jfloatArray jMatrix = env->NewFloatArray(16);
    env->CallVoidMethod(surfaceTexture_, getTransformMatrixMethod_, jMatrix);
    env->GetFloatArrayRegion(jMatrix, 0, 16, matrix4x4);
    env->DeleteLocalRef(jMatrix);
}

void SurfaceTextureHelper::onFrameAvailable() {
    frameAvailable_.store(true);
    frameCond_.notify_one();
}

void SurfaceTextureHelper::waitForFrame(int64_t timeoutMs) {
    std::unique_lock<std::mutex> lock(frameMutex_);
    if (!frameAvailable_.load()) {
        frameCond_.wait_for(lock, std::chrono::milliseconds(timeoutMs));
    }
}
