package com.sprd.refocus;

public class BokehType {
    //media type
    public static final int MEDIA_TYPE_IMAGE_BLUR = 1 << 11; // blur
    public static final int MEDIA_TYPE_IMAGE_BOKEH = 1 << 12; //do bokeh in camera
    public static final int MEDIA_TYPE_IMAGE_BOKEH_GALLERY = 1 << 13; // bokeh and save in gallery
    public static final int MEDIA_TYPE_IMAGE_AI_SCENE = 1 << 14;
    public static final int MEDIA_TYPE_IMAGE_BOKEH_HDR = 1 << 15;//do bokeh in camera
    public static final int MEDIA_TYPE_IMAGE_BOKEH_HDR_GALLERY = 1 << 16;// bokeh and save in gallery
    public static final int MEDIA_TYPE_IMAGE_MOTION_PHOTO = 1 << 17;//motion photo
    public static final int MEDIA_TYPE_IMAGE_AI_SCENE_HDR = 1 << 18;
    public static final int MEDIA_TYPE_IMAGE_BOKEH_FDR_GALLERY = 1 << 26;

    //file flag
    public static final int IMG_TYPE_MODE_BLUR = 12;//0X000C blur has bokeh
    public static final int IMG_TYPE_MODE_BLUR_GALLERY = 268;//0X010C blur not bokeh (no use)
    public static final int IMG_TYPE_MODE_BOKEH = 16;//0X0010 real-bokeh has bokeh
    public static final int IMG_TYPE_MODE_BOKEH_HDR = 17;//0X0011 real-bokeh with hdr has bokeh
    public static final int IMG_TYPE_MODE_BOKEH_GALLERY = 272;//0X0110 real-bokeh not bokeh
    public static final int IMG_TYPE_MODE_BOKEH_HDR_GALLERY = 273;//0X0111 real-bokeh with hdr not bokeh
    public static final int IMG_TYPE_MODE_BOKEH_FDR = 18;
}