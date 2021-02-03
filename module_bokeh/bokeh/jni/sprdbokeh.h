#ifndef __SPRDBOKEH_LIBRARY_HEADER_
#define __SPRDBOKEH_LIBRARY_HEADER_

#ifdef __cplusplus
extern "C" {
#endif
const char *decryptionChars = "bokeh depth";

typedef int (*sprd_bokeh_Init)(void **handle, int a_dInImgW, int a_dInImgH, char *param);

typedef int (*sprd_bokeh_Close)(void *handle);


typedef int (*sprd_bokeh_VersionInfo_Get)(void *a_pOutBuf, int a_dInBufMaxSize);


typedef int (*sprd_bokeh_ReFocusPreProcess)(void *handle, void *a_pInBokehBufYCC420NV21,
                                            void *a_pInDisparityBuf16, int depthW, int depthH);

typedef int (*sprd_bokeh_ReFocusGen)(void *handle, void *a_pOutBlurYCC420NV21,
                                     int a_dInBlurStrength,
                                     int a_dInPositionX,
                                     int a_dInPositionY
);
typedef int (*sprd_bokeh_userset)(char *ptr, int size);
void realBokehLibInit();
void realBokehLibDeinit();

class SprdRealBokehLibManager {

public:

    SprdRealBokehLibManager();

    void *mRealBokehHandler;
    sprd_bokeh_Init mRealBokehInit;
    sprd_bokeh_Close mRealBokehClose;
    sprd_bokeh_VersionInfo_Get mRealBokehVersion;
    sprd_bokeh_ReFocusPreProcess mRealBokehPreprocess;
    sprd_bokeh_ReFocusGen mRealBokehGen;
    sprd_bokeh_userset mBokehUserSet;

    bool openRealBokehLib(const char *name);
};


#ifdef __cplusplus
}
#endif

#endif
