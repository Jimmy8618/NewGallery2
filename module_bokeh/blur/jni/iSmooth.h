#ifndef __ISMOOTH_H__
#define __ISMOOTH_H__

#if (defined WIN32 || defined REALVIEW)
#define JNIEXPORT
#else

#include <jni.h>

#endif

#ifdef __cplusplus
extern "C" {
#endif

#define MAX_ROI 10

typedef struct {
    int width;  // image width                                                  ->
    int height; // image height                                                 ->
    int productInfo; //procduct/platform ID
    float min_slope; //0.001~0.01, default is 0.005                             ->0.0004
    float max_slope; //0.01~0.1, default is 0.05                                ->0.0019
    float Findex2Gamma_AdjustRatio; //2~11, default is 6.0                      ->2.0f
    int Scalingratio;//only support 2,4,6,8                                     ->8 for input 5M(1952)
    int SmoothWinSize;//odd number                                              ->5
    int box_filter_size;//odd number, default: the same as SmoothWinSize        ->3

    // below for 2.0 only
    /* Register Parameters : depend on sensor module */
    unsigned short vcm_dac_up_bound; /* Default value : 0 */
    unsigned short vcm_dac_low_bound; /* Default value : 0 */
    unsigned short *vcm_dac_info; /* Default value : NULL (Resaved) */

    /* Register Parameters : For Tuning */
    unsigned char vcm_dac_gain; /* 0~128 Default value : 128 */
    unsigned char valid_depth_clip; /* The up bound of valid_depth */
    unsigned char method; /* The depth method. (Resaved) */
    /* Register Parameters : depend on AF Scanning */
    unsigned char row_num; /* The number of AF windows with row (i.e. vertical) */
    unsigned char column_num; /* The number of AF windows with row (i.e. horizontal) */
    unsigned char boundary_ratio; /*  (Unit : Percentage) */
    /* Customer Parameter */
    unsigned char sel_size; /* Range is [0,256]. Default is 1 */
    /* The valid_depth will be the percentage in the maximum difference of dac, if sel_size is 0
     * The valid_depth will be increase with sel_size x sel_size block if sel_size is not 0
     * And the default value of this must be 1 */
    unsigned char valid_depth; /* For Tuning Range : [0, 32], Default value : 4 */
    unsigned short slope; /* Range is [0,256]. Default is 0 */
    /* The slope function will be disabled, if slope is 0
     * Adn it will transfer the depth with power function, if slope is nor 0
     * And the default value of this must be 0 */
    unsigned char valid_depth_up_bound; /* For Tuning */
    unsigned char valid_depth_low_bound; /* For Tuning */
    /* Please refer the comment of sel_size */
    unsigned short *cali_dist_seq; /* Pointer */
    unsigned short *cali_dac_seq; /* Pointer */
    unsigned short cali_seq_len;

} InitParams;

typedef struct {

    int version; //1~2, 1: 1.0 bokeh; 2: 2.0 bokeh with AF                            ->1
    int roi_type; // 0: circle 1:rectangle 2:seg                                      ->2
    int F_number; // 1 ~ 20                                                           ->
    unsigned short sel_x; /* The point which be touched */
    unsigned short sel_y; /* The point which be touched */

    // for version 2.0
    unsigned short *win_peak_pos; /* The seqence of peak position which be provided via struct isp_af_fullscan_info */

    // for version 1.0
    int CircleSize;
    // for rectangle
    int valid_roi;//                                                                  ->
    int total_roi;
    int x1[MAX_ROI], y1[MAX_ROI]; // left-top point of roi                            ->1.2 only face input
    int x2[MAX_ROI], y2[MAX_ROI]; // right-bottom point of roi                        ->1.2 only face input
    int flag[MAX_ROI]; //0:face 1:body
    int rotate_angle; // counter clock-wise. 0:face up body down 90:face left body right 180:face down body up 270:face right body left  ->
    bool rear_cam_en; //1:rear camera capture 0:front camera capture

    short camera_angle;  //cmos rotate angle
    short mobile_angle;   //g-sensor acceleometer show rotate angle
    void *ptr1; //reserve
    void *ptr2; //reserve

} WeightParams;

void smoothLibInit();
void smoothLibDeinit();

typedef int (*iSmoothCapInit)(void **handle, InitParams *params);

typedef int (*iSmoothCap_VersionInfo_Get)(void *a_pOutBuf, int a_dInBufMaxSize);

typedef int (*iSmoothCapDeinit)(void *handle);

typedef int (*iSmoothCapCreateWeightMap)(void *handle, WeightParams *params, unsigned char *Src_YUV,
                                         unsigned short *outWeightMap);

typedef int (*iSmoothCapBlurImage)(void *handle, unsigned char *Src_YUV,
                                   unsigned short *inWeightMap, WeightParams *params,
                                   unsigned char *Output_YUV);

class SprdSmoothLibManager {

public:

    SprdSmoothLibManager();

    void *mSmoothLibHandler;
    iSmoothCapInit mSmoothCapInit;
    iSmoothCap_VersionInfo_Get mSmoothVersionInfo;
    iSmoothCapDeinit mSmoothDeinit;
    iSmoothCapCreateWeightMap mSmoothWeightMap;
    iSmoothCapBlurImage mBlurImage;

    bool openSmoothLib(const char *name);

    bool mInitCheck;
};


#ifdef __cplusplus
}
#endif

#endif
