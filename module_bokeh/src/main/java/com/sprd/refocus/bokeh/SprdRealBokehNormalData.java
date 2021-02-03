package com.sprd.refocus.bokeh;

import com.sprd.refocus.RefocusUtils;

public class SprdRealBokehNormalData extends SprdRealBokehData {

    public SprdRealBokehNormalData(byte[] content) {
        super(content, TYPE_BOKEH_SPRD);
    }

    @Override
    public void initDatas(byte[] content) {
        bokehFlag = RefocusUtils.getStringValue(content, 1); // 1 bokeh Flag -> BOKE
        rotation = RefocusUtils.getIntValue(content, 2); //2 rotation
        param_state = RefocusUtils.getIntValue(content, 3); //3 param_state
        sel_y = RefocusUtils.getIntValue(content, 4); //4 a_dInPositionY
        sel_x = RefocusUtils.getIntValue(content, 5); // 5 a_dInPositionX
        blurIntensity = RefocusUtils.getIntValue(content, 6); // 6 a_dInBlurStrength -> [0,255]
        depthSize = RefocusUtils.getIntValue(content, 7);  // 7 DepthSizeData
        depthHeight = RefocusUtils.getIntValue(content, 8); // 8 DepthHeightData
        depthWidth = RefocusUtils.getIntValue(content, 9); // 9 DepthWidthData
        mainYuvSize = RefocusUtils.getIntValue(content, 10); // 10 MainSizeData
        yuvHeight = RefocusUtils.getIntValue(content, 11); // 11 MainHeightData
        yuvWidth = RefocusUtils.getIntValue(content, 12); // 12 MainWidthData
        jpegSize = content.length - 12 * 4 - depthSize - mainYuvSize;

        mainYuv = new byte[mainYuvSize];
        depthData = new byte[depthSize];
        int mainYuvIndex = jpegSize;
        int depthIndex = mainYuvIndex + mainYuvSize;
        System.arraycopy(content, mainYuvIndex, mainYuv, 0, mainYuvSize);
        System.arraycopy(content, depthIndex, depthData, 0, depthSize);
    }

    @Override
    public String toString() {
        return "SprdRealBokehNormalData{" +
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
                ", \nbokehFlag='" + bokehFlag + '\'' +
                '}';
    }
}
