/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sprd.blending;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Log;

import com.android.gallery3d.common.BitmapUtils;
import com.sprd.blending.bean.Depth;
import com.sprd.blending.bean.ExtractResult;
import com.sprd.blending.bean.Mask;
import com.sprd.blending.bean.UpdateInfo;
import com.sprd.refocus.CommonRefocus;

import java.util.List;

public class ImageBlending extends Blending {

    public static int DEPTH_WIDTH = 324;
    public static int DEPTH_HEIGHT = 243;
    public static int DEPTH_HEADSIZE = 68; //hold depthW and depthH

    public static final int SUCCESS = 0;
    public static final int MEMORY_ALLOCATION_FAILED_OR_OTHER_REASON = -1;
    public static final int TIME_OUT = -2;
    public static final int NOT_FOREGROUND_OBJECT = 1;
    public static final int FOREGROUND_WIDTH_BELOW_ZERO = -3;
    private static String TAG = "ImageBlending";


    static {
        System.loadLibrary("jni_sprd_imageblendings");
    }

    // accessed by native methods
    private int mNativeWidth;
    private int mNativeHeight;

    private int mOutputIconX;
    private int mOutputIconY;

    private int mExtractFg2PtsResultCode;
    private long mNativeContext;

    private boolean mIsDoExtractFg2Pts = false;
    //private static ImageBlending instances;

    public boolean ismIsDoExtractFg2Pts() {
        return mIsDoExtractFg2Pts;
    }

    public void setmIsDoExtractFg2Pts(boolean mIsDoExtractFg2Pts) {
        this.mIsDoExtractFg2Pts = mIsDoExtractFg2Pts;
    }

    private ImageBlending() {
    }

    @Override
    public ExtractResult getCreatemaskResult() {
        ExtractResult extractResult = new ExtractResult();
        extractResult.setResultCode(mExtractFg2PtsResultCode);
        extractResult.setHeight(mNativeHeight);
        extractResult.setWidth(mNativeWidth);
        extractResult.setCoordinateX(mOutputIconX);
        extractResult.setCoordinateY(mOutputIconY);
        return extractResult;
    }

    public static class ObjectInfo {
        public int width;
        public int height;
        public int coordinateX;
        public int coordinateY;

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Object width:").append(width)
                    .append(",height:").append(height)
                    .append("; coordinateX:").append(coordinateX)
                    .append(",coordinateY:").append(coordinateY);
            return builder.toString();
        }
    }

    public static ImageBlending getInstances(CommonRefocus commonRefocus) {
        //if (instances == null) {
        ImageBlending instances = new ImageBlending();
        Log.d(TAG, "java ImageBlending init start !");
        instances.ImageBlendingInit(commonRefocus.getRefocusData().getDecryptMode());
        Log.d(TAG, "java ImageBlending init end  !");
        //}
        return instances;
    }

    @Override
    public void initdepth(Depth mDepth, int rotation, CommonRefocus commonRefocus) {
        int mDepthHeight = mDepth.getmDepthHeight();
        int mDepthWidth = mDepth.getmDepthWidth();
        byte[] bytes = mDepth.getDepth();
        float radio = (float) mDepthHeight / (float) mDepthWidth;

        if (radio == 0.75f && mDepthWidth != DEPTH_WIDTH && mDepthHeight != DEPTH_HEIGHT) {
            bytes = depthScaling(bytes, mDepthWidth, mDepthHeight, DEPTH_WIDTH, DEPTH_HEIGHT, DEPTH_HEADSIZE);
        }
        Log.d(TAG, "initdepth: " + bytes.length);
        if (rotation == 0) {
            mDepthHeight = DEPTH_HEIGHT;
            mDepthWidth = DEPTH_WIDTH;
        } else if (rotation == 90) {
            mDepthHeight = DEPTH_WIDTH;
            mDepthWidth = DEPTH_HEIGHT;
            _ExtractFg_depth_rotate(bytes, DEPTH_WIDTH, DEPTH_HEIGHT, 270);
        } else if (rotation == 180) {
            mDepthHeight = DEPTH_HEIGHT;
            mDepthWidth = DEPTH_WIDTH;
            _ExtractFg_depth_rotate(bytes, DEPTH_WIDTH, DEPTH_HEIGHT, 180);
        } else if (rotation == 270) {
            mDepthHeight = DEPTH_WIDTH;
            mDepthWidth = DEPTH_HEIGHT;
            _ExtractFg_depth_rotate(bytes, DEPTH_WIDTH, DEPTH_HEIGHT, 90);
        }
        mDepth.setDepth(bytes);
        mDepth.setmDepthWidth(mDepthWidth);
        mDepth.setmDepthHeight(mDepthHeight);
    }

    //get data and location of maskImg
    @Override
    public byte[] createmask(Bitmap srcImg, Depth depthImg, int[] rectedgs) {
        Log.d(TAG, "ExtractFg2Pts() called with: srcImg = [" + srcImg + "], depthImg = [" + depthImg + "]," +
                " center_x = [" + rectedgs[0] + "], center_y = [" + rectedgs[1] + "], corner_x = [" + rectedgs[2] + "], corner_y = [" + rectedgs[3] + "]");
        if (mIsDoExtractFg2Pts) {
            Log.d(TAG, "have been do ExtractFg2Pts!");
            return null;
        }
        mIsDoExtractFg2Pts = true;
        Log.d(TAG, "ExtractFg2Pts: set true");
        return _ExtractFg2Pts(srcImg.getWidth(), srcImg.getHeight(), BitmapUtils.toByteArray(srcImg),
                depthImg.getDepth(), rectedgs[0], rectedgs[1], rectedgs[2], rectedgs[3]);
    }

    @Override
    public Mask scaleMask(Mask mMask, int width, int height) {
        /*Mask mask = mMask;
        if (mMask.getmMaskHeight() == width && mMask.getmMaskHeight() == height) {
            return mMask;
        }
        byte[] maskbyte = ExtractFgScaling(mMask.getMask(), mMask.getmMaskWidth(), mMask.getmMaskHeight(), width, height);
        mask.setMask(maskbyte);
        mask.setmMaskWidth(width);
        mask.setmMaskHeight(height);
        return mask;*/
        Mask mask = mMask;
        if (mMask.getmMaskHeight() == width && mMask.getmMaskHeight() == height)
            return mMask;

        byte[] maskbyte = mMask.getMask();
        int srcWidth = mMask.getmMaskWidth();
        int srcHeight = mMask.getmMaskHeight();

        //Set ARGB Data
        byte[] maskSrcARGB = new byte[srcWidth * srcHeight * 4];
        for (int i = 0; i < srcHeight; i++) {
            for (int j = 0; j < srcWidth; j++) {
                int index = i * srcWidth * 4 + j * 4;
                maskSrcARGB[index] = (byte) 0xff;
                maskSrcARGB[index + 1] = maskbyte[i * srcWidth + j];
                maskSrcARGB[index + 2] = maskbyte[i * srcWidth + j];
                maskSrcARGB[index + 3] = maskbyte[i * srcWidth + j];
            }
        }
        float scaleX = (float) width / srcWidth;
        float scaleY = (float) height / srcHeight;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleX, scaleY);
        Bitmap maskBitmap = BitmapUtils.toBitmap(maskSrcARGB, srcWidth, srcHeight);
        //upscale bitmap
        Bitmap scaleUpBitmap = Bitmap.createBitmap(maskBitmap, 0, 0, srcWidth, srcHeight, matrix, false);
        //get scaleUpBitmap data
        byte[] dstUpscaleARGB = BitmapUtils.toByteArray(scaleUpBitmap);
        //get dstMask
        byte[] dstMask = new byte[width * height];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                dstMask[i * width + j] = dstUpscaleARGB[i * width * 4 + j * 4 + 1]; //only get R channel data
            }
        }
        if (!maskBitmap.isRecycled()) {
            maskBitmap.recycle();
        }
        if (!scaleUpBitmap.isRecycled()) {
            scaleUpBitmap.recycle();
        }
        mask.setMask(dstMask);
        mask.setmMaskWidth(width);
        mask.setmMaskHeight(height);
        return mask;

    }

    @Override
    public Bitmap getForgroundBitmap(Bitmap srcImg, byte[] maskImg, int start_x, int start_y, int box_width, int box_height) {
        Log.d(TAG, "getForgroundBitmap() called with: srcImg = [" + srcImg + "], maskImg = [" + maskImg + "], " +
                "start_x = [" + start_x + "], start_y = [" + start_y + "], box_width = [" + box_width + "], box_height = [" + box_height + "]");
        byte[] object = _ExtractFgCreatefbobject(srcImg.getWidth(), srcImg.getHeight(),
                BitmapUtils.toByteArray(srcImg), maskImg, start_x, start_y, box_width, box_height);
        if (object != null) {
            return BitmapUtils.toBitmap(object, mNativeWidth, mNativeHeight);
        }
        return null;
    }

    @Override
    public int saveMask(byte[] mMask) {
        if (mMask != null && mMask.length < 0) {
            Log.d(TAG, "saveMask: mask data error");
            return -2;
        }
        return _ExtractFg_saveUpdate(mMask);
    }

    @Override
    public byte[] updatemask(Bitmap srcImg, byte[] maskImg, List<UpdateInfo> infos) {
        if (!mIsDoExtractFg2Pts) {
            Log.d(TAG, " not do ExtractFg2Pts!");
            return null;
        }
        UpdateInfo updateInfo = infos.get(0);
        return _ExtractFgUpdate(updateInfo.getMovex(), updateInfo.getMovey(), updateInfo.getMovex().length, !updateInfo.isforground());
    }

    @Override
    public int verify_position(byte[] mask, int mask_width, int mask_height, int start_x0, int start_y0,
                               int center_x, int center_y, float zoom, float angle) {
        int i = _verify_icon_pos(mask, mask_width, mask_height, start_x0, start_y0, center_x, center_y, zoom, angle);
        Log.d(TAG, "verify_position: " + i);
        return i;
    }

    public byte[] ExtractFgScaling(byte[] inputdata, int input_width, int input_height,
                                   int output_width, int output_height) {
        return _ExtractFgScaling(inputdata, input_width, input_height, output_width, output_height);
    }

    @Override
    public Bitmap doBlending(Bitmap srcImg, Mask maskImg, Bitmap dstImg,
                             int start_x0, int start_y0, int center_x, int center_y,
                             float scaleFactor, float angle) {
        Log.d(TAG, "java do Blending start !");
        Log.d(TAG, "doBlending() called with:start_x0 = [" + start_x0 + "], " +
                "start_y0 = [" + start_y0 + "], center_x = [" + center_x + "]," +
                " center_y = [" + center_y + "], scaleFactor = [" + scaleFactor + "], angle = [" + angle + "]");
        byte[] data = _doBlending(srcImg.getWidth(), srcImg.getHeight(), BitmapUtils.toByteArray(srcImg),
                srcImg.getWidth(), srcImg.getHeight(), maskImg.getMask(),
                dstImg.getWidth(), dstImg.getHeight(), BitmapUtils.toByteArray(dstImg),
                start_x0, start_y0, center_x, center_y, scaleFactor, angle);
        if (data == null || data.length <= 0) {
            return null;
        }
        return BitmapUtils.toBitmap(data, mNativeWidth, mNativeHeight);
    }

    @Override
    public void destory() {
        _ImageBlendingDeinit();
    }

    @Override
    public byte[] undoUpdate(byte[] mask) {
        if (mask != null && mask.length < 0) {
            Log.d(TAG, "undoUpdate: mask data error");
            return null;
        }
        return _ExtractFg_extractUpdate(mask);
    }

    public byte[] depthScaling(byte[] depthin, int depthinwidth, int deptpinheight, int depthoutwidth, int depthoutheight, int depthHeadSize) {
        Log.d(TAG, "depthScaling() called with: depthin = [" + depthin.length + "], depthinwidth = [" + depthinwidth + "], deptpinheight = [" + deptpinheight + "], depthoutwidth = [" + depthoutwidth + "], depthoutheight = [" + depthoutheight + "], depthHeadSize = [" + depthHeadSize + "]");
        if (depthin == null || depthin.length < 0 || depthin.length != (depthinwidth * deptpinheight * 2)) {
            return null;
        }
        if (depthinwidth <= 0 || deptpinheight <= 0 || depthoutwidth <= 0 || depthoutheight <= 0) {
            return null;
        }
        byte[] bytes = _Extract_depthScaling(depthin, depthinwidth, deptpinheight, depthoutwidth, depthoutheight, depthHeadSize);
        return bytes;
    }

    public void deinit() {
        _ImageBlendingDeinit();
    }

    private native void ImageBlendingInit(int decryptMode);

    private native byte[] _getTargetImg(int src_widht, int src_height, byte[] srcImg,
                                        int mask_widht, int mask_height, byte[] maskImg, float zoom);

    private native byte[] _doBlending(int src_widht, int src_height, byte[] srcImg,
                                      int mask_widht, int mask_height, byte[] maskImg,
                                      int dst_width, int dst_height, byte[] dstImg,
                                      int start_x0, int start_y0, int center_x, int center_y, float scaleFactor, float angle);

    private native void _ImageBlendingDeinit();

    private native byte[] _ExtractFg2Pts(int src_width, int src_height, byte[] srcImg,
                                         byte[] depthImg, int center_x, int center_y,
                                         int corner_x, int corner_y);

    private native byte[] _ExtractFgUpdate(int[] xlocs, int[] ylocs, int num, boolean isBg);

    private native byte[] _ExtractFgScaling(byte[] inputdata, int input_width, int input_height,
                                            int output_width, int output_height);

    private native byte[] _ExtractFgCreatefbobject(int src_widht, int src_height, byte[] srcImg,
                                                   byte[] maskImg, int start_x, int start_y, int box_width, int box_height);

    private native int _verify_icon_pos(byte[] mask_image, int mask_width, int mask_height, int start_x_new, int start_y_new, int center_x0, int center_y0, float zoom, float angle);

    private native int _ExtractFg_saveUpdate(byte[] mask);

    private native byte[] _ExtractFg_extractUpdate(byte[] mask);

    private native byte[] _Extract_depthScaling(byte[] depthin, int depthInWidth, int depthInHeight, int depthOutWidth, int depthOutheight, int depthHeadSize);

    private native int _ExtractFg_depth_rotate(byte depth[], int width, int height, int angle);

}
