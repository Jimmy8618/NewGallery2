/*-------------------------------------------------------------------*/
/*  Copyright(C) 2016 by Spreadtrum                                  */
/*  All Rights Reserved.                                             */
/*-------------------------------------------------------------------*/
/* 
    Face Verification Library API
*/
#ifndef __SPRD_FACE_VERIFICATION_H__
#define __SPRD_FACE_VERIFICATION_H__

#if (defined( WIN32 ) || defined( WIN64 )) && (defined FVAPI_EXPORTS)
#define FV_EXPORTS __declspec(dllexport)
#else
#define FV_EXPORTS
#endif

#ifndef FVAPI
#define FVAPI(rettype) extern FV_EXPORTS rettype
#endif

/* The error codes */
#define FV_OK                    0  /* Ok!                                      */
#define FV_ERROR_INTERNAL       -1  /* Error: Unknown internal error            */
#define FV_ERROR_NOMEMORY       -2  /* Error: Memory allocation error           */
#define FV_ERROR_INVALIDARG     -3  /* Error: Invalid argument                  */

/* the length of the feature vector extracted by FvExtractFeature() */
#define FV_FEATURE_LENGTH       512

// A YUV 4:2:0 image with a plane of 8bit Y samples followed by an
// interleaved U/V planes.
typedef struct {
    unsigned char *yData;       /* Y data pointer                    */
    unsigned char *uvData;      /* UV data pointer                   */
    int width;                  /* Image width                       */
    int height;                 /* Image height                      */
    unsigned char format;       /* Image format. 0->NV12; 1->NV21    */
    /* NV12 format; pixel order:  CbCrCbCr     */
    /* NV21 format; pixel order:  CrCbCrCb     */
} FV_IMAGE_YUV420SP;

// A YUV 4:2:0 image with a plane of 8bit Y samples followed by
// seperate U and V planes with arbitrary row and column strides.
typedef struct {
    unsigned char *yData;       /* Y data pointer                    */
    unsigned char *uData;       /* U (Cb) data pointer               */
    unsigned char *vData;       /* V (Cr) data pointer               */
    int width;                  /* Image width                       */
    int height;                 /* Image height                      */
    int yRowStride;             /* bytes per row for Y channel       */
    int uvRowStride;            /* bytes per row for U(V) channel    */
    int uvPixelStride;          /* U/V pixel stride                  */
} FV_IMAGE_YUV420;

typedef struct {
    int x, y;
} FV_POINT;

/* The face information structure */
typedef struct {
    FV_POINT landmarks[7]; /* The facial landmark points. The sequence is: Two left-eye corner points
                              Two right-eye corner points, nose tip, mouth left corner, mouth right corner
                            */
} FV_FACEINFO;

/* The face verification handle */
typedef void *FV_HANDLE;

#ifdef  __cplusplus
extern "C" {
#endif

FVAPI(const char *)FvGetVersion();

/* Create a FV handle. threadNum must be in [1, 4] */
FVAPI(int) FvCreateFvHandle(FV_HANDLE *hFV, int threadNum);

/* Release the FV handle */
FVAPI(void) FvDeleteFvHandle(FV_HANDLE *hFV);

/* Extract a feature vector from the YUV420SP image */
FVAPI(int) FvExtractFeature_YUV420SP(FV_HANDLE hFV,
                                     const FV_IMAGE_YUV420SP *inImage,
                                     const FV_FACEINFO *face,
                                     short *featureVector);

FVAPI(int) FvExtractFeature_YUV420(FV_HANDLE hFV,
                                   const FV_IMAGE_YUV420 *inImage,
                                   const FV_FACEINFO *face,
                                   short *featureVector);

/* Compute the similarity of two feature vectors extracted from two face images */
FVAPI(float) FvComputeSimilarity(const short *featureVectorA,
                                 const short *featureVectorB);


#ifdef  __cplusplus
}
#endif

#endif /* __SPRD_FACE_VERIFICATION_H__ */
