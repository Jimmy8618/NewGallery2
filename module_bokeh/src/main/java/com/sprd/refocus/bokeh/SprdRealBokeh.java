package com.sprd.refocus.bokeh;

import android.graphics.Point;
import android.util.Log;

import com.sprd.frameworks.StandardFrameworks;
import com.sprd.refocus.CommonRefocus;
import com.sprd.refocus.RefocusUtils;
import com.sprd.refocus.SprdGdepthJpegData;

public class SprdRealBokeh extends CommonRefocus {
    private static final String TAG = "SprdRealBokeh";
    private int param_state;
    private int saveSrFlag;
    private static boolean mSupportBokeh = true;

    static {
        try {
            System.loadLibrary("jni_sprd_real_bokeh");
        } catch (UnsatisfiedLinkError e) {
            mSupportBokeh = false;
            Log.i(TAG, " load jni_sprd_real_bokeh failed");
            e.printStackTrace();
        }
    }

    public SprdRealBokeh(SprdRealBokehData data) {
        super(data);
        initData(data);
    }

    public static boolean isSupportBokeh() {
        return mSupportBokeh;
    }

    private void initData(SprdRealBokehData data) {
        if (data == null) {
            return;
        }
        param_state = data.getParam_state();
        saveSrFlag = data.getSaveSr();
        if (NEW_BOKEH_DATA && (data instanceof SprdRealBokehJpegData || data instanceof SprdGdepthJpegData)) {
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
        Log.d(TAG, "initLib");
        mNeedSaveSr = false;
        bokehInit(mData.getYuvWidth(), mData.getYuvHeight(), param_state);
        if (SBS_SR_ENABLE && saveSrFlag == 0) {
            // MainYuv need SR processing before bokehReFocusPreProcess.
            SrInit(mData.getYuvWidth(), mData.getYuvHeight());
            Log.d(TAG, "SrProcess start ");
            byte[] srYuv = mData.getMainYuv();
            int srResult = SrProcess(srYuv);
            Log.d(TAG, "SrProcess end and srResult = " + srResult);
            SrDeinit();
            setMainYuv(srYuv);
            saveSrFlag = 1;
            mNeedSaveSr = true;
        }
        bokehReFocusPreProcess(mData.getMainYuv(), mData.getDepthData(), mData.getDepthWidth(),
                mData.getDepthHeight(), mData.getDecryptMode());
    }

    @Override
    public void unInitLib() {
        Log.d(TAG, "unInitLib");
        bokehClose();
    }

    @Override
    public int distance() {
        // TODO : sprd has distance
        return 0;
    }

    @Override
    public byte[] doRefocus(byte[] editYuv, Point point, int blurIntensity) {
        Log.d(TAG, "doRefocus");
        editYuv = bokehReFocusGen(editYuv, blurIntensity, point.x, point.y);
        return editYuv;
    }

    @Override
    public void calDepth() {
        // sprd real bokeh no use.
    }

    @Override
    public int getProgress() {
        //BlurIntensity:[25,255]->[1,10]*25.5
        float progressFloat = ((float) mData.getBlurIntensity() + 0.5f) * 10 / 255;
        int orgProgress = 10 - (int) progressFloat; //[0,9]
        return orgProgress;
    }

    @Override
    public int calBlurIntensity(int progress) {
        // progress:0-9, libF:0-255, hal to gallery:[25-255]
        int blurIntensity = 255 - (progress * 255 / 10);//[255-25],255 is max blur
        return blurIntensity;
    }

    @Override
    public int doDepthRotate(byte[] depth, int width, int height, int angle) {
        return depthRotate(depth, width, height, angle);
    }

    public void SrInit(int yuv_width, int yuv_height) {
        // set default thread_num is 4.
        SrInit(yuv_width, yuv_height, 4);
    }

    public native int bokehInit(int yuv_width, int yuv_height, int param);

    public native int bokehClose();

    public native int bokehReFocusPreProcess(byte[] mainYuv, byte[] depth, int depthW, int depthH, int decrypt_mode);

    public native byte[] bokehReFocusGen(byte[] output_yuv, int a_dInBlurStrength, int a_dInPositionX, int a_dInPositionY);

    public native int distance(int distance_data, byte[] depth, int x1, int y1, int x2, int y2);

    public native void SrInit(int yuv_width, int yuv_height, int thread_num);

    public native int SrProcess(byte[] input_yuv);

    public native int SrDeinit();

    public native int depthRotate(byte depth[], int width, int height, int angle);

}
