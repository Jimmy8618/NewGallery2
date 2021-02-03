package com.sprd.refocus;

import com.sprd.refocus.bokeh.SprdRealBokehData;

public class SprdGdepthJpegData extends SprdRealBokehData {

    public SprdGdepthJpegData(byte[] content) {
        super(content, TYPE_BOKEH_SPRD);
    }

    @Override
    public void initDatas(byte[] content) {
        bokehFlag = RefocusUtils.getStringValue(content, 1); // 1 bokeh Flag -> BOKE
        dataVersion = RefocusUtils.getIntValue(content, 2);
        oriJpegSize = RefocusUtils.getIntValue(content, 3);
        params_size = RefocusUtils.getIntValue(content, 4); // paramters size
        rotation = RefocusUtils.getIntValue(content, 5);
        param_state = RefocusUtils.getIntValue(content, 6);
        sel_y = RefocusUtils.getIntValue(content, 7);
        sel_x = RefocusUtils.getIntValue(content, 8);
        blurIntensity = RefocusUtils.getIntValue(content, 9);// [0,255]
        depthSize = RefocusUtils.getIntValue(content, 10); //
        depthHeight = RefocusUtils.getIntValue(content, 11);
        depthWidth = RefocusUtils.getIntValue(content, 12);
        yuvHeight = RefocusUtils.getIntValue(content, 13);
        yuvWidth = RefocusUtils.getIntValue(content, 14);
        decryptMode = RefocusUtils.getIntValue(content, 19);
        //int morePara = 4 * 4;// near,far,format,mime ; no use
        int gdepthsize = depthHeight * depthWidth;
        jpegSize = content.length - params_size * 4 - gdepthsize - depthSize - oriJpegSize; //eq jpegSize, check

        oriJpeg = new byte[oriJpegSize];
        depthData = new byte[depthSize];
        System.arraycopy(content, jpegSize, oriJpeg, 0, oriJpegSize);
        System.arraycopy(content, jpegSize + oriJpegSize, depthData, 0, depthSize);
    }

    @Override
    public String toString() {
        return "SprdGdepthJpegData{" +
                "\nyuvWidth=" + yuvWidth +
                ", \nyuvHeight=" + yuvHeight +
                ", \ndepthWidth=" + depthWidth +
                ", \ndepthHeight=" + depthHeight +
                ", \ndepthSize=" + depthSize +
                ", \nblurIntensity=" + blurIntensity +
                ", \nsel_x=" + sel_x +
                ", \nsel_y=" + sel_y +
                ", \nparam_state=" + param_state +
                ", \nrotation=" + rotation +
                ", \ndataVersion=" + dataVersion +
                ", \nparams_size=" + params_size +
                ", \njpegSize=" + jpegSize +
                ", \noriJpegSize=" + oriJpegSize +
                ", \nbokehFlag='" + bokehFlag + '\'' +
                '}';
    }
}
