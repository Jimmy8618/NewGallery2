#include <jni.h>
#include "jni_sprd_imageblending.h"
#include "ImageBlending.h"
#include "fb_extraction.h"

#define LOG_TAG "jni_image_blending"

#include <android/log.h>

#define ALOGD(...) __android_log_print(ANDROID_LOG_DEBUG  , LOG_TAG, __VA_ARGS__)
struct fields_t {
    jfieldID out_width;
    jfieldID out_height;
    jfieldID out_coordinate_x;
    jfieldID out_coordinate_y;
    jfieldID native_context;
    jfieldID ExtractFg2Pts_Result_Code;

};

struct nativeContext_t {
    extract_in_base_param *input_param;
    extract_out_base_param *output_param;
    void *handle;
};

static fields_t fields;

//static android::Mutex sLock;
jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    ALOGD("JNI_OnLoad!");
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_4) != JNI_OK) {
        ALOGD("ERROR: GetEnv failed");
        return -1;
    }

    return JNI_VERSION_1_4;
}

static void setWidthHeight(JNIEnv *env, jobject thiz, int width, int height) {
    env->SetIntField(thiz, fields.out_width, width);
    env->SetIntField(thiz, fields.out_height, height);
}

static void setOutCoordinate(JNIEnv *env, jobject thiz, int x, int y) {
    env->SetIntField(thiz, fields.out_coordinate_x, x);
    env->SetIntField(thiz, fields.out_coordinate_y, y);
}

static void setNativeContext(JNIEnv *env, jobject thiz, nativeContext_t *context) {
    env->SetLongField(thiz, fields.native_context, (jlong) context);
}

static void setExtractFg2PtsResultCode(JNIEnv *env, jobject thiz, int result) {
    env->SetIntField(thiz, fields.ExtractFg2Pts_Result_Code, result);
}

static nativeContext_t *getNativeContext(JNIEnv *env, jobject thiz) {
    return (nativeContext_t *) env->GetLongField(thiz, fields.native_context);
}

/*
 * Class:     com_sprd_blending_ImageBlending
 * Method:    ImageBlendingInit
 * Signature: (II[BII[B)V
 */
JNIEXPORT void JNICALL Java_com_sprd_blending_ImageBlending_ImageBlendingInit
        (JNIEnv *env, jobject thiz,
         jint decryptMode/*, jint src_width, jint src_height, jbyteArray srcImg*/) {
    ALOGD("JNI ImageBlendingInit start ");
    jclass clazz;
    clazz = env->GetObjectClass(thiz);
    if (clazz == NULL) {
        return;
    }

    fields.out_width = env->GetFieldID(clazz, "mNativeWidth", "I");
    if (fields.out_width == NULL) {
        ALOGD("RETURN 4");
        return;
    }

    fields.out_height = env->GetFieldID(clazz, "mNativeHeight", "I");
    if (fields.out_height == NULL) {
        ALOGD("RETURN 5");
        return;
    }

    fields.out_coordinate_x = env->GetFieldID(clazz, "mOutputIconX", "I");
    if (fields.out_coordinate_x == NULL) {
        ALOGD("RETURN 6");
        return;
    }

    fields.out_coordinate_y = env->GetFieldID(clazz, "mOutputIconY", "I");
    if (fields.out_coordinate_y == NULL) {
        ALOGD("RETURN 7");
        return;
    }

    fields.ExtractFg2Pts_Result_Code = env->GetFieldID(clazz, "mExtractFg2PtsResultCode", "I");
    if (fields.ExtractFg2Pts_Result_Code == NULL) {
        ALOGD("RETURN 8");
        return;
    }

    fields.native_context = env->GetFieldID(clazz, "mNativeContext", "J");
    if (fields.native_context == NULL) {
        ALOGD("RETURN 9");
        return;
    }

    if (decryptMode == 1) {
        int length = strlen(decryptionChars);
        char *userSet = new char[length];
        strcpy(userSet, decryptionChars);
        ALOGD("Depth_userset :[%s] , %d", userSet, length);
        sprd_ExtractFg_Depth_userset(NULL, userSet, length);
    }
    /* struct img *srcImg0;
     srcImg0 = (struct img *) malloc(sizeof(struct img));
     srcImg0->img_width = src_width;
     srcImg0->img_height = src_height;
     srcImg0->img_ptr = (unsigned char *) env->GetByteArrayElements(srcImg, NULL);

     setContext(env, thiz, NULL, srcImg0, NULL, false);*/
    ALOGD("JNI ImageBlendingInit end ");

}

JNIEXPORT jbyteArray JNICALL Java_com_sprd_blending_ImageBlending__1getTargetImg
        (JNIEnv *env, jobject thiz, jint src_widht, jint src_height, jbyteArray srcImg,
         jint mask_widht, jint mask_height, jbyteArray maskImg, jfloat zoom) {
    ALOGD("JNI getTargetImg start ");
    int width, height;
    struct img *srcImg0, *maskImg0, *outImg0;
    maskImg0 = (struct img *) malloc(sizeof(struct img));
    maskImg0->img_width = mask_widht;
    maskImg0->img_height = mask_height;
    maskImg0->img_ptr = (unsigned char *) env->GetByteArrayElements(maskImg, NULL);

    srcImg0 = (struct img *) malloc(sizeof(struct img));
    srcImg0->img_width = src_widht;
    srcImg0->img_height = src_height;
    srcImg0->img_ptr = (unsigned char *) env->GetByteArrayElements(srcImg, NULL);

    sprd_get_icon_size(maskImg0, &width, &height, zoom);
    setWidthHeight(env, thiz, width, height);
    ALOGD("src:%p,data ptr:%p,width:%d,height:%d", srcImg0, srcImg0->img_ptr,
          srcImg0->img_width, srcImg0->img_height);
    ALOGD("target width:%d,heigh:%d", width, height);
    outImg0 = (struct img *) malloc(sizeof(struct img));
    outImg0->img_width = width;
    outImg0->img_height = height;
    int image_size = width * height * sizeof(unsigned char) * 4;
    outImg0->img_ptr = (unsigned char *) malloc(image_size);
    sprd_create_rgb_icon(srcImg0, maskImg0, outImg0, zoom);
    jbyteArray retArray = env->NewByteArray(image_size);
    env->SetByteArrayRegion(retArray, 0, image_size, (jbyte *) outImg0->img_ptr);
    free(srcImg0);
    free(maskImg0);
    free(outImg0->img_ptr);
    free(outImg0);
    ALOGD("JNI getTargetImg end ");
    return retArray;
}

/*
 * Class:     com_sprd_blending_ImageBlending
 * Method:    _doBlending
 * Signature: (II[BII)[B
 */
JNIEXPORT jbyteArray JNICALL Java_com_sprd_blending_ImageBlending__1doBlending
        (JNIEnv *env, jobject thiz, jint src_widht, jint src_height, jbyteArray srcImg,
         jint mask_widht, jint mask_height, jbyteArray maskImg,
         jint dst_width, jint dst_height, jbyteArray dstImg,
         jint start_x0, jint start_y0, jint center_x0, jint center_y0, jfloat scaleFactor,
         jfloat angle) {
    ALOGD("JNI doBlending start %f, %f", scaleFactor, angle);
    struct img *srcImg0, *maskImg0, *outImg0, *dstImg0;
    srcImg0 = (struct img *) malloc(sizeof(struct img));
    srcImg0->img_width = src_widht;
    srcImg0->img_height = src_height;
    srcImg0->img_ptr = (unsigned char *) env->GetByteArrayElements(srcImg, NULL);

    maskImg0 = (struct img *) malloc(sizeof(struct img));
    maskImg0->img_width = mask_widht;
    maskImg0->img_height = mask_height;
    maskImg0->img_ptr = (unsigned char *) env->GetByteArrayElements(maskImg, NULL);

    dstImg0 = (struct img *) malloc(sizeof(struct img));
    dstImg0->img_width = dst_width;
    dstImg0->img_height = dst_height;
    dstImg0->img_ptr = (unsigned char *) env->GetByteArrayElements(dstImg, NULL);

    outImg0 = (struct img *) malloc(sizeof(struct img));
    outImg0->img_width = srcImg0->img_width;
    outImg0->img_height = srcImg0->img_height;
    setWidthHeight(env, thiz, outImg0->img_width, outImg0->img_height);
    int imagesize = outImg0->img_width * outImg0->img_height * sizeof(unsigned char) * 4;
    outImg0->img_ptr = (unsigned char *) malloc(imagesize);

    ALOGD("src:%p,data ptr:%p,width:%d,height:%d", srcImg0, srcImg0->img_ptr,
          srcImg0->img_width, srcImg0->img_height);
    ALOGD("maskImg:%p,maskImg ptr:%p,width:%d,height:%d", maskImg0, maskImg0->img_ptr,
          maskImg0->img_width, maskImg0->img_height);

    ALOGD("dstImg:%p,dstImg ptr:%p,width:%d,height:%d", dstImg0, dstImg0->img_ptr,
          dstImg0->img_width, dstImg0->img_height);

    int a = sprd_imageBlending(srcImg0, dstImg0, maskImg0, outImg0, start_x0, start_y0, center_x0,
                               center_y0, scaleFactor, angle);
    ALOGD("return sprd_imageBlending :%d", a);
    if (a == -1) {
        return NULL;
    }
    jbyteArray retArray = env->NewByteArray(imagesize);
    env->SetByteArrayRegion(retArray, 0, imagesize, (jbyte *) outImg0->img_ptr);
    env->ReleaseByteArrayElements(srcImg, (jbyte *) srcImg0->img_ptr, 0);
    env->ReleaseByteArrayElements(maskImg, (jbyte *) maskImg0->img_ptr, 0);
    env->ReleaseByteArrayElements(dstImg, (jbyte *) dstImg0->img_ptr, 0);
    free(srcImg0);
    free(dstImg0);
    free(maskImg0);
    free(outImg0->img_ptr);
    free(outImg0);
    ALOGD("JNI doBlending end ");
    return retArray;
}


void checkAndReleaseOldNativeContext(JNIEnv *env, jobject thiz) {
    nativeContext_t *oldNativeContext = getNativeContext(env, thiz);
    if (oldNativeContext != NULL) {
        ALOGD("oldNativeContext is not null and should deinit first");
        sprd_ExtractFg_Deinit(oldNativeContext->handle);
        extract_in_base_param *input = oldNativeContext->input_param;
        extract_out_base_param *output = oldNativeContext->output_param;
        free(input->inputImg);
        free(input->depthImg);
        free(input);
        free(output->maskImg->img);
        free(output->maskImg);
        free(output->obj_out_x);
        free(output->obj_out_y);
        free(output);
        free(oldNativeContext);
        setNativeContext(env, thiz, NULL);
    }
}

jbyteArray JNICALL Java_com_sprd_blending_ImageBlending__1ExtractFg2Pts
        (JNIEnv *env, jobject thiz, jint src_width, jint src_height,
         jbyteArray srcImg, jbyteArray depthImg, jint centerX, jint centerY,
         jint cornerX, jint cornerY) {
    checkAndReleaseOldNativeContext(env, thiz);
    extract_in_base_param *input = (extract_in_base_param *) malloc(sizeof(extract_in_base_param));
    extract_out_base_param *output = (extract_out_base_param *) malloc(
            sizeof(extract_out_base_param));
    imgStru *inputImgu, *depthImgu, *maskImgu;
    inputImgu = (struct imgStru *) malloc(sizeof(struct imgStru));
    depthImgu = (struct imgStru *) malloc(sizeof(struct imgStru));
    maskImgu = (struct imgStru *) malloc(sizeof(struct imgStru));
    int *maskx, *masky;
    maskx = (int *) malloc(sizeof(int));
    masky = (int *) malloc(sizeof(int));

    inputImgu->img_width = src_width;
    inputImgu->img_height = src_height;
    inputImgu->img = (unsigned char *) malloc(src_width * src_height * 4);
    env->GetByteArrayRegion(srcImg, 0, src_width * src_height * 4, (jbyte *) inputImgu->img);
    // env->GetByteArrayElements(srcImg, NULL);;
    ALOGD("src img width:%d,height:%d,data:%p", src_width, src_height, inputImgu->img);

    depthImgu->img_width = src_width;
    depthImgu->img_height = src_height;
    depthImgu->img = (unsigned char *) malloc(src_width * src_height);
    env->GetByteArrayRegion(depthImg, 0, src_width * src_height, (jbyte *) depthImgu->img);
    //depthImgu->img = (unsigned char *) env->GetByteArrayElements(depthImg, NULL);
    ALOGD("depth width:%d,height:%d,data:%p", depthImgu->img_width, depthImgu->img_height,
          depthImgu->img);

    maskImgu->img_width = src_width;
    maskImgu->img_height = src_height;
    int masksize = src_width * src_height;
    maskImgu->img = (unsigned char *) malloc(masksize);
    ALOGD("mask data:%p", maskImgu->img);

    input->sel_x = centerX;
    input->sel_y = centerY;
    input->Corner_x = cornerX;
    input->Corner_y = cornerY;
    input->inputImg = inputImgu;
    input->depthImg = depthImgu;

    ALOGD("centerx:%d,centery:%d,cornerx:%d,cornery:%d", centerX, centerY, cornerX, cornerY);

    output->maskImg = maskImgu;
    output->obj_out_x = maskx;
    output->obj_out_y = masky;
    ALOGD("ExtractFg2Pts start ");
    nativeContext_t *nativeContext = (nativeContext_t *) malloc(sizeof(struct nativeContext_t));

    nativeContext->handle = sprd_ExtractFg_Init(input->depthImg->img, src_width, src_height);
    int a = sprd_ExtractFg_2Pts(nativeContext->handle, input, output);
    // a==-1 memory allocation failed or other reason
    // a== 1 NOT FOREGROUND OBJECT
    // a==0 success
    ALOGD("return sprd_ExtractFg_2Pts :%d", a);
    setExtractFg2PtsResultCode(env, thiz, a);
    if (a != 0) {
        return NULL;
    }
    setOutCoordinate(env, thiz, *maskx, *masky);
    nativeContext->input_param = input;
    nativeContext->output_param = output;
    setNativeContext(env, thiz, nativeContext);

    jbyteArray retArray = env->NewByteArray(masksize);
    env->SetByteArrayRegion(retArray, 0, masksize, (jbyte *) maskImgu->img);
    ALOGD("ExtractFg2Pts end ");
    return retArray;

}

jbyteArray JNICALL Java_com_sprd_blending_ImageBlending__1ExtractFgUpdate
        (JNIEnv *env, jobject thiz, jintArray xlocs, jintArray ylocs, jint locnumber,
         jboolean isBg) {
    nativeContext_t *oldNativeContext = getNativeContext(env, thiz);
    if (oldNativeContext == NULL) {
        ALOGD("not call ExtractFg2Pts first!");
        return NULL;
    }
    ALOGD("ExtractFgUpdate start");
    extract_in_base_param *input = oldNativeContext->input_param;
    extract_out_base_param *output = oldNativeContext->output_param;
    input->x_loc_grp = env->GetIntArrayElements(xlocs, NULL);
    input->y_loc_grp = env->GetIntArrayElements(ylocs, NULL);
    input->loc_number = locnumber;

    ALOGD("centerx:%d,centery:%d,cornerx:%d,cornery:%d", input->sel_x, input->sel_y,
          input->Corner_x, input->Corner_y);
    ALOGD("src img width:%d,height:%d,data:%p", input->inputImg->img_width,
          input->inputImg->img_height, input->inputImg->img);
    ALOGD("depthImg width:%d,height:%d,data:%p", input->depthImg->img_width,
          input->depthImg->img_height, input->depthImg->img);

    int a = sprd_ExtractFg_Update(oldNativeContext->handle, input, output, isBg);
    ALOGD("return sprd_ExtractFg_Update :%d", a);
    if (a == -1) {
        return NULL;
    }
    int masksize = output->maskImg->img_width * output->maskImg->img_height;
    setOutCoordinate(env, thiz, *(output->obj_out_x), *(output->obj_out_y));

    jbyteArray retArray = env->NewByteArray(masksize);
    env->SetByteArrayRegion(retArray, 0, masksize, (jbyte *) output->maskImg->img);
    ALOGD("ExtractFgUpdate end");
    return retArray;
}

jbyteArray JNICALL Java_com_sprd_blending_ImageBlending__1ExtractFgScaling
        (JNIEnv *env, jobject thiz, jbyteArray inputdata, jint input_widht,
         jint input_height, jint output_widht, jint output_height) {
    ALOGD("ExtractFgScaling start");
    int masksize = output_widht * output_height;
    unsigned char *outputbuffer = (unsigned char *) malloc(output_widht * output_height);
    unsigned char *inputbuffer = (unsigned char *) env->GetByteArrayElements(inputdata, NULL);
    int a = sprd_ExtractFg_Scaling(inputbuffer, input_widht, input_height, outputbuffer,
                                   output_widht, output_height, 1);
    ALOGD("return ExtractFgScaling :%d", a);
    if (a == -1) {
        return NULL;
    }
    jbyteArray retArray = env->NewByteArray(masksize);
    env->SetByteArrayRegion(retArray, 0, masksize, (jbyte *) outputbuffer);
    free(outputbuffer);
    env->ReleaseByteArrayElements(inputdata, (jbyte *) inputbuffer, 0);
    ALOGD("ExtractFgScaling end");
    return retArray;
}

jbyteArray JNICALL Java_com_sprd_blending_ImageBlending__1ExtractFgCreatefbobject
        (JNIEnv *env, jobject thiz, jint src_width, jint src_height,
         jbyteArray srcImg, jbyteArray maskImg, jint start_x, jint start_y, jint box_width,
         jint box_height) {
    ALOGD("ExtractFgCreatefbobject start");
    int outImgeWidth, outImgeHeight, startx, starty;
    imgStru srcImgu, maskImgu, outImgu;
    srcImgu.img_width = src_width;
    srcImgu.img_height = src_height;
    srcImgu.img = (unsigned char *) env->GetByteArrayElements(srcImg, NULL);

    maskImgu.img_width = src_width;
    maskImgu.img_height = src_height;
    maskImgu.img = (unsigned char *) env->GetByteArrayElements(maskImg, NULL);

    nativeContext_t *context = getNativeContext(env, thiz);
    sprd_ExtractFg_limitFbRange(context->handle, &maskImgu, src_width, src_height, start_x, start_y,
                                box_width, box_height);
    sprd_ExtractFg_Getfbobjectsize(&srcImgu, &maskImgu, &outImgeWidth, &outImgeHeight);
    ALOGD("Getfbobjectsize:width:%d,height:%d", outImgeWidth, outImgeHeight);
    setWidthHeight(env, thiz, outImgeWidth, outImgeHeight);
    if (outImgeWidth < 0 || outImgeHeight < 0) {
        return NULL;
    }

    int outsize = 4 * outImgeWidth * outImgeHeight;
    outImgu.img = (unsigned char *) malloc(outsize);
    outImgu.img_width = outImgeWidth;
    outImgu.img_height = outImgeHeight;
    int a = sprd_ExtractFg_Createfbobject(&srcImgu, &maskImgu, &outImgu, &startx, &starty);
    ALOGD("return ExtractFgCreatefbobject :%d", a);
    if (a == -1) {
        return NULL;
    }
    setOutCoordinate(env, thiz, startx, starty);
    jbyteArray retArray = env->NewByteArray(outsize);
    env->SetByteArrayRegion(retArray, 0, outsize, (jbyte *) outImgu.img);
    free(outImgu.img);
    env->ReleaseByteArrayElements(srcImg, (jbyte *) srcImgu.img, 0);
    env->ReleaseByteArrayElements(maskImg, (jbyte *) maskImgu.img, 0);
    ALOGD("ExtractFgCreatefbobject end");
    return retArray;
}

JNIEXPORT jint JNICALL Java_com_sprd_blending_ImageBlending__1verify_1icon_1pos
        (JNIEnv *env, jobject thiz, jbyteArray mask, jint mask_width, jint mask_height,
         jint start_x_new, jint start_y_new, jint center_x0, jint center_y0, jfloat zoom,
         jfloat angle) {
    unsigned char *loc = (unsigned char *) env->GetByteArrayElements(mask, NULL);
    ALOGD("verify_1icon_1pos mask_width : %d, mask_height : %d, start_x_new : %d , start_y_new : %d, mask : %p ",
          mask_width, mask_height, start_x_new, start_y_new, loc);
    int a = sprd_verify_icon_pos(loc, mask_width, mask_height, start_x_new, start_y_new, center_x0,
                                 center_y0, zoom, angle);
    env->ReleaseByteArrayElements(mask, (jbyte *) loc, 0);
    return a;
}

/*
 * Class:     com_sprd_blending_ImageBlending
 * Method:    _ImageBlendingDeinit
 * Signature: ()V
 */
void JNICALL Java_com_sprd_blending_ImageBlending__1ImageBlendingDeinit
        (JNIEnv *env, jobject thiz) {
    //do nothing
    checkAndReleaseOldNativeContext(env, thiz);
}

JNIEXPORT jint JNICALL Java_com_sprd_blending_ImageBlending__1ExtractFg_1saveUpdate
        (JNIEnv *env, jobject thiz, jbyteArray mask) {
    nativeContext_t *oldNativeContext = getNativeContext(env, thiz);
    unsigned char *buffer = (unsigned char *) env->GetByteArrayElements(mask, NULL);
    ALOGD("return saveUpdate : start");
    int a = sprd_ExtractFg_saveUpdate(oldNativeContext->handle, buffer);
    env->ReleaseByteArrayElements(mask, (jbyte *) buffer, 0);
    ALOGD("return saveUpdate :%d", a);
    // -1 is error
    // 0 is success
    return a;
}

JNIEXPORT jbyteArray JNICALL
Java_com_sprd_blending_ImageBlending__1ExtractFg_1extractUpdate
        (JNIEnv *env, jobject thiz, jbyteArray mask) {
    unsigned char *buffer = (unsigned char *) env->GetByteArrayElements(mask, NULL);
    nativeContext_t *oldNativeContext = getNativeContext(env, thiz);
    extract_in_base_param *input = oldNativeContext->input_param;
    extract_out_base_param *output = oldNativeContext->output_param;
    int result = sprd_ExtractFg_extractUpdate(oldNativeContext->handle, buffer, input, output);
    ALOGD("extractUpdate : %d", result);
    setExtractFg2PtsResultCode(env, thiz, result);
    if (result != 0) {
        env->ReleaseByteArrayElements(mask, (jbyte *) buffer, 0);
        return NULL;
    }
    int masksize = output->maskImg->img_width * output->maskImg->img_height;
    setOutCoordinate(env, thiz, *(output->obj_out_x), *(output->obj_out_y));
    jbyteArray retArray = env->NewByteArray(masksize);
    env->SetByteArrayRegion(retArray, 0, masksize, (jbyte *) output->maskImg->img);
    env->ReleaseByteArrayElements(mask, (jbyte *) buffer, 0);
    return retArray;
}

JNIEXPORT jbyteArray JNICALL Java_com_sprd_blending_ImageBlending__1Extract_1depthScaling
        (JNIEnv *env, jobject thiz, jbyteArray depthin, jint depthInWidth, jint depthInHeight,
         jint depthOutWidth, jint depthOutheight, jint depthHeadSize) {
    unsigned char *PdepthIn = (unsigned char *) env->GetByteArrayElements(depthin, NULL);
    int depthOutSize = depthOutheight * depthOutWidth + depthHeadSize;
    unsigned char *PdepthOut = (unsigned char *) malloc(depthOutSize);
    int result = sprd_ExtractFg_Depth_Scaling(PdepthIn, depthInWidth, depthInHeight, PdepthOut,
                                              depthOutWidth,
                                              depthOutheight);
    jbyteArray depthout = env->NewByteArray(depthOutSize);
    env->SetByteArrayRegion(depthout, 0, depthOutSize, (jbyte *) PdepthOut);
    env->ReleaseByteArrayElements(depthin, (jbyte *) PdepthIn, 0);
    free(PdepthOut);
    ALOGD("return depthScaling :%d", result);
    if (result != 0) {
        return NULL;
    }
    return depthout;
}

JNIEXPORT jint JNICALL Java_com_sprd_blending_ImageBlending__1ExtractFg_1depth_1rotate
        (JNIEnv *env, jobject obj, jbyteArray depth, jint width, jint height, jint rotate) {
    jbyte *depthaddress = env->GetByteArrayElements(depth, NULL);
    jint a = sprd_ExtractFg_depth_rotate((void *) depthaddress, width, height, rotate);
    env->ReleaseByteArrayElements(depth, depthaddress, 0);
    ALOGD("return ImageBlending alSDE2Rotate :%d", a);
    return a;
}