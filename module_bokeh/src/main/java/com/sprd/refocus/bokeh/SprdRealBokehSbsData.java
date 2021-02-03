package com.sprd.refocus.bokeh;

import com.sprd.refocus.RefocusUtils;

/**
 * sbs real-bokeh data  (use sprd lib)
 */

/*
1.mJpegImageData：拍照后得到的主senor的YUV数据转化为的jpeg数据，用于显示图片
2.mMainYuvImageData：拍照后拿到的主senor的YUV数据，即大图
3.mDepthYuvImageData：depth库输出的YUV数据
4.MainWidethData  4位byte数组
5.MainHeightData   4位byte数组
6.MainSizeData   4位byte数组
7.DepthWidethData  4位byte数组
8.DepthHeightData  4位byte数组
9.DepthSizeData    4位byte数组
10.a_dInBlurStrength  4位byte数组
11.a_dInPositionX  4位byte数组
12.a_dInPositionY  4位byte数组
13.param_state   4位byte数组 0：param为NULL，1：param需要从bokeh_param.bin文件获取
14.Rotation   4位byte数组
15.saveSr  4位byte数组
16.BOKE	  bokeh标识 4位byte数组
*/

public class SprdRealBokehSbsData extends SprdRealBokehData {

    public SprdRealBokehSbsData(byte[] content) {
        super(content, TYPE_BOKEH_SBS);
    }

    @Override
    public void initDatas(byte[] content) {
        bokehFlag = RefocusUtils.getStringValue(content, 1); //bokeh Flag -> BOKE
        saveSr = RefocusUtils.getIntValue(content, 2);
        rotation = RefocusUtils.getIntValue(content, 3);
        param_state = RefocusUtils.getIntValue(content, 4);
        sel_y = RefocusUtils.getIntValue(content, 5);
        sel_x = RefocusUtils.getIntValue(content, 6);
        blurIntensity = RefocusUtils.getIntValue(content, 7); // a_dInBlurStrength -> [0,255]
        depthSize = RefocusUtils.getIntValue(content, 8);
        depthHeight = RefocusUtils.getIntValue(content, 9);
        depthWidth = RefocusUtils.getIntValue(content, 10);
        mainYuvSize = RefocusUtils.getIntValue(content, 11);
        yuvHeight = RefocusUtils.getIntValue(content, 12);
        yuvWidth = RefocusUtils.getIntValue(content, 13);
        jpegSize = content.length - 13 * 4 - depthSize - mainYuvSize;

        mainYuv = new byte[mainYuvSize];
        depthData = new byte[depthSize];
        int mainYuvIndex = jpegSize;
        int depthIndex = mainYuvIndex + mainYuvSize;
        System.arraycopy(content, mainYuvIndex, mainYuv, 0, mainYuvSize);
        System.arraycopy(content, depthIndex, depthData, 0, depthSize);
    }

    @Override
    public String toString() {
        return "SprdRealBokehSbsData{" +
                "\nyuvWidth=" + yuvWidth +
                ", \nyuvHeight=" + yuvHeight +
                ", \nmainYuvSize=" + mainYuvSize +
                ", \ndepthWidth=" + depthWidth +
                ", \ndepthHeight=" + depthHeight +
                ", \ndepthSize=" + depthSize +
                ", \nblurIntensity=" + blurIntensity +
                ", \nsel_x=" + sel_x +
                ", \nsel_y=" + sel_y +
                ", \nparam_state=" + param_state +
                ", \nrotation=" + rotation +
                ", \nsaveSr=" + saveSr +
                ", \nbokehFlag='" + bokehFlag + '\'' +
                '}';
    }
}
