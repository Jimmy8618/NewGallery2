#include <jni.h>

#define LOG_TAG "libjni_sprd_real_bokeh"

#include <android/log.h>
#include "sprdbokeh.h"
#include "SGM_SPRD.h"
#include "sprdrealbokeh.h"
#include "sr_interface.h"
#include <string.h>
#include <time.h>
#include <fcntl.h>
#include <unistd.h>
//#include <utils/threads.h>
//#include <cutils/properties.h>
#include <dlfcn.h>
#include "Mutex.h"

#define  ALOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#define  ALOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define  ALOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    ALOGI("JNI_OnLoad!");
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_4) != JNI_OK) {
        ALOGE("ERROR: GetEnv failed");
        return -1;
    }
    return JNI_VERSION_1_4;
}

struct fields_t {
    jfieldID context;
};

static fields_t fields;
static android::Mutex sLock;

SprdRealBokehLibManager *gSprdRealBokeh;

static void *getHandle(JNIEnv *env, jobject thiz) {
    android::Mutex::Autolock l(sLock);
    return (void *) env->GetLongField(thiz, fields.context);
}

static void setContext(JNIEnv *env, jobject thiz, void *handle) {
    ALOGD("JNI setContext start ");
    android::Mutex::Autolock l(sLock);
    env->SetLongField(thiz, fields.context, (jlong) handle);
    ALOGD("JNI setContext end ");
}

void realBokehLibInit() {
    gSprdRealBokeh = new SprdRealBokehLibManager();
    if (gSprdRealBokeh == NULL) return;
    bool res = gSprdRealBokeh->openRealBokehLib("libsprdbokeh.so");
    if (!res) {
        ALOGD(" libsprdbokeh.so init failed");
        return;
    }
}

void realBokehLibDeinit() {
    if (gSprdRealBokeh != NULL) {
        if (gSprdRealBokeh->mRealBokehHandler) {
            dlclose(gSprdRealBokeh->mRealBokehHandler);
            gSprdRealBokeh->mRealBokehHandler = NULL;
        }
    }
}

SprdRealBokehLibManager::SprdRealBokehLibManager()
        : mRealBokehHandler(NULL),
          mRealBokehInit(NULL),
          mRealBokehClose(NULL),
          mRealBokehVersion(NULL),
          mRealBokehPreprocess(NULL),
          mRealBokehGen(NULL) {

}

bool SprdRealBokehLibManager::openRealBokehLib(const char *name) {
    if (mRealBokehHandler) {
        dlclose(mRealBokehHandler);
    }
    mRealBokehHandler = dlopen(name, RTLD_NOW);
    if (mRealBokehHandler == NULL) {
        ALOGE("openRealBokehLib, can't open %s= ", name);
        return false;
    }

    mRealBokehInit = (sprd_bokeh_Init) dlsym(mRealBokehHandler, "sprd_bokeh_Init");
    if (mRealBokehInit == NULL) {
        ALOGE("can not find sprd_bokeh_Init in  %s= ", name);
        dlclose(mRealBokehHandler);
        mRealBokehHandler = NULL;
        return false;
    }

    mRealBokehVersion = (sprd_bokeh_VersionInfo_Get) dlsym(mRealBokehHandler,
                                                           "sprd_bokeh_VersionInfo_Get");
    if (mRealBokehVersion == NULL) {
        ALOGE("can not find sprd_bokeh_VersionInfo_Get in  %s= ", name);
        dlclose(mRealBokehHandler);
        mRealBokehHandler = NULL;
        return false;
    }

    mRealBokehClose = (sprd_bokeh_Close) dlsym(mRealBokehHandler, "sprd_bokeh_Close");
    if (mRealBokehClose == NULL) {
        ALOGE("can not find sprd_bokeh_Close in  %s= ", name);
        dlclose(mRealBokehHandler);
        mRealBokehHandler = NULL;
        return false;
    }

    mRealBokehPreprocess = (sprd_bokeh_ReFocusPreProcess) dlsym(mRealBokehHandler,
                                                                "sprd_bokeh_ReFocusPreProcess");
    if (mRealBokehPreprocess == NULL) {
        ALOGE("can not find iSmoothCapCreateWeightMap in  %s= ", name);
        dlclose(mRealBokehHandler);
        mRealBokehHandler = NULL;
        return false;
    }

    mRealBokehGen = (sprd_bokeh_ReFocusGen) dlsym(mRealBokehHandler, "sprd_bokeh_ReFocusGen");
    if (mRealBokehGen == NULL) {
        ALOGE("can not find sprd_bokeh_ReFocusGen in  %s= ", name);
        dlclose(mRealBokehHandler);
        mRealBokehHandler = NULL;
        return false;
    }

    mBokehUserSet = (sprd_bokeh_userset) dlsym(mRealBokehHandler, "sprd_bokeh_userset");
    if (mBokehUserSet == NULL) {
        ALOGE("can not find sprd_bokeh_ReFocusGen in  %s= ", name);
        dlclose(mRealBokehHandler);
        mRealBokehHandler = NULL;
        return false;
    }

    return true;
}

JNIEXPORT jint JNICALL Java_com_sprd_refocus_bokeh_SprdRealBokeh_bokehInit
        (JNIEnv *env, jobject obj, jint img_width, jint img_height, jint param) {
    jclass clazz;
    clazz = env->GetObjectClass(obj);
    if (clazz == NULL) {
        ALOGD("ArcSoftRefocus_init,can't access class.");
        return -1;
    }
    fields.context = env->GetFieldID(clazz, "mNativeContext", "J");
    if (fields.context == NULL) {
        ALOGD("ArcSoftRefocus_init,can't get context.");
        return -1;
    }
    void *handle;
    ALOGI("sprd_bokeh_Init start.");
    // param is 0 ,set param NULL

    realBokehLibInit();
    if (gSprdRealBokeh == NULL) return 0;

    char *acVersion = new char[256];
    if (acVersion == NULL) {
        return 0;
    }
    gSprdRealBokeh->mRealBokehVersion((void *) acVersion, 256);
    ALOGD("Version bokeh lib :[%s]", acVersion);
    if (acVersion != NULL) {
        delete[] acVersion;
        acVersion = NULL;
    }

    jint res = gSprdRealBokeh->mRealBokehInit(&handle, img_width, img_height, NULL);
    ALOGD("sprd_bokeh_Init, init_handle = [%p]", handle);
    setContext(env, obj, handle);
    ALOGI("sprd_bokeh_Init res =   %d", res);
    return res;
}

JNIEXPORT jint JNICALL Java_com_sprd_refocus_bokeh_SprdRealBokeh_bokehClose
        (JNIEnv *env, jobject obj) {
    ALOGI("SprdRefocusBokeh_bokehClose !");
    void *close_handle = getHandle(env, obj);
    ALOGD("sprd_bokeh_Close, close_handle = [%p]", close_handle);
    if (gSprdRealBokeh == NULL) return 0;
    jint res = gSprdRealBokeh->mRealBokehClose(close_handle);
    realBokehLibDeinit();
    delete gSprdRealBokeh;
    gSprdRealBokeh = NULL;
    return res;
}

JNIEXPORT jint JNICALL
Java_com_sprd_refocus_bokeh_SprdRealBokeh_bokehReFocusPreProcess
        (JNIEnv *env, jobject obj, jbyteArray mainYuv, jbyteArray depth, jint depthW, jint depthH,
         jint decrypt_mode) {
    ALOGI("SprdRefocusBokeh_bokehReFocusPreProcess,depthW = %d", depthW);
    jbyte *main_yuv = env->GetByteArrayElements(mainYuv, NULL);
    jbyte *depth_data = env->GetByteArrayElements(depth, NULL);

    ALOGI("sprd_bokeh_ReFocusPreProcess start!");
    void *process_handle = getHandle(env, obj);

    ALOGD("sprd_bokeh_ReFocusPreProcess, process_handle = [%p]", process_handle);
    if (gSprdRealBokeh == NULL) return 0;
    if (decrypt_mode == 1) {
        int length = strlen(decryptionChars);
        char *userSet = new char[length];
        strcpy(userSet, decryptionChars);
        ALOGD("mBokehUserSet :[%s] , %d", userSet, length);
        gSprdRealBokeh->mBokehUserSet(userSet, length);
    }
    jint res = gSprdRealBokeh->mRealBokehPreprocess(process_handle, (void *) main_yuv,
                                                    (void *) depth_data, depthW, depthH);
    ALOGI("alRnBReFocusPreProcess res = %d", res);

    env->ReleaseByteArrayElements(mainYuv, (jbyte *) main_yuv, JNI_ABORT);
    env->ReleaseByteArrayElements(depth, (jbyte *) depth_data, JNI_ABORT);
    return res;
}

JNIEXPORT jbyteArray JNICALL
Java_com_sprd_refocus_bokeh_SprdRealBokeh_bokehReFocusGen
        (JNIEnv *env, jobject obj, jbyteArray output_yuv, jint a_dInBlurStrength,
         jint a_dInPositionX, jint a_dInPositionY) {
    ALOGI("SprdRefocusBokeh_bokehReFocusGen!");
    jbyte *out_bokeh_yuv = env->GetByteArrayElements(output_yuv, NULL);

    ALOGI("point = %d,%d", a_dInPositionX, a_dInPositionY);
    ALOGI("a_dInBlurStrength = %d", a_dInBlurStrength);
    void *gen_handle = getHandle(env, obj);
    ALOGD("sprd_bokeh_ReFocusPreProcess, gen_handle = [%p]", gen_handle);
    if (gSprdRealBokeh == NULL) return 0;
    jint res = gSprdRealBokeh->mRealBokehGen(gen_handle, out_bokeh_yuv, a_dInBlurStrength,
                                             a_dInPositionX, a_dInPositionY);
    ALOGI("alRnBReFocusGen res = %d", res);

    env->ReleaseByteArrayElements(output_yuv, (jbyte *) out_bokeh_yuv, JNI_ABORT);
    return output_yuv;
}

DistanceTwoPointInfo distance_two_point_info;

JNIEXPORT jint JNICALL Java_com_sprd_refocus_bokeh_SprdRealBokeh_distance
        (JNIEnv *env, jobject obj, jint distance_data, jbyteArray depth, jint x1, jint y1, jint x2,
         jint y2) {
    /* ALOGI("point1 = (%d,%d), point2 = (%d,%d)",x1,y1,x2,y2);
     distance_two_point_info.x1_pos = x1;
     distance_two_point_info.y1_pos = y1;
     distance_two_point_info.x2_pos = x2;
     distance_two_point_info.y2_pos = y2;

     ALOGI("SprdRefocusBokeh_distance!");
     jbyte *depth_data = env->GetByteArrayElements(depth, NULL);
     jint res = sprd_depth_distancemeasurement(&distance_data, (void *)depth_data, &distance_two_point_info);

     ALOGI("get distance result res = %d ",res);
     ALOGI("distance is = %d ",distance_data);
     env->ReleaseByteArrayElements(depth, (jbyte *)depth_data, JNI_ABORT);*/

    return 0;

}

void *sr_handle;

JNIEXPORT void JNICALL Java_com_sprd_refocus_bokeh_SprdRealBokeh_SrInit
        (JNIEnv *env, jobject obj, jint yuv_width, jint yuv_height, jint thread_num) {
#ifdef SPRD_NEED_SR
    sr_handle = sprd_sr_init(yuv_width, yuv_height, thread_num);
    if (sr_handle != NULL) {
        ALOGI("SrInit success!");
    }
#endif
}


JNIEXPORT jint JNICALL Java_com_sprd_refocus_bokeh_SprdRealBokeh_SrProcess
        (JNIEnv *env, jobject obj, jbyteArray input_yuv) {

    jint res = -1;

#ifdef SPRD_NEED_SR
    if (sr_handle == NULL) {
        ALOGI("SrProcess, Not SrInit, please check it!");
        return res;
    }
    jbyte *in_yuv = env->GetByteArrayElements(input_yuv, NULL);

    ALOGI("sprd_sr_process start.");
    if (gSprdRealBokeh == NULL) return 0;
    res = sprd_sr_process(sr_handle, (unsigned char *) in_yuv);
    ALOGI("sprd_sr_process end res = %d", res);

    env->ReleaseByteArrayElements(input_yuv, (jbyte *) in_yuv, JNI_ABORT);
#endif

    return res;
}


JNIEXPORT jint JNICALL Java_com_sprd_refocus_bokeh_SprdRealBokeh_SrDeinit
        (JNIEnv *, jobject) {

    jint res = -1;

#ifdef SPRD_NEED_SR
    if (sr_handle == NULL) {
        ALOGI("SrDeinit, Not SrInit, please check it!");
        return res;
    }
    if (gSprdRealBokeh == NULL) return 0;
    res = sprd_sr_deinit(sr_handle);
    ALOGI("sprd_sr_deinit res = %d", res);
#endif

    return res;
}

JNIEXPORT jint JNICALL Java_com_sprd_refocus_bokeh_SprdRealBokeh_depthRotate
        (JNIEnv *env, jobject obj, jbyteArray depth, jint width, jint height, jint rotate) {
    /* jbyte * depthaddress = env->GetByteArrayElements(depth, NULL);
     jint a = sprd_depth_rotate((void *)depthaddress, width, height, rotate);
     env->ReleaseByteArrayElements(depth, depthaddress, 0);
     ALOGD("return alSDE2Rotate :%d", a);*/
    return 0;
}