#pragma once

#include <android/log.h>

#define RE_TAG "RenderEngine"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, RE_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, RE_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, RE_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, RE_TAG, __VA_ARGS__)
