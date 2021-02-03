//
// Created by rui.li on 2/5/18.
//

#include <jni.h>
#include <android/log.h>
#include "jni_sprd_dehaze.h"
#include "dehaze_interface.h"

#define RESULT_SUCCESS 0;
#define RESULT_FAIL -1;

#define  ALOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,"Dehaze",__VA_ARGS__)

void *dehaze_handle = NULL;

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_4) != JNI_OK) {
        ALOGD("jni_sprd_dehaze on load error: GetEnv failed");
        return -1;
    }

    return JNI_VERSION_1_4;
}

JNIEXPORT jint JNICALL Java_com_android_gallery3d_filtershow_filters_ImageFilterDehaze_dehaze_1init
        (JNIEnv *env, jobject obj, jint img_width, jint img_height) {
    dehaze_handle = dehaze_init(img_width, img_height);
    if (NULL == dehaze_handle) {
        ALOGD("fail to dehaze_init !");
        return RESULT_FAIL;
    }
    return RESULT_SUCCESS;
}


JNIEXPORT jint JNICALL
Java_com_android_gallery3d_filtershow_filters_ImageFilterDehaze_dehaze_1yuv4202rgb
        (JNIEnv *, jobject, jbyteArray) {
    return RESULT_SUCCESS;
}

JNIEXPORT jint JNICALL
Java_com_android_gallery3d_filtershow_filters_ImageFilterDehaze_dehaze_1rgb2yuv420
        (JNIEnv *, jobject, jbyteArray, jbyteArray, jbyteArray, jbyteArray) {
    return RESULT_SUCCESS;
}


JNIEXPORT jint JNICALL
Java_com_android_gallery3d_filtershow_filters_ImageFilterDehaze_dehaze_1copyRGBData
        (JNIEnv *env, jobject obj, jbyteArray in_data) {
    if (NULL != dehaze_handle) {
        jbyte *src_data = env->GetByteArrayElements(in_data, NULL);
        jsize len = env->GetArrayLength(in_data);

        int result = dehaze_copyRGBData(dehaze_handle, (uint8_t *) src_data);

        env->ReleaseByteArrayElements(in_data, src_data, 0);
        if (0 != result) {
            ALOGD("dehaze_copyRGBData fail result = %d", result);
            return RESULT_FAIL;
        }
    }
    return RESULT_SUCCESS;
}

JNIEXPORT jint JNICALL
Java_com_android_gallery3d_filtershow_filters_ImageFilterDehaze_dehaze_1process
        (JNIEnv *env, jobject obj, jbyteArray out_data) {
    if (NULL != dehaze_handle) {
        jbyte *dst_data = env->GetByteArrayElements(out_data, NULL);
        jsize out_len = env->GetArrayLength(out_data);

        int result = dehaze_process(dehaze_handle, (uint8_t *) dst_data);
        env->ReleaseByteArrayElements(out_data, dst_data, 0);
        if (0 != result) {
            ALOGD("dehaze_process fail result = %d ", result);
            return RESULT_FAIL;
        }
    }
    ALOGD("JNI_dehaze_process X");
    return RESULT_SUCCESS;
}


JNIEXPORT jint JNICALL
Java_com_android_gallery3d_filtershow_filters_ImageFilterDehaze_dehaze_1deinit
        (JNIEnv *env, jobject obj) {
    if (NULL != dehaze_handle) {
        int result = dehaze_deinit(dehaze_handle);
        if (0 != result) {
            ALOGD("dehaze_deinit fail result = %d", result);
            return RESULT_FAIL;
        }
    }
    return RESULT_SUCCESS;
}