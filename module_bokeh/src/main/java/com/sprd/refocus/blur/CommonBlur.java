package com.sprd.refocus.blur;

import android.graphics.Point;
import android.util.Log;

import com.sprd.frameworks.StandardFrameworks;
import com.sprd.refocus.CommonRefocus;
import com.sprd.refocus.RefocusUtils;

public class CommonBlur extends CommonRefocus {
    private static final String TAG = "CommonBlur";
    private static boolean mSupportCommonBlur = true;

    static {
        try {
            System.loadLibrary("jni_sprd_blur");
        } catch (UnsatisfiedLinkError error) {
            mSupportCommonBlur = false;
            Log.d(TAG, " load jni_sprd_blur failed");
            error.printStackTrace();
        }
    }

    public CommonBlur(CommonBlurData data) {
        super(data);
        initData(data);
    }

    public static boolean isSupportCommonblur() {
        return mSupportCommonBlur;
    }

    private void initData(CommonBlurData data) {
        byte[] oriJpeg = data.getOriJpeg();
        if (StandardFrameworks.getInstances().isSupportHwCodec()) {
            setMainYuv(RefocusUtils.hwJpeg2yuv(oriJpeg));
        } else {
            setMainYuv(RefocusUtils.jpeg2yuv(oriJpeg));
        }
    }

    @Override
    public void initLib() {
        mNeedSaveSr = false;
        doInitLib((CommonBlurData) mData);
    }

    @Override
    public void unInitLib() {
        iSmoothCapDeinit();
    }

    @Override
    public int distance() {
        // no distance feature
        return 0;
    }

    @Override
    public byte[] doRefocus(byte[] editYuv, Point point, int blurIntensity) {
        editYuv = blurRefocus((CommonBlurData) mData, point, blurIntensity);
        return editYuv;
    }

    @Override
    public void calDepth() {
        // don't need calculate depth
    }

    @Override
    public int getProgress() {
        //BlurIntensity(F_number):[2,20] -> [1,10]*2
        int orgProgress = (mData.getBlurIntensity() * 10 / 20) - 1; //[0,9]
        return orgProgress;
    }

    @Override
    public int calBlurIntensity(int progress) {
        //progress:0-9, libF:0-20, hal to gallery:[2-20]
        int blurIntensity = (progress + 1) * 20 / 10;//[2-20],2 is max blur
        return blurIntensity;
    }

    @Override
    public int doDepthRotate(byte[] depth, int width, int height, int angle) {
        return 0;
    }


    private void doInitLib(CommonBlurData data) {
        float min_slope = (float) data.getMin_slope() / (float) 10000;
        float max_slope = (float) data.getMax_slope() / (float) 10000;
        float Findex2Gamma_AdjustRatio = (float) data.getFindex2gamma_adjust_ratio() / (float) 10000;
        iSmoothCapInit(
                data.getYuvWidth(),
                data.getYuvHeight(),
                min_slope,
                max_slope,
                Findex2Gamma_AdjustRatio,
                data.getScalingratio(),
                data.getSmoothWinSize(),
                data.getBox_filter_size(),
                data.getVcm_dac_up_bound(),
                data.getVcm_dac_low_bound(),
                data.getVcm_dac_info(),
                data.getVcm_dac_gain(),
                data.getValid_depth_clip(),
                data.getMethod(),
                data.getRow_num(),
                data.getColumn_num(),
                data.getBoundary_ratio(),
                data.getSel_size(),
                data.getValid_depth(),
                data.getSlope(),
                data.getValid_depth_up_bound(),
                data.getValid_depth_low_bound(),
                data.getCali_dist_seq(),
                data.getCali_dac_seq(),
                data.getCali_seq_len(),
                data.getPlatformId());
    }

    private byte[] blurRefocus(CommonBlurData data, Point point, int blurIntensity) {
        return iSmoothCapBlurImage(
                data.getMainYuv(),
                data.getWeightMap(), //if GaussBlur, it is null
                data.getRear_cam_en(),
                data.getVersion(),
                data.getRoi_type(),
                blurIntensity,
                point.x,
                point.y,
                data.getWin_peak_pos(),
                data.getCircle_size(),
                data.getTotal_roi(),
                data.getValid_roi(),
                data.getX1(),
                data.getY1(),
                data.getX2(),
                data.getY2(),
                data.getFlag(),
                data.getRotation(),
                data.getCamera_angle(),
                data.getMobile_angle());
    }

    private native void iSmoothCapInit(
            int width,
            int height,
            float min_slope,
            float max_slope,
            float Findex2Gamma_AdjustRatio,
            int Scalingratio,
            int SmoothWinSize,
            int box_filter_size,
            int vcm_dac_up_bound,
            int vcm_dac_low_bound,
            int vcm_dac_info,
            int vcm_dac_gain,
            int valid_depth_clip,
            int method,
            int row_num,
            int column_num,
            int boundary_ratio,
            int sel_size,
            int valid_depth,
            int slope,
            int valid_depth_up_bound,
            int valid_depth_low_bound,
            int[] cali_dist_seq,
            int[] cali_dac_seq,
            int cali_seq_len,
            int platform_id
    );

    public native byte[] iSmoothCapBlurImage(
            byte[] src_yuv_data,
            byte[] weightMap,
            int rear_cam_en,
            int version,
            int roi_type,
            int F_number,
            int sel_x,
            int sel_y,
            int[] win_peak_pos,
            int CircleSize,
            int total_roi,
            int valid_roi,
            int[] x1,
            int[] y1,
            int[] x2,
            int[] y2,
            int[] flag_data,
            int rotate_angle,
            int camera_angle,
            int mobile_angle);

    public native void iSmoothCapDeinit();

}
