package com.sprd.refocus.blur;

import com.sprd.refocus.RefocusData;
import com.sprd.refocus.RefocusUtils;

import java.util.Arrays;

/**
 * TwoFrameBlurData:
 * it is blur 3.0
 * <p>
 * 1 has WeightMap
 * 2 no WeightMap
 */

/*
1）mJpegImageData：拍照后得到的主senor的YUV数 据转化为的jpeg数据，用 于显示图片

2）后景yuv：拍照后拿到的后景的YUV数据，size是width * height *3/ 2
   如果calWeightMap 为0 即 widgetMap在拍摄时算好了，这个位置就不是存的后景yuv而是widgetMap

3）前景yuv：拍照后拿到的前景的YUV数据，size是width * height *3/ 2 , 也即mainYuv用来做blur的yuv
4）参数---所有参数都转化为4位byte数组

1~40是初始化参数InitBoke2FramesParams的成员的值
-----------------
1~8：tmp_coeff[8]
9～11： similar_coeff[3];
12~18：vfir_coeff[7];
19~25：hfir_coeff[7];
26： enable
27： fir_mode
28：fir_len
29: fir_channel
30:fir_cal_mode
31：fir_edge_factor
32：depth_mode
33：smooth_thr
34：touch_factor
35：scale_factor
36：refer_len
37：merge_factor
38：similar_factor
39：tmp_mode
40：tmp_thr
----------------
41：rotation 照片方向
42： YUV图的宽
43： YUV图的高
44：mFNum
45：touchValid x坐标
46：touchValid y坐标
47： 是否在图库进行blur，0：否，1：是
48： 是否使用isp tunning参数，0：否，1：是
49：version（blur1.0，blur1.1，blur1.2是1，blur2.0是2，blur3.0是3）
50：BlurFlag（“BLUR”）
*/


public class TwoFrameBlurData extends RefocusData {
    // near yuv is the mainyuv
    private byte[] farYuvData;
    private byte[] weightMap;
    private int[] tmp_coeff;
    private int[] similar_coeff;
    private int[] vfir_coeff;
    private int[] hfir_coeff;
    private int enable;
    private int fir_mode;
    private int fir_len;
    private int fir_channel;
    private int fir_cal_mode;
    private int fir_edge_factor;
    private int depth_mode;
    private int smooth_thr;
    private int touch_factor;
    private int scale_factor;
    private int refer_len;
    private int merge_factor;
    private int similar_factor;
    private int tmp_mode;
    private int tmp_thr;
    private int calWeightMap;//是否在图库进行blur，0：否，1：是
    private int ispTunning; // 0：否，1：是
    private int version;
    private String blurFlag;

    public TwoFrameBlurData(byte[] content) {
        super(content, TYPE_BLUR_TF);
    }

    @Override
    public void initDatas(byte[] content) {
        blurFlag = RefocusUtils.getStringValue(content, 1); // 1 BlurFlag
        version = RefocusUtils.getIntValue(content, 2);
        ispTunning = RefocusUtils.getIntValue(content, 3); // 3 isp tunning? 0 no, 1 yes
        calWeightMap = RefocusUtils.getIntValue(content, 4); //4  0: don't need to calculate depth, 1: need calculate
        sel_y = RefocusUtils.getIntValue(content, 5); //5 mSelCoordY
        sel_x = RefocusUtils.getIntValue(content, 6); // 6 mSelCoordX
        blurIntensity = RefocusUtils.getIntValue(content, 7);   // 7 mFNum
        yuvHeight = RefocusUtils.getIntValue(content, 8);  // 8 mMainHeightData
        yuvWidth = RefocusUtils.getIntValue(content, 9); // 9 mMainWidethData
        rotation = RefocusUtils.getIntValue(content, 10); // 10 rotation

        tmp_thr = RefocusUtils.getIntValue(content, 11); // 11 tmp_thr
        tmp_mode = RefocusUtils.getIntValue(content, 12); // 12 tmp_mode
        similar_factor = RefocusUtils.getIntValue(content, 13); // 13 similar_factor
        merge_factor = RefocusUtils.getIntValue(content, 14); // 14 merge_factor
        refer_len = RefocusUtils.getIntValue(content, 15); // 15 refer_len
        scale_factor = RefocusUtils.getIntValue(content, 16); // 16 scale_factor
        touch_factor = RefocusUtils.getIntValue(content, 17); // 17 touch_factor
        smooth_thr = RefocusUtils.getIntValue(content, 18); // 18 smooth_thr
        depth_mode = RefocusUtils.getIntValue(content, 19); // 19 depth_mode
        fir_edge_factor = RefocusUtils.getIntValue(content, 20); // 20 fir_edge_factor
        fir_cal_mode = RefocusUtils.getIntValue(content, 21); // 21 fir_cal_mode
        fir_channel = RefocusUtils.getIntValue(content, 22); // 22 fir_channel
        fir_len = RefocusUtils.getIntValue(content, 23); // 23 fir_len
        fir_mode = RefocusUtils.getIntValue(content, 24); // 24 fir_mode
        enable = RefocusUtils.getIntValue(content, 25); // 25 enable

        hfir_coeff = new int[7];
        vfir_coeff = new int[7];
        similar_coeff = new int[3];
        tmp_coeff = new int[8];

        //26-32
        for (int i = 0; i < 7; i++) {
            hfir_coeff[i] = RefocusUtils.getIntValue(content, 32 - i);
        }

        //33 - 39
        for (int i = 0; i < 7; i++) {
            vfir_coeff[i] = RefocusUtils.getIntValue(content, 39 - i);
        }

        //40 -42
        for (int i = 0; i < 3; i++) {
            similar_coeff[i] = RefocusUtils.getIntValue(content, 42 - i);
        }

        //43-50
        for (int i = 0; i < 8; i++) {
            tmp_coeff[i] = RefocusUtils.getIntValue(content, 50 - i);
        }

        mainYuvSize = yuvHeight * yuvWidth * 3 / 2;  //9 main_yuv_data  size is w * h* 3/2
        if (calWeightMap == 0) {
            int weightMapSize = yuvHeight * yuvWidth / 4;    //10 weight_map size is w * h / 4
            jpegSize = content.length - mainYuvSize - weightMapSize - 50 * 4;
            weightMap = new byte[weightMapSize];
            mainYuv = new byte[mainYuvSize];
            int weightMapIndex = jpegSize;
            int mainYuvIndex = weightMapIndex + weightMapSize;
            System.arraycopy(content, weightMapIndex, weightMap, 0, weightMapSize);
            System.arraycopy(content, mainYuvIndex, mainYuv, 0, mainYuvSize);
            depthData = weightMap;
        } else if (calWeightMap == 1) {
            jpegSize = content.length - mainYuvSize * 2 - 50 * 4;
            farYuvData = new byte[mainYuvSize];
            mainYuv = new byte[mainYuvSize];
            int farYuvIndex = jpegSize;
            int mainYuvIndex = farYuvIndex + mainYuvSize;
            System.arraycopy(content, farYuvIndex, farYuvData, 0, mainYuvSize);
            System.arraycopy(content, mainYuvIndex, mainYuv, 0, mainYuvSize);
        }
    }

    public Boolean needCalWidgetMap() {
        return calWeightMap != 0 || weightMap == null;
    }

    public byte[] getFarYuvData() {
        return farYuvData;
    }

    public void setFarYuvData(byte[] farYuvData) {
        this.farYuvData = farYuvData;
    }

    public byte[] getWeightMap() {
        return weightMap;
    }

    public void setWeightMap(byte[] weightMap) {
        this.weightMap = weightMap;
    }

    public int[] getTmp_coeff() {
        return tmp_coeff;
    }

    public void setTmp_coeff(int[] tmp_coeff) {
        this.tmp_coeff = tmp_coeff;
    }

    public int[] getSimilar_coeff() {
        return similar_coeff;
    }

    public void setSimilar_coeff(int[] similar_coeff) {
        this.similar_coeff = similar_coeff;
    }

    public int[] getVfir_coeff() {
        return vfir_coeff;
    }

    public void setVfir_coeff(int[] vfir_coeff) {
        this.vfir_coeff = vfir_coeff;
    }

    public int[] getHfir_coeff() {
        return hfir_coeff;
    }

    public void setHfir_coeff(int[] hfir_coeff) {
        this.hfir_coeff = hfir_coeff;
    }

    public int getEnable() {
        return enable;
    }

    public void setEnable(int enable) {
        this.enable = enable;
    }

    public int getFir_mode() {
        return fir_mode;
    }

    public void setFir_mode(int fir_mode) {
        this.fir_mode = fir_mode;
    }

    public int getFir_len() {
        return fir_len;
    }

    public void setFir_len(int fir_len) {
        this.fir_len = fir_len;
    }

    public int getFir_channel() {
        return fir_channel;
    }

    public void setFir_channel(int fir_channel) {
        this.fir_channel = fir_channel;
    }

    public int getFir_cal_mode() {
        return fir_cal_mode;
    }

    public void setFir_cal_mode(int fir_cal_mode) {
        this.fir_cal_mode = fir_cal_mode;
    }

    public int getFir_edge_factor() {
        return fir_edge_factor;
    }

    public void setFir_edge_factor(int fir_edge_factor) {
        this.fir_edge_factor = fir_edge_factor;
    }

    public int getDepth_mode() {
        return depth_mode;
    }

    public void setDepth_mode(int depth_mode) {
        this.depth_mode = depth_mode;
    }

    public int getSmooth_thr() {
        return smooth_thr;
    }

    public void setSmooth_thr(int smooth_thr) {
        this.smooth_thr = smooth_thr;
    }

    public int getTouch_factor() {
        return touch_factor;
    }

    public void setTouch_factor(int touch_factor) {
        this.touch_factor = touch_factor;
    }

    public int getScale_factor() {
        return scale_factor;
    }

    public void setScale_factor(int scale_factor) {
        this.scale_factor = scale_factor;
    }

    public int getRefer_len() {
        return refer_len;
    }

    public void setRefer_len(int refer_len) {
        this.refer_len = refer_len;
    }

    public int getMerge_factor() {
        return merge_factor;
    }

    public void setMerge_factor(int merge_factor) {
        this.merge_factor = merge_factor;
    }

    public int getSimilar_factor() {
        return similar_factor;
    }

    public void setSimilar_factor(int similar_factor) {
        this.similar_factor = similar_factor;
    }

    public int getTmp_mode() {
        return tmp_mode;
    }

    public void setTmp_mode(int tmp_mode) {
        this.tmp_mode = tmp_mode;
    }

    public int getTmp_thr() {
        return tmp_thr;
    }

    public void setTmp_thr(int tmp_thr) {
        this.tmp_thr = tmp_thr;
    }

    public int getCalWeightMap() {
        return calWeightMap;
    }

    public void setCalWeightMap(int calWeightMap) {
        this.calWeightMap = calWeightMap;
    }

    public int getIspTunning() {
        return ispTunning;
    }

    public void setIspTunning(int ispTunning) {
        this.ispTunning = ispTunning;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getBlurFlag() {
        return blurFlag;
    }

    public void setBlurFlag(String blurFlag) {
        this.blurFlag = blurFlag;
    }

    @Override
    public String toString() {
        return "TwoFrameBlurData{" +
                "\ntmp_coeff=" + Arrays.toString(tmp_coeff) +
                ", \nsimilar_coeff=" + Arrays.toString(similar_coeff) +
                ", \nvfir_coeff=" + Arrays.toString(vfir_coeff) +
                ", \nhfir_coeff=" + Arrays.toString(hfir_coeff) +
                ", \nenable=" + enable +
                ", \nfir_mode=" + fir_mode +
                ", \nfir_len=" + fir_len +
                ", \nfir_channel=" + fir_channel +
                ", \nfir_cal_mode=" + fir_cal_mode +
                ", \nfir_edge_factor=" + fir_edge_factor +
                ", \ndepth_mode=" + depth_mode +
                ", \nsmooth_thr=" + smooth_thr +
                ", \ntouch_factor=" + touch_factor +
                ", \nscale_factor=" + scale_factor +
                ", \nrefer_len=" + refer_len +
                ", \nmerge_factor=" + merge_factor +
                ", \nsimilar_factor=" + similar_factor +
                ", \ntmp_mode=" + tmp_mode +
                ", \ntmp_thr=" + tmp_thr +
                ", \nrotation=" + rotation +
                ", \nyuvWidth=" + yuvWidth +
                ", \nyuvHeight=" + yuvHeight +
                ", \nblurIntensity=" + blurIntensity +
                ", \nsel_x=" + sel_x +
                ", \nsel_y=" + sel_y +
                ", \ncalWeightMap=" + calWeightMap +
                ", \nispTunning=" + ispTunning +
                ", \nversion=" + version +
                ", \nblurFlag='" + blurFlag + '\'' +
                '}';
    }
}
