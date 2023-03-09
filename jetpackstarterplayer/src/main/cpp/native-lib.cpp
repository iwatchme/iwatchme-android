#include <jni.h>
#include <string>

#include "common/logutil.h"

#include "codec/FFmpegMediaCodec.h"

extern "C" {

#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
#include "libavfilter/avfilter.h"
#include "libavcodec/jni.h"
#include <jni.h>


JNIEXPORT
jint JNI_OnLoad(JavaVM *vm, void *res) {
    av_jni_set_java_vm(vm, 0);
    return JNI_VERSION_1_4;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_iwatchme_jetpackstarterplayer_MainActivity_decodeWithPath(JNIEnv *env, jobject thiz, jstring path) {

    const char *inputPath = "/sdcard/sintel.mp4";
    const char *outputPath = "/sdcard/sintel.yuv";

    char info[40000] = {0};
    void *iter = NULL;
    const AVCodec *c_temp = NULL;
    while (c_temp = av_codec_iterate(&iter)) {
        if (avcodec_find_decoder_by_name(c_temp->name) != NULL) {
            sprintf(info, "%s[Dec]", info);
        } else {
            sprintf(info, "%s[Enc]", info);
        }

        switch (c_temp->type) {
            case AVMEDIA_TYPE_VIDEO:
                sprintf(info, "%s[Video]", info);
                break;
            case AVMEDIA_TYPE_AUDIO:
                sprintf(info, "%s[Audio]", info);
                break;
            default:
                sprintf(info, "%s[Other]", info);
                break;
        }
        sprintf(info, "%s %10s\n", info, c_temp->name);
    }

    LOGD("%s", info);

    FFmpegMediaCodec *mediaCodec = new FFmpegMediaCodec;
    int ret = mediaCodec->decode(inputPath, outputPath);
    LOGD("mediacodec ret is %d", ret);

}



extern "C"
JNIEXPORT void JNICALL
Java_com_iwatchme_jetpackstarterplayer_Decoder_decodeToSurface(JNIEnv *env, jobject thiz, jstring path,
                                             jobject surface) {

    const char *inputPath = "/sdcard/sintel.mp4";

    FFmpegMediaCodec *mediaCodec = new FFmpegMediaCodec;
    int ret = mediaCodec->decode(inputPath, surface);
    LOGD("mediacodec ret is %d", ret);
}

}

