//
// Created by SPREADTRUM\ying.sun on 19-2-25.
//
#include <jni.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <vector>
#include <android/log.h> //需要在mk中添加引用

#include "jni_sprd_smarterase.h"
#include "InpaintLite.h"

#ifndef LOG_TAG
#define LOG_TAG "jni_smart_erase"
#define LOGV(...) __android_log_print(ANDROID_LOG_SILENT, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#endif

using namespace tflite::InpaintLite;

void *handle = 0;
//char* input_masked = "/storage/emulated/0/input_masked.bmp";
//char* input_mask = "/storage/emulated/0/input_mask.bmp";

JNIEXPORT jint

JNICALL Java_com_sprd_gallery3d_smarterase_SmartEraseNative_init
        (JNIEnv *, jobject) {

    LOGV("call InpaintLiteInit start");
    int success = InpaintLiteInit(&handle, "/system/etc/models/GatedConv_GLNet_256x256.tflite", 4,
                                  false);
    LOGV("call InpaintLiteInit end %d", success);
    return 0;

}

JNIEXPORT jint

JNICALL Java_com_sprd_gallery3d_smarterase_SmartEraseNative_deinit
        (JNIEnv *, jobject) {
    LOGV(" call InpaintLiteDeInit start");
    int success = InpaintLiteDeInit(handle);
    LOGV(" call InpaintLiteDeInit end %d", success);
    return success;
}

JNIEXPORT jint

JNICALL Java_com_sprd_gallery3d_smarterase_SmartErase_getVersion
        (JNIEnv *, jobject) {
    return 0;
}

JNIEXPORT jbyteArray

JNICALL Java_com_sprd_gallery3d_smarterase_SmartEraseNative_process
        (JNIEnv *env, jobject thiz, jbyteArray ori_image, jbyteArray mask, jint image_width,
         jint image_height) {
    int image_channels = 3;
    LOGV("process start");
    std::vector<uint8_t> pred(image_width * image_height * image_channels);

    jbyte *image_byte = env->GetByteArrayElements(ori_image, 0);
    jbyte *mask_byte = env->GetByteArrayElements(mask, 0);

    LOGV("image size: %d x %d, image channel: %d", image_width, image_height, image_channels);
    int result = InpaintLiteRun(handle, (uint8_t *) image_byte, (uint8_t *) mask_byte, image_width,
                                image_height,
                                image_channels, pred.data());
    LOGV("InpaintLiteRun %d ", result);

    env->ReleaseByteArrayElements(ori_image, image_byte, 0);
    env->ReleaseByteArrayElements(mask, mask_byte, 0);

    jbyteArray bytes = env->NewByteArray(image_width * image_height * image_channels);
    if (bytes != 0) {
        env->SetByteArrayRegion(bytes, 0, image_width * image_height * image_channels,
                                (jbyte *) pred.data());
    }
    LOGV("process end");
    return bytes;
}

