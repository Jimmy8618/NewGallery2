package com.sprd.refocus.bokeh;

import com.sprd.refocus.RefocusUtils;

   /*
    1.  mJpegImageData -> jpeg1
    2.  mMainJpegImageData -> jpeg ori
    3.  mDepthYuvImageData -> depth
    4.  MainWidthData
    5.  MainHeightData
    6   depth_width
    7   depth_height
    8.  DepthSizeData
    9.  i32BlurIntensity
    10. InPositionX
    11. InPositionY
    12  param_state
    13. rotation
    14. mJpegImageData-size -> jpeg1 size
    15. mMainJpegImageData—size -> ori jpeg size
    16. BOKE  */

public class SprdRealBokehJpegData extends SprdRealBokehData {

    public SprdRealBokehJpegData(byte[] content) {
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
        decryptMode = RefocusUtils.getIntValue(content, 15);
        jpegSize = content.length - params_size * 4 - depthSize - oriJpegSize; //eq jpegSize, check

        oriJpeg = new byte[oriJpegSize];
        depthData = new byte[depthSize];
        System.arraycopy(content, jpegSize, oriJpeg, 0, oriJpegSize);
        System.arraycopy(content, jpegSize + oriJpegSize, depthData, 0, depthSize);
    }

    public int getOriJpegSize() {
        return oriJpegSize;
    }

    public void setOriJpegSize(int oriJpegSize) {
        this.oriJpegSize = oriJpegSize;
    }

    @Override
    public String toString() {
        return "SprdRealBokehJpegData{" +
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
