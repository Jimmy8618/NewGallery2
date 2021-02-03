package com.sprd.refocus;

import android.util.Log;

import com.adobe.xmp.XMPException;
import com.adobe.xmp.XMPMeta;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class GDepthData {
    private static final String TAG = "GDepthData";
    private static final String XMP_GCAMERA = "http://ns.google.com/photos/1.0/camera/";
    private static final String XMP_DEPTHMAP = "http://ns.google.com/photos/1.0/depthmap/";
    private static final String XMP_GIAMGE = "http://ns.google.com/photos/1.0/image/";

    private String mFormat;
    private double mFar;
    private double mNear;
    private byte[] mDepthData;
    private byte[] mGImageData;
    private double mDepthWidth;
    private double mDepthHeight;

    public static GDepthData newGDepthData(String filepath) {
        Log.d(TAG, "newGDepthData from:" + filepath);
        if (!filepath.toLowerCase().endsWith(".jpg")
                && !filepath.toLowerCase().endsWith(".jpeg")) {
            Log.d(TAG, "XMP parse: only jpeg file is supported");
            return null;
        }
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(filepath);
            return newGDepthData(inputStream);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Could not read file: " + filepath, e);
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static GDepthData newGDepthData(InputStream is) {
        GDepthData gDepthData = new GDepthData();
        XMPMeta xmpMeta = XMPUtils.read(is, false);
        try {
            gDepthData.setmFormat(
                    xmpMeta.getPropertyString(XMP_DEPTHMAP, "GDepth:Format"));
            gDepthData.setmFar(
                    Double.parseDouble(xmpMeta.getPropertyString(XMP_DEPTHMAP, "GDepth:Far")));
            gDepthData.setmNear(
                    Double.parseDouble(xmpMeta.getPropertyString(XMP_DEPTHMAP, "GDepth:Near")));
            gDepthData.setmDepthData(xmpMeta.getPropertyBase64(XMP_DEPTHMAP, "GDepth:Data"));
            gDepthData.setmDepthWidth(
                    Double.parseDouble(xmpMeta.getPropertyString(XMP_DEPTHMAP, "GDepth:ImageWidth")));
            gDepthData.setmDepthHeight(
                    Double.parseDouble(xmpMeta.getPropertyString(XMP_DEPTHMAP, "GDepth:ImageHeight")));
            gDepthData.setmGImageData(xmpMeta.getPropertyBase64(XMP_GIAMGE, "GImage:Data"));

        } catch (XMPException e) {
            Log.d(TAG, "newGDepthData fail! ");
            e.printStackTrace();
        }
        return gDepthData;
    }

    public static String getXmpGcamera() {
        return XMP_GCAMERA;
    }

    public static String getXmpDepthmap() {
        return XMP_DEPTHMAP;
    }

    public static String getXmpGiamge() {
        return XMP_GIAMGE;
    }

    public String getmFormat() {
        return mFormat;
    }

    public void setmFormat(String mFormat) {
        this.mFormat = mFormat;
    }

    public double getmFar() {
        return mFar;
    }

    public void setmFar(double mFar) {
        this.mFar = mFar;
        Log.i(TAG, "setmFar mFar = " + mFar);
    }

    public double getmNear() {
        return mNear;
    }

    public void setmNear(double mNear) {
        this.mNear = mNear;
        Log.i(TAG, "setmNear mNear = " + mNear);
    }

    public byte[] getmDepthData() {
        return mDepthData;
    }

    public void setmDepthData(byte[] mDepthData) {
        this.mDepthData = mDepthData;
        Log.i(TAG, "setmFar mDepthData = " + mDepthData.length);
    }

    public byte[] getmGImageData() {
        return mGImageData;
    }

    public void setmGImageData(byte[] mGImageData) {
        this.mGImageData = mGImageData;
    }

    public double getmDepthWidth() {
        return mDepthWidth;
    }

    public void setmDepthWidth(double mDepthWidth) {
        this.mDepthWidth = mDepthWidth;
    }

    public double getmDepthHeight() {
        return mDepthHeight;
    }

    public void setmDepthHeight(double mDepthHeight) {
        this.mDepthHeight = mDepthHeight;
    }

    @Override
    public String toString() {
        return "GDepthData:{" +
                "\nmFormat:" + mFormat +
                "\nmFar:" + mFar +
                "\nmNear:" + mNear +
                "\nmDepthWidth:" + mDepthWidth +
                "\nmDepthHeight:" + mDepthHeight +
                "\nmDepthData size:" + (mDepthData == null ? 0 : mDepthData.length) +
                "\nmGImageData size:" + (mGImageData == null ? 0 : mGImageData.length) +
                "}";
    }
}
