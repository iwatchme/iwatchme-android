#include <jni.h>
#include <android/native_window_jni.h>
#include "common/log.h"
#include "engine/RenderEngine.h"
#include "engine/Timeline.h"
#include "decode/SurfaceTextureHelper.h"

static JavaVM* g_jvm = nullptr;

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
}

// JNI 回调桥接：从 C++ 渲染线程调用 Kotlin 层的 onPlaybackCompleted
class JniPlaybackCallback : public PlaybackCallback {
public:
    JniPlaybackCallback(JavaVM* jvm, jobject engineObj)
        : jvm_(jvm), engineObj_(engineObj) {}

    ~JniPlaybackCallback() override {
        JNIEnv* env = nullptr;
        if (jvm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) {
            env->DeleteGlobalRef(engineObj_);
        }
    }

    void onPlaybackCompleted() override {
        JNIEnv* env = nullptr;
        bool attached = false;
        int status = jvm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
        if (status == JNI_EDETACHED) {
            jvm_->AttachCurrentThread(&env, nullptr);
            attached = true;
        }
        if (env) {
            jclass clazz = env->GetObjectClass(engineObj_);
            jmethodID method = env->GetMethodID(clazz, "onNativePlaybackCompleted", "()V");
            if (method) {
                env->CallVoidMethod(engineObj_, method);
            }
            env->DeleteLocalRef(clazz);
        }
        if (attached) {
            jvm_->DetachCurrentThread();
        }
    }

private:
    JavaVM* jvm_;
    jobject engineObj_;  // 全局引用
};

// 将 engine 和 callback 打包在一起，方便通过 handle 统一管理生命周期
struct EngineEntry {
    RenderEngine* engine;
    JniPlaybackCallback* callback;
};

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* /* reserved */) {
    g_jvm = vm;
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        LOGE("JNI_OnLoad: GetEnv failed");
        return JNI_ERR;
    }

    // 在主线程中缓存应用类引用（渲染线程的 ClassLoader 无法找到应用类）
    SurfaceTextureHelper::cacheJavaClasses(env);

    LOGI("RenderEngine native library loaded");
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_iwatchme_renderengine_RenderEngine_nativeVersion(JNIEnv* env, jobject /* this */) {
    const char* ffmpegVersion = av_version_info();
    char version[256];
    snprintf(version, sizeof(version), "RenderEngine v0.1.0 | FFmpeg %s", ffmpegVersion);
    LOGI("%s", version);
    return env->NewStringUTF(version);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_iwatchme_renderengine_RenderEngine_nativeCreate(JNIEnv* env, jobject thiz) {
    auto* engine = new RenderEngine(g_jvm);

    // 设置播放完成回调
    jobject globalRef = env->NewGlobalRef(thiz);
    auto* callback = new JniPlaybackCallback(g_jvm, globalRef);
    engine->setCallback(callback);

    // 打包 engine + callback，统一通过 handle 管理
    auto* entry = new EngineEntry{engine, callback};
    return reinterpret_cast<jlong>(entry);
}

extern "C" JNIEXPORT void JNICALL
Java_com_iwatchme_renderengine_RenderEngine_nativeDestroy(JNIEnv* /* env */, jobject /* this */, jlong handle) {
    auto* entry = reinterpret_cast<EngineEntry*>(handle);
    entry->engine->setCallback(nullptr);
    delete entry->engine;
    delete entry->callback;
    delete entry;
}

static RenderEngine* getEngine(jlong handle) {
    auto* entry = reinterpret_cast<EngineEntry*>(handle);
    return entry->engine;
}

extern "C" JNIEXPORT void JNICALL
Java_com_iwatchme_renderengine_RenderEngine_nativeSetSurface(JNIEnv* env, jobject /* this */, jlong handle, jobject surface) {
    auto* engine = getEngine(handle);
    ANativeWindow* window = nullptr;
    if (surface != nullptr) {
        window = ANativeWindow_fromSurface(env, surface);
    }
    engine->setSurface(window);
    if (window) {
        ANativeWindow_release(window);  // setSurface 内部会自己 acquire 引用
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_iwatchme_renderengine_RenderEngine_nativeSetVideoSource(JNIEnv* env, jobject /* this */, jlong handle, jstring filePath) {
    auto* engine = getEngine(handle);
    const char* path = env->GetStringUTFChars(filePath, nullptr);
    bool result = engine->setVideoSource(path);
    env->ReleaseStringUTFChars(filePath, path);
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_iwatchme_renderengine_RenderEngine_nativeGetDuration(JNIEnv* /* env */, jobject /* this */, jlong handle) {
    return getEngine(handle)->getDuration();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_iwatchme_renderengine_RenderEngine_nativeGetVideoWidth(JNIEnv* /* env */, jobject /* this */, jlong handle) {
    return getEngine(handle)->getVideoWidth();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_iwatchme_renderengine_RenderEngine_nativeGetVideoHeight(JNIEnv* /* env */, jobject /* this */, jlong handle) {
    return getEngine(handle)->getVideoHeight();
}

extern "C" JNIEXPORT void JNICALL
Java_com_iwatchme_renderengine_RenderEngine_nativePlay(JNIEnv* /* env */, jobject /* this */, jlong handle) {
    getEngine(handle)->play();
}

extern "C" JNIEXPORT void JNICALL
Java_com_iwatchme_renderengine_RenderEngine_nativePause(JNIEnv* /* env */, jobject /* this */, jlong handle) {
    getEngine(handle)->pause();
}

extern "C" JNIEXPORT void JNICALL
Java_com_iwatchme_renderengine_RenderEngine_nativeSeek(JNIEnv* /* env */, jobject /* this */, jlong handle, jlong positionUs) {
    getEngine(handle)->seek(positionUs);
}

extern "C" JNIEXPORT void JNICALL
Java_com_iwatchme_renderengine_RenderEngine_nativeSeekFast(JNIEnv* /* env */, jobject /* this */, jlong handle, jlong positionUs) {
    getEngine(handle)->seekFast(positionUs);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_iwatchme_renderengine_RenderEngine_nativeSetTimeline(
    JNIEnv* env, jobject /* this */, jlong handle,
    jobjectArray paths, jlongArray trimIns, jlongArray trimOuts)
{
    auto* engine = getEngine(handle);

    int count = env->GetArrayLength(paths);
    jlong* trimInArr = env->GetLongArrayElements(trimIns, nullptr);
    jlong* trimOutArr = env->GetLongArrayElements(trimOuts, nullptr);

    std::vector<Clip> clips;
    clips.reserve(count);
    for (int i = 0; i < count; i++) {
        auto jpath = (jstring)env->GetObjectArrayElement(paths, i);
        const char* path = env->GetStringUTFChars(jpath, nullptr);

        Clip clip;
        clip.sourcePath = path;
        clip.trimIn = trimInArr[i];
        clip.trimOut = trimOutArr[i];
        clips.push_back(std::move(clip));

        env->ReleaseStringUTFChars(jpath, path);
        env->DeleteLocalRef(jpath);
    }

    env->ReleaseLongArrayElements(trimIns, trimInArr, 0);
    env->ReleaseLongArrayElements(trimOuts, trimOutArr, 0);

    return engine->setTimeline(std::move(clips)) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_iwatchme_renderengine_RenderEngine_nativeSetMultiTrackTimeline(
    JNIEnv* env, jobject /* this */, jlong handle,
    jint trackCount, jintArray clipCounts,
    jobjectArray allPaths, jlongArray allTrimIns, jlongArray allTrimOuts,
    jfloat overlayAlpha)
{
    auto* engine = getEngine(handle);

    jint* counts = env->GetIntArrayElements(clipCounts, nullptr);
    jlong* trimInArr = env->GetLongArrayElements(allTrimIns, nullptr);
    jlong* trimOutArr = env->GetLongArrayElements(allTrimOuts, nullptr);

    int totalClips = env->GetArrayLength(allPaths);
    int offset = 0;

    std::vector<Clip> primaryClips;
    std::vector<Clip> overlayClips;

    for (int t = 0; t < trackCount; t++) {
        int count = counts[t];
        std::vector<Clip>& target = (t == 0) ? primaryClips : overlayClips;
        target.reserve(count);

        for (int i = 0; i < count; i++) {
            int idx = offset + i;
            auto jpath = (jstring)env->GetObjectArrayElement(allPaths, idx);
            const char* path = env->GetStringUTFChars(jpath, nullptr);

            Clip clip;
            clip.sourcePath = path;
            clip.trimIn = trimInArr[idx];
            clip.trimOut = trimOutArr[idx];
            target.push_back(std::move(clip));

            env->ReleaseStringUTFChars(jpath, path);
            env->DeleteLocalRef(jpath);
        }
        offset += count;
    }

    env->ReleaseIntArrayElements(clipCounts, counts, 0);
    env->ReleaseLongArrayElements(allTrimIns, trimInArr, 0);
    env->ReleaseLongArrayElements(allTrimOuts, trimOutArr, 0);

    return engine->setMultiTrackTimeline(std::move(primaryClips), std::move(overlayClips),
                                          (float)overlayAlpha) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_iwatchme_renderengine_RenderEngine_nativeSetOverlayAlpha(JNIEnv* /* env */, jobject /* this */, jlong handle, jfloat alpha) {
    getEngine(handle)->setOverlayAlpha((float)alpha);
}

extern "C" JNIEXPORT void JNICALL
Java_com_iwatchme_renderengine_RenderEngine_nativeSetSubtitle(JNIEnv* env, jobject /* this */,
                                                              jlong handle,
                                                              jstring srtPath,
                                                              jstring fontPath,
                                                              jint fontSizePx) {
    const char* srtC = srtPath ? env->GetStringUTFChars(srtPath, nullptr) : "";
    const char* fontC = fontPath ? env->GetStringUTFChars(fontPath, nullptr) : "";
    std::string srt = srtC ? srtC : "";
    std::string font = fontC ? fontC : "";
    if (srtPath) env->ReleaseStringUTFChars(srtPath, srtC);
    if (fontPath) env->ReleaseStringUTFChars(fontPath, fontC);
    getEngine(handle)->setSubtitle(srt, font, (int)fontSizePx);
}

extern "C" JNIEXPORT void JNICALL
Java_com_iwatchme_renderengine_RenderEngine_nativeSetSubtitleEnabled(JNIEnv* /* env */, jobject /* this */, jlong handle, jboolean enabled) {
    getEngine(handle)->setSubtitleEnabled(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_iwatchme_renderengine_RenderEngine_nativeGetPosition(JNIEnv* /* env */, jobject /* this */, jlong handle) {
    return getEngine(handle)->getPosition();
}
