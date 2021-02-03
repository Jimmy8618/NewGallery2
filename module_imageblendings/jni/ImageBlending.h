#ifndef _IMAGE_BLENDING_H_
#define _IMAGE_BLENDING_H_
#ifdef __cplusplus

extern "C"
{
#endif
struct img {
    int img_width;
    int img_height;
    unsigned char *img_ptr;
};
int
sprd_imageBlending(struct img *srcImg, struct img *dstImg, struct img *maskImg, struct img *outImg,
                   int start_x0, int start_y0, int center_x0, int center_y0, float scalratio,
                   float angle);
int sprd_create_rgb_icon(struct img *srcImg, struct img *maskImg, struct img *outImg, float zoom);
int sprd_get_icon_size(struct img *maskImg, int *width, int *height, float zoom);
int sprd_verify_icon_pos(unsigned char *mask_image, int mask_image_width, int mask_image_height,
                         int start_x_new, int start_y_new, int center_x0, int center_y0, float zoom,
                         float angle);
#ifdef __cplusplus
}
#endif
#endif
