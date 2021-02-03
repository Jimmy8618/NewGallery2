package com.sprd.refocus;

import android.graphics.Point;
import android.util.Log;

import com.sprd.frameworks.StandardFrameworks;
import com.sprd.refocus.blur.CommonBlur;
import com.sprd.refocus.blur.FaceBlurData;
import com.sprd.refocus.blur.FaceBlurNewData;
import com.sprd.refocus.blur.GaussBlurData;
import com.sprd.refocus.blur.GaussBlurNewData;
import com.sprd.refocus.blur.TwoFrameBlur;
import com.sprd.refocus.blur.TwoFrameBlurData;
import com.sprd.refocus.blur.TwoFrameBlurNewData;
import com.sprd.refocus.bokeh.SprdRealBokeh;
import com.sprd.refocus.bokeh.SprdRealBokehJpegData;
import com.sprd.refocus.bokeh.SprdRealBokehNormalData;
import com.sprd.refocus.bokeh.SprdRealBokehSbsData;

public abstract class CommonRefocus {
    private static final String TAG = "CommonRefocus";
    private static final String BOKEH_FLAG = "BOKE";
    private static final String BLUR_FLAG = "BLUR";

    protected long mNativeContext; // accessed by native methods
    protected long mNativeDstBuffer; // accessed by native methods
    protected static boolean SBS_SR_ENABLE = false;
    protected static boolean NEW_BOKEH_DATA = true;
    protected static boolean NEW_BLUR_DATA = true;
    protected int mType;
    protected RefocusData mData;
    protected boolean mNeedSaveSr = false;

    /*
    目前blur1.0  blur3.0  blur1.2 三种; bokeh一种
    四个字节表示version.可取范围0xffffffff
      前8位：表示内部版本叠加。
      8-16：表示次版本
      16-24：表示主版本
      定义： 主版本对应相应id：

    主版本	   次版本	   内部版本	version
    blur(24)      blur1.0 ：1	1	0x180101
                  blur1.2： 2	1	0x180201
                  blur3.0 ：3	1	0x180301
    bokeh(28)     bokeh 自研算法 1	1	0x1C0101
    */
    private static int BLUR_DATA_FLAG = 24;
    private static int GB_MINOR_VERSION = 1;//GaussBlurData
    private static int GB_INTERNAL_VERSION = 2;
    private static int FB_MINOR_VERSION = 2;//FaceBlurData
    private static int FB_INTERNAL_VERSION = 2;
    private static int TF_MINOR_VERSION = 3;  // TwoFrameBlurData
    private static int TF_INTERNAL_VERSION = 1;

    private static int BOKEH_DATA_FLAG = 28;//SprdRealBokehJpegData
    private static int BOKEH_MINOR_VERSION = 1;
    private static int BOKEH_INTERNAL_VERSION = 1;

    private static int GDEPTH_BOKEH_DATA_FLAG = 29;//SprdGdepthJpegData
    private static int GDEPTH_BOKEH_MINOR_VERSION = 1;
    private static int GDEPTH_BOKEH_INTERNAL_VERSION = 1;

    public CommonRefocus(RefocusData data) {
        mData = data;
        mType = data.getType();
        Log.d(TAG, "mType = " + mType);
    }

    public static CommonRefocus getInstance(byte[] content) {
        SBS_SR_ENABLE = RefocusUtils.isNeedSR();
        Log.d(TAG, "SBS_SR_ENABLE = " + SBS_SR_ENABLE +
                ", \nSUPPORT_HW_CODEC = " + StandardFrameworks.getInstances().isSupportHwCodec());
        return initRefocusType(content);
    }

    private static CommonRefocus initRefocusType(byte[] content) {
        Log.d(TAG, "initRefocusType start");
        CommonRefocus instance = null;
        String typeString = RefocusUtils.getStringValue(content, 1);
        Log.d(TAG, "typeString = " + typeString);
        int version = RefocusUtils.getIntValue(content, 2);
        int mainVersion = (version >> 16 & 0xFF) + (version >> 24 & 0xFF);
        int minorVersion = version >> 8 & 0xFF;
        int internalVersion = version & 0xFF;
        Log.i(TAG, "initRefocusType mainVersion =   " + mainVersion + " minorVersion = " + minorVersion
                + " internalVersion = " + internalVersion + "  version = " + version);
        if (BLUR_FLAG.equalsIgnoreCase(typeString) || mainVersion == BLUR_DATA_FLAG) {
            if (minorVersion == TF_MINOR_VERSION && internalVersion == TF_INTERNAL_VERSION) {
                if (NEW_BLUR_DATA) {
                    TwoFrameBlurNewData tfNewData = new TwoFrameBlurNewData(content);
                    Log.d(TAG, "new two frame data is : " + tfNewData.toString());
                    instance = new TwoFrameBlur(tfNewData);
                } else {
                    TwoFrameBlurData tfData = new TwoFrameBlurData(content);
                    Log.d(TAG, "two frame data is : " + tfData.toString());
                    instance = new TwoFrameBlur(tfData);
                }
            } else {
                if (minorVersion == FB_MINOR_VERSION && internalVersion == FB_INTERNAL_VERSION) {
                    if (NEW_BLUR_DATA) {
                        FaceBlurNewData faceNewData = new FaceBlurNewData(content);
                        Log.d(TAG, "new face data is : " + faceNewData.toString());
                        instance = new CommonBlur(faceNewData);
                    } else {
                        FaceBlurData faceData = new FaceBlurData(content);
                        Log.d(TAG, "face data is : " + faceData.toString());
                        instance = new CommonBlur(faceData);
                    }
                } else if (minorVersion == GB_MINOR_VERSION && internalVersion == GB_INTERNAL_VERSION) {
                    if (NEW_BLUR_DATA) {
                        GaussBlurNewData gaussNewData = new GaussBlurNewData(content);
                        Log.d(TAG, "new gauss data is : " + gaussNewData.toString());
                        instance = new CommonBlur(gaussNewData);
                    } else {
                        GaussBlurData gaussData = new GaussBlurData(content);
                        Log.d(TAG, "gauss data is : " + gaussData.toString());
                        instance = new CommonBlur(gaussData);
                    }
                }
            }
        } else if (BOKEH_FLAG.equalsIgnoreCase(typeString) && mainVersion == BOKEH_DATA_FLAG) {
            if (minorVersion == BOKEH_MINOR_VERSION && internalVersion == BOKEH_INTERNAL_VERSION) {
                if (SBS_SR_ENABLE) {
                    SprdRealBokehSbsData sprdSbsData = new SprdRealBokehSbsData(content);
                    Log.d(TAG, "sbs data is : " + sprdSbsData.toString());
                    instance = new SprdRealBokeh(sprdSbsData);
                } else {
                    if (NEW_BOKEH_DATA) {
                        SprdRealBokehJpegData sprdJpegData = new SprdRealBokehJpegData(content);
                        Log.d(TAG, "new sprd data is : " + sprdJpegData.toString());
                        instance = new SprdRealBokeh(sprdJpegData);
                    } else {
                        SprdRealBokehNormalData sprdData = new SprdRealBokehNormalData(content);
                        Log.d(TAG, "sprd data is : " + sprdData.toString());
                        instance = new SprdRealBokeh(sprdData);
                    }
                }
            }
        } else if (BOKEH_FLAG.equalsIgnoreCase(typeString) && mainVersion == GDEPTH_BOKEH_DATA_FLAG) {
            if (minorVersion == GDEPTH_BOKEH_MINOR_VERSION && internalVersion == GDEPTH_BOKEH_INTERNAL_VERSION) {
                SprdGdepthJpegData sprdGdepthData = new SprdGdepthJpegData(content);
                Log.d(TAG, "new sprd data is : " + sprdGdepthData.toString());
                instance = new SprdRealBokeh(sprdGdepthData);
            }
        }
        /* Currently, There is no altek refocus picture on android8.0
        else {
            AltekRefocusData altekData = new AltekRefocusData(content);
            Log.d(TAG, "altek refocus data is : " + altekData.toString());
            instance = new AltekRefocus(altekData);
        }
        */
        Log.d(TAG, "instance = " + instance);
        return instance;
    }

    // Debug, dump yuv, depth, params.
    public void dumpData(String filePath) {
        String dumpPath = RefocusUtils.getDumpPath(filePath);
        Log.d(TAG, "debug dumpData, dumpPath " + dumpPath);
        RefocusUtils.writeByteData(mData.getOriJpeg(), "ori.jpg", dumpPath);
        RefocusUtils.writeByteData(mData.getMainYuv(), "mainYuv.yuv", dumpPath);
        RefocusUtils.writeByteData(mData.getDepthData(), "depth.data", dumpPath);
        RefocusUtils.writeByteData(mData.toString().getBytes(), "Params.txt", dumpPath);
    }

    public int getType() {
        return mType;
    }

    public boolean isNeedSaveSr() {
        return mNeedSaveSr;
    }

    public void setMainYuv(byte[] mainYuv) {
        mData.setMainYuv(mainYuv);
    }

    public void setDepth(byte[] depth) {
        mData.setDepthData(depth);
    }

    public RefocusData getRefocusData() {
        return mData;
    }

    // init lib
    public abstract void initLib();

    // unInit lib
    public abstract void unInitLib();

    // calculate distance
    public abstract int distance();

    // do refocus
    public abstract byte[] doRefocus(byte[] editYuv, Point point, int blurIntensity);

    // some refocus no depth,need use two yuv to calculate depth
    public abstract void calDepth();

    // calculate progress by Original blur intensity
    public abstract int getProgress();

    // calculate blur intensity by progress
    public abstract int calBlurIntensity(int progress);

    // depth Rotate
    public abstract int doDepthRotate(byte depth[], int width, int height, int angle);
}
