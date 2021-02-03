package com.sprd.refocus.blur;

import android.util.Log;

import com.sprd.refocus.RefocusData;
import com.sprd.refocus.RefocusUtils;

/**
 * Blur data :
 * <p>
 * blur1.0,2.0 -> Gaussian Blur
 * blur1.2 -> Face Blur
 */

/*
1）mJpegImageData：拍照后得到的主senor的YUV数 据转化为的jpeg数据，用 于显示图片
2）outWeightMap：(just for blur1.2)
仅当blur1.2时有此数据，blur1.2拍照时使用算法库计算的outWeightMap， 大小为（width/Scalingratio）*（height /Scalingratio）*sizeof(unsigned short)
3）原始yuv：size是width * height *3/ 2
4）参数---所有参数都转化为4位byte数组
其中背景灰色的参数仅限2.0使用，这里依然保留 (1-10,11-20,71-79,82-96 for blur 2.0)
1~10：cali_dist_seq[] 的10值，转 化为的4位byte数组
11～20：cali_dac_seq[] 的10值，转 化为的4位byte数组
21~30：x1[] 的10个坐标值，转化为的4位byte数组
31~40：y1[] 的10个坐标值，转化为 的4位byte数组
41~50：x2[] 的10个 坐标 值，转化为的4位byte数组
51~60：y2[] 的10个 坐标 值，转化为的4位byte数组
61~70：flag[] 的10个坐标值，转化为 的4位byte数组
71~79: win_peak_pos[] 的9个景深值，转化为的4位byte数组
80: Scalingratio
81：SmoothWinSize
82：vcm_dac_up_bound
83：vcm_dac_low_bound
84：vcm_dac_info; 传固定整形值0，图库使用时此指针*vcm_dac_info 赋值为NULL
85：vcm_dac_gain
86：valid_depth_clip
87：method
88：row_num
89：column_num
90：boundary_ratio
91：sel_size
92：valid_depth
93：slope
94：valid_depth_up_bound
95：valid_depth_low_bound
96：cali_seq_len
97：rotation
98：width  -- > yuvWidth
99：height  -- > yuvHeight
100：YUV size（width*height*3/2） -- > mainYuvSize
101：rear_cam_en  使用前置还是后置拍的照片，0：前置，1：后置
102：roi_type
103：f_number  -- > blurIntensity
104：circle_size
105：valid_roi
106：total_roi
107：min_slope  传给lib时要转 化为float除 以10000
108：max_slope   传给lib时要转 化为float除 以10000
109：findex2gamma_adjust_ratio  传给lib时要转 化为float除 以10000
110：sel_x  -> sel_x
111：sel_y  -> sel_y
112：scaleSmoothWidth（width的 1/8，以前版 本用的，目前没用，保留）
113：scaleSmoothHeight,（height 的 1/8，以 前版本用的，目前没用，保留）
114：box_filter_size
115：version（blur1.0，blur1.1，blur1.2是1，blur2.0是2，blur3.0是3）
116：blurFlag（“BLUR”）

platform ID define
#define PLATFORM_ID_GENERIC    0x0000
#define PLATFORM_ID_PIKE2      0x0100
#define PLATFORM_ID_SHARKLE    0x0200
#define PLATFORM_ID_SHARKLEP   0x0201
#define PLATFORM_ID_SHARKL3    0x0300
#define PLATFORM_ID_SHARKL5    0x0400
#define PLATFORM_ID_SHARKL5P   0x0401
#define PLATFORM_ID_SHARKL6    0x0500
#define PLATFORM_ID_SHARKL6P   0x0501
#define PLATFORM_ID_ROC1       0x0600
*/

public abstract class CommonBlurData extends RefocusData {

    // just FaceBlurData has weightMap.
    protected byte[] weightMap;
    protected int[] cali_dist_seq;
    protected int[] cali_dac_seq;
    protected int[] x1;
    protected int[] y1;
    protected int[] x2;
    protected int[] y2;
    protected int[] flag;
    protected int[] win_peak_pos;
    protected int Scalingratio;
    protected int SmoothWinSize;
    protected int vcm_dac_up_bound;
    protected int vcm_dac_low_bound;
    protected int vcm_dac_info;
    protected int vcm_dac_gain;
    protected int valid_depth_clip;
    protected int method;
    protected int row_num;
    protected int column_num;
    protected int boundary_ratio;
    protected int sel_size;
    protected int valid_depth;
    protected int slope;
    protected int valid_depth_up_bound;
    protected int valid_depth_low_bound;
    protected int cali_seq_len;
    protected int rear_cam_en;
    protected int roi_type;
    protected int circle_size;
    protected int valid_roi;
    protected int total_roi;
    protected int min_slope;
    protected int max_slope;
    protected int findex2gamma_adjust_ratio;
    protected int scaleSmoothWidth;
    protected int scaleSmoothHeight;
    protected int box_filter_size;
    protected int version;
    protected int dataVersion; // hal 数据结构图的version
    protected String blurFlag;
    protected int camera_angle;
    protected int mobile_angle;

    protected int weightMapSize;
    protected int weight_height;
    protected int weight_width;

    protected int platformId; //平台id

    public CommonBlurData(byte[] content, int type) {
        super(content, type);
    }

    protected void initCommonBlurData(byte[] content) {
        blurFlag = RefocusUtils.getStringValue(content, 1); // 1 blurFlag
        dataVersion = RefocusUtils.getIntValue(content, 2);
        version = RefocusUtils.getIntValue(content, 3); //3 version
        platformId = RefocusUtils.getIntValue(content, 4);
        box_filter_size = RefocusUtils.getIntValue(content, 5); //4 box_filter_size
        scaleSmoothHeight = RefocusUtils.getIntValue(content, 6); // 5 scaleSmoothHeight
        scaleSmoothWidth = RefocusUtils.getIntValue(content, 7);   // 6 scaleSmoothWidth
        mobile_angle = RefocusUtils.getIntValue(content, 8);     //camera_angle
        camera_angle = RefocusUtils.getIntValue(content, 9);   //mobile_angle
        sel_y = RefocusUtils.getIntValue(content, 10);  // 6 mSelCoordY
        sel_x = RefocusUtils.getIntValue(content, 11); // 7 mSelCoordX
        findex2gamma_adjust_ratio = RefocusUtils.getIntValue(content, 12); // 8 Findex2Gamma_AdjustRatio
        max_slope = RefocusUtils.getIntValue(content, 13); // 9 mMax_slope
        min_slope = RefocusUtils.getIntValue(content, 14);   //  10 mMin_slope
        total_roi = RefocusUtils.getIntValue(content, 15);  // 11 total_roi : total face number
        valid_roi = RefocusUtils.getIntValue(content, 16);   //  12 valid_roi
        circle_size = RefocusUtils.getIntValue(content, 17); // 13 CircleSize
        blurIntensity = RefocusUtils.getIntValue(content, 18);  // 14 mFNum
        roi_type = RefocusUtils.getIntValue(content, 19);   //  15 roi_type
        weightMapSize = RefocusUtils.getIntValue(content, 20); //weight-size
        weight_height = RefocusUtils.getIntValue(content, 21); //weight-height
        weight_width = RefocusUtils.getIntValue(content, 22); //weight-weight
        rear_cam_en = RefocusUtils.getIntValue(content, 23); // 16 Front or rear? 0 :Front  1 :rear
        mainYuvSize = RefocusUtils.getIntValue(content, 24);  // 17 mMainSizeData
        yuvHeight = RefocusUtils.getIntValue(content, 25);  // 18 mainYuvHeight
        yuvWidth = RefocusUtils.getIntValue(content, 26);   // 19 mainYuvWidth
        rotation = RefocusUtils.getIntValue(content, 27);   // 20 mainYuvWidth
        cali_seq_len = RefocusUtils.getIntValue(content, 28);   // 21 cali_seq_len
        valid_depth_low_bound = RefocusUtils.getIntValue(content, 29);   // 22 valid_depth_low_bound
        valid_depth_up_bound = RefocusUtils.getIntValue(content, 30);   // 23 valid_depth_up_bound
        slope = RefocusUtils.getIntValue(content, 31);   // 24 slope
        valid_depth = RefocusUtils.getIntValue(content, 32);   // 25 valid_depth
        sel_size = RefocusUtils.getIntValue(content, 33);   // 26 sel_size
        boundary_ratio = RefocusUtils.getIntValue(content, 34);   // 27 boundary_ratio
        column_num = RefocusUtils.getIntValue(content, 35);   // 28 column_num
        row_num = RefocusUtils.getIntValue(content, 36);   // 29 row_num
        method = RefocusUtils.getIntValue(content, 37);  // 30 method
        valid_depth_clip = RefocusUtils.getIntValue(content, 38); // 31 valid_depth_clip
        vcm_dac_gain = RefocusUtils.getIntValue(content, 39);// 32 vcm_dac_gain
        vcm_dac_info = RefocusUtils.getIntValue(content, 40);   // 33 vcm_dac_info
        vcm_dac_low_bound = RefocusUtils.getIntValue(content, 41);   // 34 vcm_dac_low_bound
        vcm_dac_up_bound = RefocusUtils.getIntValue(content, 42);   // 35 vcm_dac_up_bound
        SmoothWinSize = RefocusUtils.getIntValue(content, 43); // 36 SmoothWinSize
        Scalingratio = RefocusUtils.getIntValue(content, 44); // 37 Scalingratio

        win_peak_pos = new int[9];
        flag = new int[10];
        y2 = new int[10];
        x2 = new int[10];
        y1 = new int[10];
        x1 = new int[10];
        cali_dac_seq = new int[10];
        cali_dist_seq = new int[10];

        for (int i = 0; i < 9; i++) {
            win_peak_pos[i] = RefocusUtils.getIntValue(content, 53 - i);
        }

        // 10 flag_data,position is 47 - 56
        for (int i = 0; i < 10; i++) {
            flag[i] = RefocusUtils.getIntValue(content, 63 - i);
        }

        // 10 y2_data,position is 57 - 66
        for (int i = 0; i < 10; i++) {
            y2[i] = RefocusUtils.getIntValue(content, 73 - i);
        }

        // 10 x2_data,position is 67 - 76
        for (int i = 0; i < 10; i++) {
            x2[i] = RefocusUtils.getIntValue(content, 83 - i);
        }

        // 10 y1_data,position is 77 - 86
        for (int i = 0; i < 10; i++) {
            y1[i] = RefocusUtils.getIntValue(content, 93 - i);
        }

        // 10 x1_data,position is 87 - 96
        for (int i = 0; i < 10; i++) {
            x1[i] = RefocusUtils.getIntValue(content, 103 - i);
        }

        // 10 cali_dac_seq_data,position is 97 - 106
        for (int i = 0; i < 10; i++) {
            cali_dac_seq[i] = RefocusUtils.getIntValue(content, 113 - i);
        }

        // 10 cali_dist_seq_data,position is 107 - 116
        for (int i = 0; i < 10; i++) {
            cali_dist_seq[i] = RefocusUtils.getIntValue(content, 123 - i);
        }
    }

    public byte[] getWeightMap() {
        return weightMap;
    }

    public void setWeightMap(byte[] weightMap) {
        this.weightMap = weightMap;
    }

    public int[] getCali_dist_seq() {
        return cali_dist_seq;
    }

    public void setCali_dist_seq(int[] cali_dist_seq) {
        this.cali_dist_seq = cali_dist_seq;
    }

    public int[] getCali_dac_seq() {
        return cali_dac_seq;
    }

    public void setCali_dac_seq(int[] cali_dac_seq) {
        this.cali_dac_seq = cali_dac_seq;
    }

    public int[] getX1() {
        return x1;
    }

    public void setX1(int[] x1) {
        this.x1 = x1;
    }

    public int[] getY1() {
        return y1;
    }

    public void setY1(int[] y1) {
        this.y1 = y1;
    }

    public int[] getX2() {
        return x2;
    }

    public void setX2(int[] x2) {
        this.x2 = x2;
    }

    public int[] getY2() {
        return y2;
    }

    public void setY2(int[] y2) {
        this.y2 = y2;
    }

    public int[] getFlag() {
        return flag;
    }

    public void setFlag(int[] flag) {
        this.flag = flag;
    }

    public int[] getWin_peak_pos() {
        return win_peak_pos;
    }

    public void setWin_peak_pos(int[] win_peak_pos) {
        this.win_peak_pos = win_peak_pos;
    }

    public int getScalingratio() {
        return Scalingratio;
    }

    public void setScalingratio(int scalingratio) {
        Scalingratio = scalingratio;
    }

    public int getSmoothWinSize() {
        return SmoothWinSize;
    }

    public void setSmoothWinSize(int smoothWinSize) {
        SmoothWinSize = smoothWinSize;
    }

    public int getVcm_dac_up_bound() {
        return vcm_dac_up_bound;
    }

    public void setVcm_dac_up_bound(int vcm_dac_up_bound) {
        this.vcm_dac_up_bound = vcm_dac_up_bound;
    }

    public int getVcm_dac_low_bound() {
        return vcm_dac_low_bound;
    }

    public void setVcm_dac_low_bound(int vcm_dac_low_bound) {
        this.vcm_dac_low_bound = vcm_dac_low_bound;
    }

    public int getVcm_dac_info() {
        return vcm_dac_info;
    }

    public void setVcm_dac_info(int vcm_dac_info) {
        this.vcm_dac_info = vcm_dac_info;
    }

    public int getVcm_dac_gain() {
        return vcm_dac_gain;
    }

    public void setVcm_dac_gain(int vcm_dac_gain) {
        this.vcm_dac_gain = vcm_dac_gain;
    }

    public int getValid_depth_clip() {
        return valid_depth_clip;
    }

    public void setValid_depth_clip(int valid_depth_clip) {
        this.valid_depth_clip = valid_depth_clip;
    }

    public int getMethod() {
        return method;
    }

    public void setMethod(int method) {
        this.method = method;
    }

    public int getRow_num() {
        return row_num;
    }

    public void setRow_num(int row_num) {
        this.row_num = row_num;
    }

    public int getColumn_num() {
        return column_num;
    }

    public void setColumn_num(int column_num) {
        this.column_num = column_num;
    }

    public int getBoundary_ratio() {
        return boundary_ratio;
    }

    public void setBoundary_ratio(int boundary_ratio) {
        this.boundary_ratio = boundary_ratio;
    }

    public int getSel_size() {
        return sel_size;
    }

    public void setSel_size(int sel_size) {
        this.sel_size = sel_size;
    }

    public int getValid_depth() {
        return valid_depth;
    }

    public void setValid_depth(int valid_depth) {
        this.valid_depth = valid_depth;
    }

    public int getSlope() {
        return slope;
    }

    public void setSlope(int slope) {
        this.slope = slope;
    }

    public int getValid_depth_up_bound() {
        return valid_depth_up_bound;
    }

    public void setValid_depth_up_bound(int valid_depth_up_bound) {
        this.valid_depth_up_bound = valid_depth_up_bound;
    }

    public int getValid_depth_low_bound() {
        return valid_depth_low_bound;
    }

    public void setValid_depth_low_bound(int valid_depth_low_bound) {
        this.valid_depth_low_bound = valid_depth_low_bound;
    }

    public int getCali_seq_len() {
        return cali_seq_len;
    }

    public void setCali_seq_len(int cali_seq_len) {
        this.cali_seq_len = cali_seq_len;
    }

    public int getRear_cam_en() {
        return rear_cam_en;
    }

    public void setRear_cam_en(int rear_cam_en) {
        this.rear_cam_en = rear_cam_en;
    }

    public int getRoi_type() {
        return roi_type;
    }

    public void setRoi_type(int roi_type) {
        this.roi_type = roi_type;
    }

    public int getCircle_size() {
        return circle_size;
    }

    public void setCircle_size(int circle_size) {
        this.circle_size = circle_size;
    }

    public int getValid_roi() {
        return valid_roi;
    }

    public void setValid_roi(int valid_roi) {
        this.valid_roi = valid_roi;
    }

    public int getTotal_roi() {
        return total_roi;
    }

    public void setTotal_roi(int total_roi) {
        this.total_roi = total_roi;
    }

    public int getMin_slope() {
        return min_slope;
    }

    public void setMin_slope(int min_slope) {
        this.min_slope = min_slope;
    }

    public int getMax_slope() {
        return max_slope;
    }

    public void setMax_slope(int max_slope) {
        this.max_slope = max_slope;
    }

    public int getFindex2gamma_adjust_ratio() {
        return findex2gamma_adjust_ratio;
    }

    public void setFindex2gamma_adjust_ratio(int findex2gamma_adjust_ratio) {
        this.findex2gamma_adjust_ratio = findex2gamma_adjust_ratio;
    }

    public int getScaleSmoothWidth() {
        return scaleSmoothWidth;
    }

    public void setScaleSmoothWidth(int scaleSmoothWidth) {
        this.scaleSmoothWidth = scaleSmoothWidth;
    }

    public int getScaleSmoothHeight() {
        return scaleSmoothHeight;
    }

    public void setScaleSmoothHeight(int scaleSmoothHeight) {
        this.scaleSmoothHeight = scaleSmoothHeight;
    }

    public int getBox_filter_size() {
        return box_filter_size;
    }

    public void setBox_filter_size(int box_filter_size) {
        this.box_filter_size = box_filter_size;
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

    public int getCamera_angle() {
        return camera_angle;
    }

    public int getMobile_angle() {
        return mobile_angle;
    }

    public int getPlatformId() {
        return platformId;
    }

}
