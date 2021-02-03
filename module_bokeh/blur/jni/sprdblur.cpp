#include <jni.h>

#define LOG_TAG "jni_sprd_newrefocus"

#include <android/log.h>
#include "iSmooth.h"
#include "commonblur.h"
#include "twoframeblur.h"
#include "Bokeh2Frames_interface.h"
#include <string.h>
#include <time.h>
#include <fcntl.h>
#include <unistd.h>
//#include <utils/threads.h>
//#include <cutils/properties.h>
#include <dlfcn.h>
#include <stdlib.h>
#include "Mutex.h"

#define  ALOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#define  ALOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define  ALOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)

struct fields_t {
    jfieldID context;
    jfieldID dst_buffer;

};
static fields_t fields;
static android::Mutex sLock;

SprdBokehFrameLibManager *gSprdBokeh;
SprdSmoothLibManager *gSprdSmooth;

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    ALOGI("JNI_OnLoad!");
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_4) != JNI_OK) {
        ALOGE("ERROR: GetEnv failed");
        return -1;
    }

    return JNI_VERSION_1_4;
}

static void *getHandle(JNIEnv *env, jobject thiz) {
    android::Mutex::Autolock l(sLock);
    return (void *) env->GetLongField(thiz, fields.context);
}

static void *getbuffer(JNIEnv *env, jobject thiz) {
    android::Mutex::Autolock l(sLock);
    return (void *) env->GetLongField(thiz, fields.dst_buffer);
}

static void setContext(JNIEnv *env, jobject thiz, void *handle, void *buffer) {
    ALOGD("JNI setContext start ");
    void *oldhandle = getHandle(env, thiz);

    if (oldhandle != NULL) {
        if (mIsTwoFrameBokeh == true) {
            ALOGD("JNI BokehFrames_Deinit ! ");
            if (gSprdBokeh != NULL) {
                gSprdBokeh->mBokehFramesDenit(oldhandle);
            }
        } else {
            ALOGD("JNI iSmoothCapDeinit ! ");
            if (gSprdSmooth != NULL) {
                gSprdSmooth->mSmoothDeinit(oldhandle);
            }
        }
    }
    char *oldbuffer = (char *) getbuffer(env, thiz);
    if (oldbuffer != NULL) {
        delete[] oldbuffer;
    }
    android::Mutex::Autolock l(sLock);
    env->SetLongField(thiz, fields.context, (jlong) handle);
    env->SetLongField(thiz, fields.dst_buffer, (jlong) buffer);
    ALOGD("JNI setContext end ");
}

void smoothLibInit() {
    gSprdSmooth = new SprdSmoothLibManager();
    if (gSprdSmooth == NULL) return;
    bool ret = gSprdSmooth->openSmoothLib("libbokeh_gaussian_cap.so");
    if (!ret) {
        ALOGD(" libbokeh_gaussian_cap.so init failed");
        return;
    }
}

void smoothLibDeinit() {
    if (gSprdSmooth != NULL) {
        if (gSprdSmooth->mSmoothLibHandler) {
            dlclose(gSprdSmooth->mSmoothLibHandler);
            gSprdSmooth->mSmoothLibHandler = NULL;
        }
    }
}

SprdSmoothLibManager::SprdSmoothLibManager()
        : mSmoothLibHandler(NULL),
          mSmoothCapInit(NULL),
          mSmoothVersionInfo(NULL),
          mSmoothDeinit(NULL),
          mSmoothWeightMap(NULL),
          mBlurImage(NULL) {

}

bool SprdSmoothLibManager::openSmoothLib(const char *name) {
    if (mSmoothLibHandler) {
        dlclose(mSmoothLibHandler);
    }
    mSmoothLibHandler = dlopen(name, RTLD_NOW);
    if (mSmoothLibHandler == NULL) {
        ALOGE("openSmoothLib, can't open %s= ", name);
        return false;
    }

    mSmoothCapInit = (iSmoothCapInit) dlsym(mSmoothLibHandler, "iSmoothCapInit");
    if (mSmoothCapInit == NULL) {
        ALOGE("can not find iSmoothCapInit in  %s= ", name);
        dlclose(mSmoothLibHandler);
        mSmoothLibHandler = NULL;
        return false;
    }

    mSmoothVersionInfo = (iSmoothCap_VersionInfo_Get) dlsym(mSmoothLibHandler,
                                                            "iSmoothCap_VersionInfo_Get");
    if (mSmoothVersionInfo == NULL) {
        ALOGE("can not find iSmoothCap_VersionInfo_Get in  %s= ", name);
        dlclose(mSmoothLibHandler);
        mSmoothLibHandler = NULL;
        return false;
    }

    mSmoothDeinit = (iSmoothCapDeinit) dlsym(mSmoothLibHandler, "iSmoothCapDeinit");
    if (mSmoothDeinit == NULL) {
        ALOGE("can not find iSmoothCapDeinit in  %s= ", name);
        dlclose(mSmoothLibHandler);
        mSmoothLibHandler = NULL;
        return false;
    }

    mSmoothWeightMap = (iSmoothCapCreateWeightMap) dlsym(mSmoothLibHandler,
                                                         "iSmoothCapCreateWeightMap");
    if (mSmoothWeightMap == NULL) {
        ALOGE("can not find iSmoothCapCreateWeightMap in  %s= ", name);
        dlclose(mSmoothLibHandler);
        mSmoothLibHandler = NULL;
        return false;
    }

    mBlurImage = (iSmoothCapBlurImage) dlsym(mSmoothLibHandler, "iSmoothCapBlurImage");
    if (mBlurImage == NULL) {
        ALOGE("can not find iSmoothCapBlurImage in  %s= ", name);
        dlclose(mSmoothLibHandler);
        mSmoothLibHandler = NULL;
        return false;
    }
    return true;
}


JNIEXPORT void JNICALL
Java_com_sprd_refocus_blur_CommonBlur_iSmoothCapInit(JNIEnv *env, jobject obj,
                                                     jint width, jint height,
                                                     jfloat min_slope,
                                                     jfloat max_slope,
                                                     jfloat Findex2Gamma_AdjustRatio,
                                                     jint Scalingratio,
                                                     jint SmoothWinSize,
                                                     jint box_filter_size,
                                                     jint vcm_dac_up_bound,
                                                     jint vcm_dac_low_bound,
                                                     jint vcm_dac_info,
                                                     jint vcm_dac_gain,
                                                     jint valid_depth_clip,
                                                     jint method, jint row_num,
                                                     jint column_num,
                                                     jint boundary_ratio,
                                                     jint sel_size,
                                                     jint valid_depth,
                                                     jint slope,
                                                     jint valid_depth_up_bound,
                                                     jint valid_depth_low_bound,
                                                     jintArray cali_dist_seq,
                                                     jintArray cali_dac_seq,
                                                     jint cali_seq_len,
                                                     jint platform_id) {

    smoothLibInit();
    if (gSprdSmooth == NULL) return;
    char *acVersion = new char[256];
    if (acVersion == NULL) return;
    gSprdSmooth->mSmoothVersionInfo((void *) acVersion, 256);
    ALOGD("Version bokeh_gaussian_cap lib :[%s]", acVersion);
    if (acVersion != NULL) {
        delete[] acVersion;
        acVersion = NULL;
    }

    ALOGD("iSmoothCapInit set params start ");
    InitParams init_params;
    init_params.width = (int) width;
    init_params.height = (int) height;
    init_params.productInfo = (int) platform_id;
    init_params.min_slope = (float) min_slope;
    init_params.max_slope = (float) max_slope;
    init_params.Findex2Gamma_AdjustRatio = (float) Findex2Gamma_AdjustRatio;
    init_params.Scalingratio = (int) Scalingratio;
    init_params.SmoothWinSize = (int) SmoothWinSize;
    init_params.box_filter_size = (int) box_filter_size;

    // blur 2.0 params
    init_params.vcm_dac_up_bound = (unsigned short) vcm_dac_up_bound;
    init_params.vcm_dac_low_bound = (unsigned short) vcm_dac_low_bound;
    // vcm_dac_info not be used , set it null
    init_params.vcm_dac_info = NULL;

    init_params.vcm_dac_gain = (unsigned char) vcm_dac_gain;
    init_params.valid_depth_clip = (unsigned char) valid_depth_clip;
    init_params.method = (unsigned char) method;

    init_params.row_num = (unsigned char) row_num;
    init_params.column_num = (unsigned char) column_num;
    init_params.boundary_ratio = (unsigned char) boundary_ratio;

    init_params.sel_size = (unsigned char) sel_size;
    init_params.valid_depth = (unsigned char) valid_depth;
    init_params.slope = (unsigned short) slope;

    init_params.valid_depth_up_bound = (unsigned char) valid_depth_up_bound;
    init_params.valid_depth_low_bound = (unsigned char) valid_depth_low_bound;


    int *c_cali_dist_seq_array = (int *) env->GetIntArrayElements(cali_dist_seq, 0);
    int len_arr_dist = env->GetArrayLength(cali_dist_seq);
    unsigned short cali_dist_seq_array[len_arr_dist];
    for (int i = 0; i < len_arr_dist; i++) {
        cali_dist_seq_array[i] = (unsigned short) c_cali_dist_seq_array[i];
    }

    int *c_cali_dac_seq_array = (int *) env->GetIntArrayElements(cali_dac_seq, 0);
    int len_arr_dac = env->GetArrayLength(cali_dac_seq);
    unsigned short cali_dac_seq_array[len_arr_dac];
    for (int i = 0; i < len_arr_dac; i++) {
        cali_dac_seq_array[i] = (unsigned short) c_cali_dac_seq_array[i];
    }

    init_params.cali_dac_seq = cali_dac_seq_array;
    init_params.cali_dist_seq = cali_dist_seq_array;
    init_params.cali_seq_len = (unsigned short) cali_seq_len;
    ALOGD("iSmoothCapInit set params end ");

    jclass clazz;
    clazz = env->GetObjectClass(obj);
    if (clazz == NULL) {
        return;
    }
    fields.context = env->GetFieldID(clazz, "mNativeContext", "J");
    if (fields.context == NULL) {
        return;
    }
    fields.dst_buffer = env->GetFieldID(clazz, "mNativeDstBuffer", "J");
    if (fields.dst_buffer == NULL) {
        return;
    }

    void *handle;
    mIsTwoFrameBokeh = false;
    ALOGD("JNI iSmoothCapInit start ");
    gSprdSmooth->mSmoothCapInit(&handle, &init_params);
    ALOGD("JNI iSmoothCapInit end ");
    char *dst_yuv = (char *) malloc(width * height * 3 / 2);
    env->ReleaseIntArrayElements(cali_dist_seq, c_cali_dist_seq_array, 0);
    env->ReleaseIntArrayElements(cali_dac_seq, c_cali_dac_seq_array, 0);
    setContext(env, obj, handle, (void *) dst_yuv);
}

JNIEXPORT jbyteArray JNICALL
Java_com_sprd_refocus_blur_CommonBlur_iSmoothCapBlurImage
        (JNIEnv *env, jobject obj, jbyteArray src_yuv_data, jbyteArray weightMap, jint rear_cam_en,
         jint version, jint roi_type,
         jint F_number, jint sel_x, jint sel_y, jintArray win_peak_pos, jint CircleSize,
         jint total_roi, jint valid_roi,
         jintArray x1, jintArray y1, jintArray x2, jintArray y2, jintArray flag_data,
         jint rotate_angle, jint camera_angle, jint mobile_angle) {
    WeightParams weight_Params;
    weight_Params.version = (int) version;
    weight_Params.roi_type = (int) roi_type;
    weight_Params.F_number = (int) F_number;
    weight_Params.sel_x = (unsigned short) sel_x;
    weight_Params.sel_y = (unsigned short) sel_y;

    int *c_array = (int *) env->GetIntArrayElements(win_peak_pos, 0);
    int len_arr = env->GetArrayLength(win_peak_pos);
    unsigned short depth[len_arr];
    for (int i = 0; i < len_arr; i++) {
        depth[i] = (unsigned short) c_array[i];
    }
    weight_Params.win_peak_pos = depth;

    weight_Params.CircleSize = (int) CircleSize;
    weight_Params.total_roi = (int) total_roi;
    weight_Params.valid_roi = (int) valid_roi;

    int *c_x1_array = (int *) env->GetIntArrayElements(x1, 0);
    int len_arr_x1 = env->GetArrayLength(x1);
    for (int i = 0; i < len_arr_x1; i++) {
        weight_Params.x1[i] = c_x1_array[i];
    }

    int *c_y1_array = (int *) env->GetIntArrayElements(y1, 0);
    int len_arr_y1 = env->GetArrayLength(y1);
    for (int i = 0; i < len_arr_y1; i++) {
        weight_Params.y1[i] = c_y1_array[i];
    }

    int *c_x2_array = (int *) env->GetIntArrayElements(x2, 0);
    int len_arr_x2 = env->GetArrayLength(x2);
    for (int i = 0; i < len_arr_x2; i++) {
        weight_Params.x2[i] = c_x2_array[i];
    }

    int *c_y2_array = (int *) env->GetIntArrayElements(y2, 0);
    int len_arr_y2 = env->GetArrayLength(y2);
    for (int i = 0; i < len_arr_y2; i++) {
        weight_Params.y2[i] = c_y2_array[i];
    }

    int *c_flag_array = (int *) env->GetIntArrayElements(flag_data, 0);
    int len_arr_flag = env->GetArrayLength(flag_data);
    for (int i = 0; i < len_arr_flag; i++) {
        weight_Params.flag[i] = c_flag_array[i];
    }
    weight_Params.rotate_angle = (int) rotate_angle;
    weight_Params.rear_cam_en = (int) rear_cam_en;
    weight_Params.camera_angle = (short) camera_angle;
    weight_Params.mobile_angle = (short) mobile_angle;

    void *handle = getHandle(env, obj);
    if (handle == NULL) {
        ALOGE("not init!,please check!");
        return NULL;
    }
    char *dst_yuv = (char *) getbuffer(env, obj);
    if (dst_yuv == NULL) {
        ALOGE("no dst buffer!,please check!");
        return NULL;
    }
    ALOGD("blur start");
    long bufferLength = (long) (env->GetArrayLength(src_yuv_data));
    jbyte *src_yuv_buf = env->GetByteArrayElements(src_yuv_data, NULL);
    if (gSprdSmooth == NULL) return NULL;
    if (version == 1 && roi_type == 2) {
        ALOGD("blur1.2 iSmoothCapBlurImage start");
        jbyte *inWeightMap = env->GetByteArrayElements(weightMap, NULL);
        gSprdSmooth->mBlurImage(handle, (unsigned char *) src_yuv_buf,
                                (unsigned short *) inWeightMap, &weight_Params,
                                (unsigned char *) dst_yuv);
        env->ReleaseByteArrayElements(weightMap, inWeightMap, JNI_ABORT);
        ALOGD("blur1.2 iSmoothCapBlurImage end");
    } else {
        gSprdSmooth->mSmoothWeightMap(handle, &weight_Params, (unsigned char *) src_yuv_buf, NULL);
        ALOGD("blur iSmoothCapBlurImage start");
        gSprdSmooth->mBlurImage(handle, (unsigned char *) src_yuv_buf, NULL, &weight_Params,
                                (unsigned char *) dst_yuv);
        ALOGD("blur iSmoothCapBlurImage end");
    }

    jbyteArray retArray = env->NewByteArray(bufferLength);
    env->SetByteArrayRegion(retArray, 0, bufferLength, (jbyte *) dst_yuv);
    //delete[] dst_yuv;
    ALOGD("blur end");
    env->ReleaseIntArrayElements(win_peak_pos, c_array, 0);
    env->ReleaseIntArrayElements(x1, c_x1_array, 0);
    env->ReleaseIntArrayElements(y1, c_y1_array, 0);
    env->ReleaseIntArrayElements(x2, c_x2_array, 0);
    env->ReleaseIntArrayElements(y2, c_y2_array, 0);
    env->ReleaseIntArrayElements(flag_data, c_flag_array, 0);
    env->ReleaseByteArrayElements(src_yuv_data, src_yuv_buf, JNI_ABORT);
    return retArray;
}

JNIEXPORT void JNICALL Java_com_sprd_refocus_blur_CommonBlur_iSmoothCapDeinit
        (JNIEnv *env, jobject obj) {
    void *handle = getHandle(env, obj);
    if (handle == NULL) {
        ALOGE("not init or have deinited already!,please check!");
    }

    char *dst_yuv = (char *) getbuffer(env, obj);
    if (dst_yuv == NULL) {
        ALOGE("no dst buffer!,please check!");
    }
    //iSmoothDeinit(handle);
    //free(dst_yuv);
    setContext(env, obj, NULL, NULL);
    smoothLibDeinit();
    delete gSprdSmooth;
    gSprdSmooth = NULL;

}

void bokehFramesLibInit() {
    gSprdBokeh = new SprdBokehFrameLibManager();
    if (gSprdBokeh == NULL) return;
    bool ret = gSprdBokeh->openBokehFrameLib("libBokeh2Frames.so");
    if (!ret) {
        ALOGD(" libBokeh2Frames.so init failed");
        return;
    }

}

void bokehFramesLibDeinit() {
    if (gSprdBokeh != NULL) {
        if (gSprdBokeh->mBokehFrameLibHandler) {
            dlclose(gSprdBokeh->mBokehFrameLibHandler);
            gSprdBokeh->mBokehFrameLibHandler = NULL;
        }
    }
}

SprdBokehFrameLibManager::SprdBokehFrameLibManager()
        : mBokehFrameLibHandler(NULL),
          mBokehFramesVersion(NULL),
          mBokehFramesinit(NULL),
          mBokehFramesWeightMap(NULL),
          mBokehFrameProcess(NULL),
          mBokehFrameParaInfo(NULL),
          mBokehFramesDenit(NULL) {

}

bool SprdBokehFrameLibManager::openBokehFrameLib(const char *name) {
    if (mBokehFrameLibHandler) {
        dlclose(mBokehFrameLibHandler);
    }
    mBokehFrameLibHandler = dlopen(name, RTLD_NOW);
    if (mBokehFrameLibHandler == NULL) {
        ALOGE("openSmoothLib, can't open %s= ", name);
        return false;
    }

    mBokehFramesinit = (BokehFrames_Init) dlsym(mBokehFrameLibHandler, "BokehFrames_Init");
    if (mBokehFramesinit == NULL) {
        ALOGE("can not find iSmoothCapInit in  %s= ", name);
        dlclose(mBokehFrameLibHandler);
        mBokehFrameLibHandler = NULL;
        return false;
    }

    mBokehFramesVersion = (BokehFrames_VersionInfo_Get) dlsym(mBokehFrameLibHandler,
                                                              "BokehFrames_VersionInfo_Get");
    if (mBokehFramesVersion == NULL) {
        ALOGE("can not find BokehFrames_VersionInfo_Get in  %s= ", name);
        dlclose(mBokehFrameLibHandler);
        mBokehFrameLibHandler = NULL;
        return false;
    }

    mBokehFramesinit = (BokehFrames_Init) dlsym(mBokehFrameLibHandler, "BokehFrames_Init");
    if (mBokehFramesinit == NULL) {
        ALOGE("can not find BokehFrames_Init in  %s= ", name);
        dlclose(mBokehFrameLibHandler);
        mBokehFrameLibHandler = NULL;
        return false;
    }

    mBokehFramesWeightMap = (BokehFrames_WeightMap) dlsym(mBokehFrameLibHandler,
                                                          "BokehFrames_WeightMap");
    if (mBokehFramesWeightMap == NULL) {
        ALOGE("can not find BokehFrames_WeightMap in  %s= ", name);
        dlclose(mBokehFrameLibHandler);
        mBokehFrameLibHandler = NULL;
        return false;
    }

    mBokehFrameProcess = (Bokeh2Frames_Process) dlsym(mBokehFrameLibHandler,
                                                      "Bokeh2Frames_Process");
    if (mBokehFrameProcess == NULL) {
        ALOGE("can not find Bokeh2Frames_Process in  %s= ", name);
        dlclose(mBokehFrameLibHandler);
        mBokehFrameLibHandler = NULL;
        return false;
    }

    mBokehFrameParaInfo = (BokehFrames_ParamInfo_Get) dlsym(mBokehFrameLibHandler,
                                                            "BokehFrames_ParamInfo_Get");
    if (mBokehFrameParaInfo == NULL) {
        ALOGE("can not find BokehFrames_ParamInfo_Get in  %s= ", name);
        dlclose(mBokehFrameLibHandler);
        mBokehFrameLibHandler = NULL;
        return false;
    }

    mBokehFramesDenit = (BokehFrames_Deinit) dlsym(mBokehFrameLibHandler, "BokehFrames_Deinit");
    if (mBokehFramesDenit == NULL) {
        ALOGE("can not find BokehFrames_Deinit in  %s= ", name);
        dlclose(mBokehFrameLibHandler);
        mBokehFrameLibHandler = NULL;
        return false;
    }
    return true;
}


JNIEXPORT void JNICALL Java_com_sprd_refocus_blur_TwoFrameBlur_twoFrameBokehInit
        (JNIEnv *env, jobject obj, jint width, jint height, jint isp_tunning, jint tmp_thr,
         jint tmp_mode, jint similar_factor, jint merge_factor,
         jint refer_len, jint scale_factor, jint touch_factor, jint smooth_thr, jint depth_mode,
         jint fir_edge_factor, jint fir_cal_mode, jint fir_channel, jint fir_len, jint fir_mode,
         jint enable, jintArray hfir_coeff, jintArray vfir_coeff, jintArray similar_coeff,
         jintArray tmp_coeff) {

    jclass clazz;
    clazz = env->GetObjectClass(obj);
    if (clazz == NULL) {
        return;
    }

    fields.context = env->GetFieldID(clazz, "mNativeContext", "J");
    if (fields.context == NULL) {
        return;
    }
    fields.dst_buffer = env->GetFieldID(clazz, "mNativeDstBuffer", "J");
    if (fields.dst_buffer == NULL) {
        return;
    }

    mIsTwoFrameBokeh = true;
    void *handle;
    char acVersion[256];
    bokehFramesLibInit();
    if (gSprdBokeh == NULL) return;
    gSprdBokeh->mBokehFramesVersion(acVersion, 256);
    ALOGD("Version BokehFrames[%s]", acVersion);
    InitBoke2FramesParams init_boke_Params;

    init_boke_Params.enable = (unsigned long) enable;
    init_boke_Params.fir_mode = (unsigned long) fir_mode;
    init_boke_Params.fir_len = (unsigned long) fir_len;
    init_boke_Params.fir_channel = (unsigned long) fir_channel;
    init_boke_Params.fir_cal_mode = (unsigned long) fir_cal_mode;
    init_boke_Params.fir_edge_factor = (signed long) fir_edge_factor;
    init_boke_Params.depth_mode = (unsigned long) depth_mode;
    init_boke_Params.smooth_thr = (unsigned long) smooth_thr;
    init_boke_Params.touch_factor = (unsigned long) touch_factor;
    init_boke_Params.scale_factor = (unsigned long) scale_factor;
    init_boke_Params.refer_len = (unsigned long) refer_len;
    init_boke_Params.merge_factor = (unsigned long) merge_factor;
    init_boke_Params.similar_factor = (unsigned long) similar_factor;
    init_boke_Params.tmp_mode = (unsigned long) tmp_mode;
    init_boke_Params.tmp_thr = (unsigned long) tmp_thr;

    int *c_hfir_coeff = (int *) env->GetIntArrayElements(hfir_coeff, 0);
    int len_hfir_coeff = env->GetArrayLength(hfir_coeff);
    for (int i = 0; i < len_hfir_coeff; i++) {
        init_boke_Params.hfir_coeff[i] = (signed long) c_hfir_coeff[i];
    }

    int *c_vfir_coeff = (int *) env->GetIntArrayElements(vfir_coeff, 0);
    int len_vfir_coeff = env->GetArrayLength(vfir_coeff);
    for (int i = 0; i < len_vfir_coeff; i++) {
        init_boke_Params.vfir_coeff[i] = (signed long) c_vfir_coeff[i];
    }

    int *c_similar_coeff = (int *) env->GetIntArrayElements(similar_coeff, 0);
    int len_similar_coeff = env->GetArrayLength(similar_coeff);
    for (int i = 0; i < len_similar_coeff; i++) {
        init_boke_Params.similar_coeff[i] = (unsigned long) c_similar_coeff[i];
    }

    int *c_tmp_coeff = (int *) env->GetIntArrayElements(tmp_coeff, 0);
    int len_tmp_coeff = env->GetArrayLength(tmp_coeff);
    for (int i = 0; i < len_tmp_coeff; i++) {
        init_boke_Params.tmp_coeff[i] = (signed long) c_tmp_coeff[i];
    }

    ALOGD("JNI twoFrameBokeInit start  width = %d, height = %d, isp_tunning = %d", width, height,
          isp_tunning);
    if (isp_tunning == 0) {
        ALOGD("JNI twoFrameBokeInit start no use isp params.");
        gSprdBokeh->mBokehFramesinit(&handle, (int) width, (int) height, NULL);
    } else {
        ALOGD("JNI twoFrameBokeInit start use isp params.");
        gSprdBokeh->mBokehFramesinit(&handle, (int) width, (int) height, &init_boke_Params);
    }
    ALOGD("JNI twoFrameBokeInit end ");
    env->ReleaseIntArrayElements(hfir_coeff, c_hfir_coeff, 0);
    env->ReleaseIntArrayElements(vfir_coeff, c_vfir_coeff, 0);
    env->ReleaseIntArrayElements(similar_coeff, c_similar_coeff, 0);
    env->ReleaseIntArrayElements(tmp_coeff, c_tmp_coeff, 0);

    char *dst_yuv = (char *) malloc(width * height * 3 / 2);
    setContext(env, obj, handle, (void *) dst_yuv);
}

JNIEXPORT jbyteArray JNICALL
Java_com_sprd_refocus_blur_TwoFrameBlur_twoFrameDepthInit
        (JNIEnv *env, jobject obj, jbyteArray near_yuv, jbyteArray far_yuv, jbyteArray depth) {
    void *handle = getHandle(env, obj);
    if (handle == NULL) {
        ALOGE("not init!,please check!");
        return NULL;
    }
    jbyte *near_yuv_buf = env->GetByteArrayElements(near_yuv, NULL);
    jbyte *far_yuv_buf = env->GetByteArrayElements(far_yuv, NULL);
    jbyte *depth_buf = env->GetByteArrayElements(depth, NULL);
    if (gSprdBokeh == NULL) return NULL;
    int res = gSprdBokeh->mBokehFramesWeightMap((void *) near_yuv_buf, (void *) far_yuv_buf,
                                                (void *) depth_buf, handle);
    ALOGE("twoFrameDepthInit res = %d", res);

    long depthLength = (long) (env->GetArrayLength(depth));
    jbyteArray depthArray = env->NewByteArray(depthLength);
    env->SetByteArrayRegion(depthArray, 0, depthLength, (jbyte *) depth_buf);

    env->ReleaseByteArrayElements(near_yuv, near_yuv_buf, 0);
    env->ReleaseByteArrayElements(far_yuv, far_yuv_buf, 0);
    env->ReleaseByteArrayElements(depth, depth_buf, 0);

    return depthArray;

}

JNIEXPORT jbyteArray JNICALL
Java_com_sprd_refocus_blur_TwoFrameBlur_twoFrameBokeh
        (JNIEnv *env, jobject obj, jbyteArray main_yuv, jbyteArray weight_map, jint F_number,
         jint sel_x, jint sel_y) {
    void *handle = getHandle(env, obj);
    if (handle == NULL) {
        ALOGE("not init!,please check!");
        return NULL;
    }
    char *dst_yuv = (char *) getbuffer(env, obj);
    if (dst_yuv == NULL) {
        ALOGE("no dst buffer!,please check!");
        return NULL;
    }
    WeightBoke2FramesParams weight_bokeh_params;
    weight_bokeh_params.F_number = (int) F_number;
    weight_bokeh_params.sel_x = (unsigned short) sel_x;
    weight_bokeh_params.sel_y = (unsigned short) sel_y;
    ALOGD("twoFrameBokeh start, F = %d, point = (%d,%d)", F_number, sel_x, sel_y);

    jbyte *main_yuv_buf = env->GetByteArrayElements(main_yuv, NULL);
    jbyte *weight_map_buf = env->GetByteArrayElements(weight_map, NULL);
    long bufferLength = (long) (env->GetArrayLength(main_yuv));
    if (gSprdBokeh == NULL) return NULL;
    gSprdBokeh->mBokehFrameProcess((void *) main_yuv_buf, (void *) dst_yuv, (void *) weight_map_buf,
                                   handle, &weight_bokeh_params);
    jbyteArray retArray = env->NewByteArray(bufferLength);
    env->SetByteArrayRegion(retArray, 0, bufferLength, (jbyte *) dst_yuv);
    ALOGD("twoFrameBokeh end");
    env->ReleaseByteArrayElements(main_yuv, main_yuv_buf, 0);
    env->ReleaseByteArrayElements(weight_map, weight_map_buf, 0);

    return retArray;
}


JNIEXPORT void JNICALL
Java_com_sprd_refocus_blur_TwoFrameBlur_twoFrameBokehDeinit
        (JNIEnv *env, jobject obj) {
    void *handle = getHandle(env, obj);
    if (handle == NULL) {
        ALOGE("not init or have deinited already!,please check!");
    }

    char *dst_yuv = (char *) getbuffer(env, obj);
    if (dst_yuv == NULL) {
        ALOGE("no dst buffer!,please check!");
    }
    setContext(env, obj, NULL, NULL);
    bokehFramesLibDeinit();
    delete gSprdBokeh;
    gSprdBokeh = NULL;
}
