package com.sprd.refocus;

import android.util.Log;

public abstract class RefocusData {

    public static final int TYPE_REFOCUS = 1 << 0;
    public static final int TYPE_BOKEH_SPRD = 1 << 1;
    public static final int TYPE_BOKEH_ARC = 1 << 2;
    public static final int TYPE_BOKEH_SBS = 1 << 3;
    public static final int TYPE_BLUR_TF = 1 << 4;
    public static final int TYPE_BLUR_FACE = 1 << 5;
    public static final int TYPE_BLUR_GAUSS = 1 << 6;
    public static final String TAG = "RefocusData";

    protected int type;
    protected int jpegSize;
    protected byte[] mainYuv;  // main yuv data
    protected int mainYuvSize; // main yuv data szie
    protected int yuvWidth; // main yuv width
    protected int yuvHeight; // main yuv height
    protected byte[] depthData;
    protected int depthSize;
    protected int depthWidth;
    protected int depthHeight;
    protected int rotation;
    protected int sel_x;
    protected int sel_y;
    protected int blurIntensity;
    protected byte[] oriJpeg;   //for new refocus data, new blur1.x data, new blur3.0 data(cal weightMap in camera)
    protected byte[] farJpeg;   //for new blur3.0 data(cal weightMap in galley)
    protected byte[] nearJpeg;  //for new blur3.0 data(cal weightMap in galley)
    protected int oriJpegSize;
    protected int decryptMode;

    public RefocusData(byte[] content, int type) {
        this.type = type;
        initDatas(content);
        checkRotation();
        checkDataSize(mainYuvSize);
        checkDataSize(oriJpegSize);
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getJpegSize() {
        return jpegSize;
    }

    public void setJpegSize(int jpegSize) {
        this.jpegSize = jpegSize;
    }

    public byte[] getMainYuv() {
        return mainYuv;
    }

    public void setMainYuv(byte[] mainYuv) {
        this.mainYuv = mainYuv;
    }

    public int getMainYuvSize() {
        return mainYuvSize;
    }

    public void setMainYuvSize(int mainYuvSize) {
        this.mainYuvSize = mainYuvSize;
    }

    public int getYuvWidth() {
        return yuvWidth;
    }

    public void setYuvWidth(int yuvWidth) {
        this.yuvWidth = yuvWidth;
    }

    public int getYuvHeight() {
        return yuvHeight;
    }

    public void setYuvHeight(int yuvHeight) {
        this.yuvHeight = yuvHeight;
    }

    public byte[] getDepthData() {
        return depthData;
    }

    public void setDepthData(byte[] depthData) {
        this.depthData = depthData;
    }

    public int getDepthSize() {
        return depthSize;
    }

    public void setDepthSize(int depthSize) {
        this.depthSize = depthSize;
    }

    public int getDepthWidth() {
        return depthWidth;
    }

    public void setDepthWidth(int depthWidth) {
        this.depthWidth = depthWidth;
    }

    public int getDepthHeight() {
        return depthHeight;
    }

    public void setDepthHeight(int depthHeight) {
        this.depthHeight = depthHeight;
    }

    public int getRotation() {
        return rotation;
    }

    public void setRotation(int rotation) {
        this.rotation = rotation;
    }

    public int getSel_x() {
        return sel_x;
    }

    public void setSel_x(int sel_x) {
        this.sel_x = sel_x;
    }

    public int getSel_y() {
        return sel_y;
    }

    public void setSel_y(int sel_y) {
        this.sel_y = sel_y;
    }

    public int getBlurIntensity() {
        return blurIntensity;
    }

    public void setBlurIntensity(int blurIntensity) {
        this.blurIntensity = blurIntensity;
    }

    public byte[] getOriJpeg() {
        return oriJpeg;
    }

    public void setOriJpeg(byte[] oriJpeg) {
        this.oriJpeg = oriJpeg;
    }

    public byte[] getFarJpeg() {
        return farJpeg;
    }

    public void setFarJpeg(byte[] farJpeg) {
        this.farJpeg = farJpeg;
    }

    public byte[] getNearJpeg() {
        return nearJpeg;
    }

    public void setNearJpeg(byte[] nearJpeg) {
        this.nearJpeg = nearJpeg;
    }

    public int getDecryptMode() {
        return decryptMode;
    }

    @Override
    public abstract String toString();

    public abstract void initDatas(byte[] content);

    protected void checkDataSize(int dataSize) {
        if (dataSize == 0) {
            return;
        }
        if (dataSize < 0 || (dataSize > (yuvWidth * yuvHeight) * 3 / 2)) {
            Log.w(TAG, "error data is: " + toString());
            throw new IllegalArgumentException("data error ! dataSize = " + dataSize);
        }
    }

    protected void checkRotation() {
        if (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270) {
            Log.w(TAG, "error data is: " + toString());
            throw new IllegalArgumentException("rotation error ! rotation = " + rotation);
        }
    }


}
