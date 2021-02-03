#ifndef __BOKEH2FRAMES_INTERFACE_H__
#define __BOKEH2FRAMES_INTERFACE_H__

#ifdef __cplusplus
extern "C" {
#endif

typedef unsigned char cmr_u8;
typedef unsigned long cmr_u32;
typedef signed long cmr_s32;

typedef struct {
    cmr_u32 enable;
    cmr_u32 fir_mode;
    cmr_u32 fir_len;
    cmr_s32 hfir_coeff[7];
    cmr_s32 vfir_coeff[7];
    cmr_u32 fir_channel;
    cmr_u32 fir_cal_mode;
    cmr_s32 fir_edge_factor;
    cmr_u32 depth_mode;
    cmr_u32 smooth_thr;
    cmr_u32 touch_factor;
    cmr_u32 scale_factor;
    cmr_u32 refer_len;
    cmr_u32 merge_factor;
    cmr_u32 similar_factor;
    cmr_u32 similar_coeff[3];
    cmr_u32 tmp_mode;
    cmr_s32 tmp_coeff[8];
    cmr_u32 tmp_thr;
} InitBoke2FramesParams;

typedef struct {
    int F_number; // 1 ~ 20
    unsigned short sel_x; /* The point which be touched */
    unsigned short sel_y; /* The point which be touched */
} WeightBoke2FramesParams;

typedef struct {
    cmr_u8 *microdepth_buffer;
    cmr_u32 microdepth_size;
} MicrodepthBoke2Frames;

void bokehFramesLibInit();
void bokehFramesLibDeinit();

typedef int (*BokehFrames_VersionInfo_Get)(char a_acOutRetbuf[256], unsigned int a_udInSize);

typedef int  (*BokehFrames_Init)(void **handle, int width, int height,
                                 InitBoke2FramesParams *params);

typedef int (*BokehFrames_WeightMap)(void *img0_src, void *img1_src, void *dis_map, void *handle);

typedef int(*Bokeh2Frames_Process)(void *img0_src, void *img_rslt, void *dis_map, void *handle,
                                   WeightBoke2FramesParams *params);

typedef int (*BokehFrames_ParamInfo_Get)(void *handle, MicrodepthBoke2Frames **microdepthInfo);

typedef int (*iSmoothCapBlurImage)(void *handle, unsigned char *Src_YUV,
                                   unsigned short *inWeightMap, WeightParams *params,
                                   unsigned char *Output_YUV);

typedef int (*BokehFrames_Deinit)(void *handle);

class SprdBokehFrameLibManager {
public:
    SprdBokehFrameLibManager();

    BokehFrames_VersionInfo_Get mBokehFramesVersion;
    BokehFrames_Init mBokehFramesinit;
    BokehFrames_WeightMap mBokehFramesWeightMap;
    Bokeh2Frames_Process mBokehFrameProcess;
    BokehFrames_ParamInfo_Get mBokehFrameParaInfo;
    BokehFrames_Deinit mBokehFramesDenit;


    bool openBokehFrameLib(const char *name);

    bool mBokehFrameInitCheck;
    void *mBokehFrameLibHandler;


/*int BokehFrames_VersionInfo_Get(char a_acOutRetbuf[256], unsigned int a_udInSize);
int BokehFrames_Init(void **handle,int width,int height,InitBoke2FramesParams *params);
int BokehFrames_WeightMap(void * img0_src, void * img1_src,void *dis_map, void *handle);
int Bokeh2Frames_Process(void * img0_src, void *img_rslt,void *dis_map,void *handle,WeightBoke2FramesParams *params);
int BokehFrames_ParamInfo_Get(void *handle, MicrodepthBoke2Frames **microdepthInfo);
int BokehFrames_Deinit(void *handle);*/
};

#ifdef __cplusplus
}
#endif

#endif
