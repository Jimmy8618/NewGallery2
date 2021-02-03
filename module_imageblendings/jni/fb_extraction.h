#ifndef _FB_EXTRACTION_H__
#define _FB_EXTRACTION_H__
#ifdef __cplusplus

extern "C"
{
#endif
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

const char *decryptionChars = "bokeh depth";
struct imgStru {
    int img_width;
    int img_height;
    unsigned char *img;
};
typedef struct extractinput_base_param {
    struct imgStru *inputImg;
    struct imgStru *depthImg;
    int *x_loc_grp;
    int *y_loc_grp;
    int sel_x;
    int sel_y;
    int Corner_x;
    int Corner_y;
    int loc_number;
    bool isFeather;
} extract_in_base_param;

typedef struct extract_output_base_param {
    struct imgStru *maskImg;
    int *obj_out_x;
    int *obj_out_y;
} extract_out_base_param;

void *sprd_ExtractFg_Init(void *DisparityImage, int nWidth, int nHeight);
int
sprd_ExtractFg_Update(void *handle, extract_in_base_param *input, extract_out_base_param *output,
                      bool Bg_flag);
int sprd_ExtractFg_2Pts(void *handle, extract_in_base_param *input, extract_out_base_param *output);
int sprd_ExtractFg_GetVersion(char *ver);
int sprd_ExtractFg_Deinit(void *handle);
int sprd_ExtractFg_Scaling(unsigned char *pInData, int input_width, int input_height,
                           unsigned char *pOutData, int output_width, int output_height,
                           int nChannels);
int sprd_ExtractFg_Depth_Scaling(unsigned char *depthIn, int input_width, int input_height,
                                 unsigned char *depthOut, int output_width, int output_height);
int sprd_ExtractFg_Createfbobject(struct imgStru *srcImg, struct imgStru *maskImg,
                                  struct imgStru *outputARGB, int *obj_start_x, int *obj_start_y);
int sprd_ExtractFg_Getfbobjectsize(struct imgStru *srcImg, struct imgStru *maskImg, int *width,
                                   int *height);

int sprd_ExtractFg_saveUpdate(void *handle, unsigned char *buf);
int sprd_ExtractFg_extractUpdate(void *handle, unsigned char *buf, extract_in_base_param *input,
                                 extract_out_base_param *output);
int sprd_ExtractFg_limitFbRange(void *handle, struct imgStru *maskImg, int width, int height,
                                int start_x, int start_y, int box_width, int box_height);

int sprd_ExtractFg_smoothMask(struct imgStru *maskImg, bool isFeather, int imgWidth, int imgHeight,
                              int start_x, int start_y, int box_width, int box_height);
int
sprd_ExtractFg_GetFeatherFbobjectSize(struct imgStru *srcImg, struct imgStru *maskImg, int *width,
                                      int *height);
int sprd_ExtractFg_CreateShowObject(struct imgStru *srcImg, struct imgStru *maskImg,
                                    struct imgStru *outputARGB, bool isFeather);
int sprd_ExtractFg_CreateFeatherfbobject(struct imgStru *srcImg, struct imgStru *maskImg,
                                         struct imgStru *outputARGB, int *start_x, int *start_y);
int sprd_ExtractFg_depth_rotate(void *a_pOutDisparity, int width, int height, int angle);
int sprd_ExtractFg_Depth_userset(void *handle, char *ptr, int size);
#ifdef __cplusplus
}
#endif

#endif