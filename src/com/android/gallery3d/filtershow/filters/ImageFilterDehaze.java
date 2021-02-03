package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;
import android.util.Log;

import com.android.gallery3d.util.GalleryUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by rui.li on 2/2/18.
 */

public class ImageFilterDehaze extends ImageFilterFx {

    static {
        System.loadLibrary("jni_sprd_dehaze");
    }

    private static final String TAG = "ImageFilterDehaze";

    public ImageFilterDehaze() {
    }

    @Override
    public Bitmap apply(Bitmap srcBitmap, float scaleFactor, int quality) {
        Log.d(TAG, "apply bitmap:" + srcBitmap.getWidth() + " x " + srcBitmap.getHeight());
        int w = srcBitmap.getWidth();
        int h = srcBitmap.getHeight();
        if (w * h < GalleryUtils.DEHAZE_MAX_SIZE) {
            Log.d(TAG, "apply don't support dehaze for too small picture");
            return srcBitmap;
        }

        byte[] data = toByteArray(srcBitmap);

        // init dehaze
        if (-1 == dehaze_init(w, h)) {
            Log.d(TAG, "fail to init dehaze library !");
            return srcBitmap;
        }

        // dehaze copy data, data can be re-used after copy
        if (-1 == dehaze_copyRGBData(data)) {
            Log.d(TAG, "fail to copy data to dehaze library !");
            return srcBitmap;
        }

        // dehaze process
        if (-1 == dehaze_process(data)) {
            Log.d(TAG, "fail to process dehaze !");
            return srcBitmap;
        }

        Bitmap dstBitmap = toBitmap(data, w, h);
        Log.d(TAG, "apply dstBitmap = " + dstBitmap);

        // dehaze deinit
        if (-1 == dehaze_deinit()) {
            Log.d(TAG, "fail to deinit dehaze !");
        }

        if (dstBitmap == null) {
            return srcBitmap;
        }

        Bitmap outBitmap = dstBitmap.copy(dstBitmap.getConfig(), true);
        dstBitmap.recycle();
        if (outBitmap == null) {
            return srcBitmap;
        }

        return outBitmap;
    }

    public static byte[] toByteArray(int[] ints) {
        ByteOrder order = ByteOrder.BIG_ENDIAN;
        final ByteBuffer buf = ByteBuffer.allocate(ints.length * 4).order(order);
        buf.asIntBuffer().put(ints);
        return buf.array();
    }

    public static int[] toIntArray(byte[] buf) {
        int[] array = new int[buf.length / 4];
        for (int i = 0, offset = 0; i < array.length; i++, offset += 4) {
            array[i] = (buf[3 + offset] & 0xFF) | ((buf[2 + offset] & 0xFF) << 8)
                    | ((buf[1 + offset] & 0xFF) << 16) | ((buf[0 + offset] & 0xFF) << 24);
        }
        return array;
    }

    public static byte[] toByteArray(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] src = new int[width * height];
        bitmap.getPixels(src, 0, width, 0, 0, width, height);
        return toByteArray(src);
    }

    public static Bitmap toBitmap(byte buf[], int width, int height) {
        if (width > 0 && height > 0) {
            int[] ints = toIntArray(buf);
            Log.d(TAG, "toBitmap ints.length = " + ints.length);
            return Bitmap.createBitmap(ints, width, height, Bitmap.Config.ARGB_8888);
        } else {
            return null;
        }
    }

    public native int dehaze_init(int img_width, int img_height);

    public native int dehaze_yuv4202rgb(byte[] srcData);

    public native int dehaze_rgb2yuv420(byte[] bufR, byte[] bufG, byte[] bufB, byte[] yuvData);

    public native int dehaze_copyRGBData(byte[] srcData);

    public native int dehaze_process(byte[] dstData);

    public native int dehaze_deinit();
}