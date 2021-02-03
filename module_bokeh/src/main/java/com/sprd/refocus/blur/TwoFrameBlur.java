package com.sprd.refocus.blur;

import android.graphics.Point;
import android.util.Log;

import com.sprd.frameworks.StandardFrameworks;
import com.sprd.refocus.CommonRefocus;
import com.sprd.refocus.RefocusUtils;


public class TwoFrameBlur extends CommonRefocus {
    private static final String TAG = "TwoFrameBlur";
    private boolean needCalDepth;
    private int weightMapSize;
    private byte[] farYuvData;
    private static boolean mSupportTFBlur = true;

    static {
        try {
            System.loadLibrary("jni_sprd_blur");
        } catch (UnsatisfiedLinkError e) {
            mSupportTFBlur = false;
            Log.i(TAG, " load jni_sprd_blur failed");
            e.printStackTrace();
        }
    }

    public TwoFrameBlur(TwoFrameBlurData data) {
        super(data);
        initData(data);
    }

    public static boolean ismSupportTFBlur() {
        return mSupportTFBlur;
    }

    private void initData(TwoFrameBlurData data) {
        if (data == null) {
            return;
        }
        needCalDepth = data.needCalWidgetMap();
        weightMapSize = data.getYuvWidth() * data.getYuvHeight() / 4;
        farYuvData = data.getFarYuvData();
    }

    public TwoFrameBlur(TwoFrameBlurNewData data) {
        super(data);
        initData(data);
    }

    private void initData(TwoFrameBlurNewData data) {
        if (data == null) {
            return;
        }
        needCalDepth = data.needCalWidgetMap();
        weightMapSize = data.getYuvWidth() * data.getYuvHeight() / 4;
        if (needCalDepth) {
            //near and far jpeg
            byte[] nearJpeg = data.getNearJpeg();
            byte[] farJpeg = data.getFarJpeg();
            if (StandardFrameworks.getInstances().isSupportHwCodec()) {
                Log.d(TAG, "hwJpeg2yuv cal near(main) yuv");
                setMainYuv(RefocusUtils.hwJpeg2yuv(nearJpeg));
                Log.d(TAG, "hwJpeg2yuv cal far yuv");
                farYuvData = RefocusUtils.hwJpeg2yuv(farJpeg);
                Log.d(TAG, "hwJpeg2yuv cal yuv end");
            } else {
                Log.d(TAG, "jpeg2yuv cal near(main) yuv");
                setMainYuv(RefocusUtils.jpeg2yuv(nearJpeg));
                Log.d(TAG, "jpeg2yuv cal far yuv");
                farYuvData = RefocusUtils.jpeg2yuv(farJpeg);
                Log.d(TAG, "jpeg2yuv cal yuv end");
            }
        } else {
            byte[] oriJpeg = data.getOriJpeg();
            if (StandardFrameworks.getInstances().isSupportHwCodec()) {
                setMainYuv(RefocusUtils.hwJpeg2yuv(oriJpeg));
            } else {
                setMainYuv(RefocusUtils.jpeg2yuv(oriJpeg));
            }
        }
    }

    @Override
    public void initLib() {
        mNeedSaveSr = false;
        TwoFrameBlurNewData tfData = (TwoFrameBlurNewData) this.mData;
        twoFrameBokehInit(
                tfData.getYuvWidth(),
                tfData.getYuvHeight(),
                tfData.getIspTunning(),
                tfData.getTmp_thr(),
                tfData.getTmp_mode(),
                tfData.getSimilar_factor(),
                tfData.getMerge_factor(),
                tfData.getRefer_len(),
                tfData.getScale_factor(),
                tfData.getTouch_factor(),
                tfData.getSmooth_thr(),
                tfData.getDepth_mode(),
                tfData.getFir_edge_factor(),
                tfData.getFir_cal_mode(),
                tfData.getFir_channel(),
                tfData.getFir_len(),
                tfData.getFir_mode(),
                tfData.getEnable(),
                tfData.getHfir_coeff(),
                tfData.getVfir_coeff(),
                tfData.getSimilar_coeff(),
                tfData.getTmp_coeff());
    }

    @Override
    public void unInitLib() {
        twoFrameBokehDeinit();
    }

    @Override
    public int distance() {
        // no distance feature
        return 0;
    }

    @Override
    public byte[] doRefocus(byte[] editYuv, Point point, int blurIntensity) {
        editYuv = twoFrameBokeh(mData.getMainYuv(), mData.getDepthData(), blurIntensity, point.x, point.y);
        return editYuv;
    }

    @Override
    public void calDepth() {
        if (needCalDepth) {
            byte[] depth = new byte[weightMapSize];
            depth = twoFrameDepthInit(mData.getMainYuv(), farYuvData, depth);
            setDepth(depth);
            farYuvData = null;
        }
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

    private native void twoFrameBokehInit(
            int width,
            int height,
            int isp_tunning,
            int tmp_thr,
            int tmp_mode,
            int similar_factor,
            int merge_factor,
            int refer_len,
            int scale_factor,
            int touch_factor,
            int smooth_thr,
            int depth_mode,
            int fir_edge_factor,
            int fir_cal_mode,
            int fir_channel,
            int fir_len,
            int fir_mode,
            int enable,
            int[] hfir_coeff,
            int[] vfir_coeff,
            int[] similar_coeff,
            int[] tmp_coeff);

    private native byte[] twoFrameBokeh(byte[] src_yuv_data, byte[] weight_map, int F_number, int sel_x, int sel_y);

    private native void twoFrameBokehDeinit();

    private native byte[] twoFrameDepthInit(byte[] near_yuv, byte[] far_yuv, byte[] dis_map);
}
