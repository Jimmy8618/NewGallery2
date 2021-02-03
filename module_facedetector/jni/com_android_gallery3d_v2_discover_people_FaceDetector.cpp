#include <jni.h>
#include <android/log.h>
#include "facealignapi.h"
#include "sprdfdapi.h"
#include "faceverifyapi.h"

#define  ALOGE(...)  __android_log_print(ANDROID_LOG_ERROR,"FaceDetector_JNI",__VA_ARGS__)
#define  ALOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,"FaceDetector_JNI",__VA_ARGS__)

#define MIN(a, b) (a > b ? b : a)
#define MAX(a, b) (a > b ? a : b)
#define MIN_FACE_SIZE 50

extern "C" {

/*
 * Class:     com_android_gallery3d_v2_discover_people_FaceDetector
 * Method:    nativeDetectFaces
 * Signature: ([BII)[Lcom/android/gallery3d/v2/discover/people/FaceDetector$Face;
 */
JNIEXPORT jobjectArray JNICALL
Java_com_android_gallery3d_v2_discover_people_FaceDetector_nativeDetectFaces
        (JNIEnv *env, jobject obj, jbyteArray yuv, jint width, jint height) {
    //Java Class
    jclass faceObjClass = env->FindClass(
            "com/android/gallery3d/v2/discover/people/FaceDetector$Face");
    jmethodID faceObjMethodId = env->GetMethodID(faceObjClass, "<init>", "(IIIIIIII[S)V");

    //Handle
    FD_DETECTOR_HANDLE fdHandle;  // face detection
    FA_ALIGN_HANDLE faHandle;     // face alignment
    FV_HANDLE fvHandle;           // face verification

    // create face detection handle
    FD_OPTION opt;
    FdInitOption(&opt);
    //opt.maxFaceNum = 4;
    opt.minFaceSize = MIN_FACE_SIZE;//MAX(static_cast<unsigned int>(MIN(width, height) / 12), MIN_FACE_SIZE);
    opt.directions = FD_DIRECTION_ALL;
    opt.angleFrontal = FD_ANGLE_RANGE_90;
    opt.angleHalfProfile = FD_ANGLE_NONE;
    opt.angleFullProfile = FD_ANGLE_NONE;
    FdCreateDetector(&fdHandle, &opt);

    // create face alignment handle
    FaCreateAlignHandle(&faHandle);

    // create face verification handle: using 4 threads
    FvCreateFvHandle(&fvHandle, 4);

    //fdImage
    FD_IMAGE fdImg;
    fdImg.data = reinterpret_cast<unsigned char *>(env->GetByteArrayElements(yuv, NULL));
    fdImg.width = width;
    fdImg.height = height;
    fdImg.step = width;

    int faceCount;

    //detect faces
    int ret = FdDetectFace(fdHandle, &fdImg);

    if (ret != FD_OK) {
        faceCount = 0;
    } else {
        faceCount = FdGetFaceCount(fdHandle);
    }

    ALOGD("nativeDetectFaces faceCount = %d, minFaceSize = %d", faceCount, opt.minFaceSize);

    jobjectArray faceArray = env->NewObjectArray(faceCount, faceObjClass, NULL);

    FD_FACEINFO fdFace;
    for (int i = 0; i < faceCount; i++) {
        //get face info
        FdGetFaceInfo(fdHandle, i, &fdFace);

        FA_FACEINFO faFace;
        faFace.x = fdFace.x;
        faFace.y = fdFace.y;
        faFace.width = fdFace.width;
        faFace.height = fdFace.height;
        faFace.yawAngle = fdFace.yawAngle;
        faFace.rollAngle = fdFace.rollAngle;

        //align
        FA_SHAPE faceShape;
        FaFaceAlign(faHandle, (const FA_IMAGE *) &fdImg, &faFace, &faceShape);

        FV_IMAGE_YUV420SP fvImage;
        fvImage.yData = fdImg.data;
        fvImage.uvData = fvImage.yData + fdImg.width * fdImg.height;
        fvImage.width = fdImg.width;
        fvImage.height = fdImg.height;
        fvImage.format = 1;

        FV_FACEINFO fvFace;
        const int *data = faceShape.data;
        FV_POINT *lm = (FV_POINT *) (fvFace.landmarks);
        for (int i = 0; i < 7; i++) {
            lm[i].x = data[i * 2];
            lm[i].y = data[i * 2 + 1];
        }

        short featureVector[FV_FEATURE_LENGTH];

        //init to 0
        for (int i = 0; i < FV_FEATURE_LENGTH; ++i) {
            featureVector[i] = 0;
        }

        //extract feature vector
        FvExtractFeature_YUV420SP(fvHandle, &fvImage, &fvFace, featureVector);

        //create vector array
        jshortArray fv = env->NewShortArray(FV_FEATURE_LENGTH);
        env->SetShortArrayRegion(fv, 0, FV_FEATURE_LENGTH, featureVector);

        //create face object
        jobject faceObj = env->NewObject(faceObjClass, faceObjMethodId,
                                         fdFace.x, fdFace.y, fdFace.width,
                                         fdFace.height, fdFace.yawAngle, fdFace.rollAngle,
                                         fdFace.score, faceShape.score, fv);
        //add to faceArray
        env->SetObjectArrayElement(faceArray, i, faceObj);

        //delete
        env->DeleteLocalRef(fv);
        env->DeleteLocalRef(faceObj);
    }

    //delete
    env->ReleaseByteArrayElements(yuv, reinterpret_cast<jbyte *>(fdImg.data), 0);
    env->DeleteLocalRef(faceObjClass);

    FdDeleteDetector(&fdHandle);
    FaDeleteAlignHandle(&faHandle);
    FvDeleteFvHandle(&fvHandle);

    return faceArray;
}


/*
 * Class:     com_android_gallery3d_v2_discover_people_FaceDetector
 * Method:    nativeMatch
 * Signature: ([S[S)F
 */
JNIEXPORT jfloat JNICALL
Java_com_android_gallery3d_v2_discover_people_FaceDetector_nativeMatch
        (JNIEnv *env, jobject obj, jshortArray va, jshortArray vb) {
    jshort *a = env->GetShortArrayElements(va, NULL);
    jshort *b = env->GetShortArrayElements(vb, NULL);
    jfloat result = FvComputeSimilarity(a, b);

    env->ReleaseShortArrayElements(va, a, 0);
    env->ReleaseShortArrayElements(vb, b, 0);
    return result;
}

}