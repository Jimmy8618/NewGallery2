/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.data;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.BitmapRegionDecoder;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.BitmapUtils;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.util.ThreadPool.CancelListener;
import com.android.gallery3d.util.ThreadPool.JobContext;
import com.android.photos.data.GalleryBitmapPool;
import com.bumptech.glide.request.RequestOptions;
import com.sprd.frameworks.StandardFrameworks;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class DecodeUtils {
    private static final String TAG = "DecodeUtils";
    private static boolean isLowRam = StandardFrameworks.getInstances().isLowRam();

    private static class DecodeCanceller implements CancelListener {
        Options mOptions;

        public DecodeCanceller(Options options) {
            mOptions = options;
        }

        @Override
        public void onCancel() {
            mOptions.requestCancelDecode();
        }
    }

    @TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
    public static void setOptionsMutable(Options options) {
        if (ApiHelper.HAS_OPTIONS_IN_MUTABLE) {
            options.inMutable = true;
        }
    }

    public static Bitmap decode(JobContext jc, FileDescriptor fd, Options options) {
        if (options == null) {
            options = new Options();
        }
        jc.setCancelListener(new DecodeCanceller(options));
        setOptionsMutable(options);
        return ensureGLCompatibleBitmap(
                BitmapFactory.decodeFileDescriptor(fd, null, options));
    }

    public static void decodeBounds(JobContext jc, FileDescriptor fd,
                                    Options options) {
        Utils.assertTrue(options != null);
        options.inJustDecodeBounds = true;
        jc.setCancelListener(new DecodeCanceller(options));
        BitmapFactory.decodeFileDescriptor(fd, null, options);
        options.inJustDecodeBounds = false;
    }

    public static Bitmap decode(JobContext jc, byte[] bytes, Options options) {
        return decode(jc, bytes, 0, bytes.length, options);
    }

    public static Bitmap decode(JobContext jc, byte[] bytes, int offset,
                                int length, Options options) {
        if (options == null) {
            options = new Options();
        }
        jc.setCancelListener(new DecodeCanceller(options));
        setOptionsMutable(options);
        return ensureGLCompatibleBitmap(
                BitmapFactory.decodeByteArray(bytes, offset, length, options));
    }

    public static void decodeBounds(JobContext jc, byte[] bytes, int offset,
                                    int length, Options options) {
        Utils.assertTrue(options != null);
        options.inJustDecodeBounds = true;
        jc.setCancelListener(new DecodeCanceller(options));
        BitmapFactory.decodeByteArray(bytes, offset, length, options);
        options.inJustDecodeBounds = false;
    }

    public static Bitmap decodeThumbnail(
            JobContext jc, String filePath, Options options, int targetSize, int type) {
        //FileInputStream fis = null;
        try {
            //fis = new FileInputStream(filePath);
            //FileDescriptor fd = fis.getFD();
            return decodeThumbnailInternal(jc, filePath, options, targetSize, type);
        } catch (Exception ex) {
            Log.w(TAG, "decodeThumbnail Exception:");
            ex.printStackTrace();
            return null;
        } finally {
            //Utils.closeSilently(fis);
        }
    }

    public static Bitmap decodeThumbnail(
            JobContext jc, FileDescriptor fd, Options options, int targetSize, int type) {
        if (options == null) {
            options = new Options();
        }
        jc.setCancelListener(new DecodeCanceller(options));

        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(fd, null, options);
        //InputStream is = new FileInputStream(fd);
        //BitmapFactory.decodeStream(is, null, options);
        if (jc.isCancelled()) {
            //Utils.closeSilently(is);
            Log.d(TAG, "decodeThumbnail jc cancelled");
            return null;
        }

        int w = options.outWidth;
        int h = options.outHeight;

        if (type == MediaItem.TYPE_MICROTHUMBNAIL) {
            // We center-crop the original image as it's micro thumbnail. In this case,
            // we want to make sure the shorter side >= "targetSize".
            float scale = (float) targetSize / Math.min(w, h);
            options.inSampleSize = BitmapUtils.computeSampleSizeLarger(scale);

            // For an extremely wide image, e.g. 300x30000, we may got OOM when decoding
            // it for TYPE_MICROTHUMBNAIL. So we add a max number of pixels limit here.
            final int MAX_PIXEL_COUNT = 640000; // 400 x 1600
            if ((w / options.inSampleSize) * (h / options.inSampleSize) > MAX_PIXEL_COUNT) {
                options.inSampleSize = BitmapUtils.computeSampleSize(
                        (float) Math.sqrt((double) MAX_PIXEL_COUNT / (w * h)));
            }
        } else {
            // For screen nail, we only want to keep the longer side >= targetSize.
            float scale = (float) targetSize / Math.max(w, h);
            options.inSampleSize = BitmapUtils.computeSampleSizeLarger(scale);
        }

        options.inJustDecodeBounds = false;
        setOptionsMutable(options);

        Bitmap result = BitmapFactory.decodeFileDescriptor(fd, null, options);
        if (result == null) {
            Log.d(TAG, "decodeThumbnail decodeFile result = null.");
            return null;
        }

        // We need to resize down if the decoder does not support inSampleSize
        // (For example, GIF images)
        float scale = (float) targetSize / (type == MediaItem.TYPE_MICROTHUMBNAIL
                ? Math.min(result.getWidth(), result.getHeight())
                : Math.max(result.getWidth(), result.getHeight()));

        if (scale <= 0.5) {
            result = BitmapUtils.resizeBitmapByScale(result, scale, true);
        }
        if (result == null) {
            Log.d(TAG, "decodeThumbnail ensureGLCompatibleBitmap result = null.");
        }
        //Utils.closeSilently(is);
        return ensureGLCompatibleBitmap(result);
    }

    private static Bitmap decodeThumbnailInternal(
            JobContext jc, String filePath, Options options, int targetSize, int type) {
        if (options == null) {
            options = new Options();
        }
        jc.setCancelListener(new DecodeCanceller(options));

        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);
        if (jc.isCancelled()) {
            Log.d(TAG, "decodeThumbnailFromFile jc cancelled");
            return null;
        }

        int w = options.outWidth;
        int h = options.outHeight;

        if (type == MediaItem.TYPE_MICROTHUMBNAIL) {
            // We center-crop the original image as it's micro thumbnail. In this case,
            // we want to make sure the shorter side >= "targetSize".
            float scale = (float) targetSize / Math.min(w, h);
            options.inSampleSize = BitmapUtils.computeSampleSizeLarger(scale);

            // For an extremely wide image, e.g. 300x30000, we may got OOM when decoding
            // it for TYPE_MICROTHUMBNAIL. So we add a max number of pixels limit here.
            final int MAX_PIXEL_COUNT = 640000; // 400 x 1600
            if ((w / options.inSampleSize) * (h / options.inSampleSize) > MAX_PIXEL_COUNT) {
                options.inSampleSize = BitmapUtils.computeSampleSize(
                        (float) Math.sqrt((double) MAX_PIXEL_COUNT / (w * h)));
            }
        } else {
            // For screen nail, we only want to keep the longer side >= targetSize.
            float scale = (float) targetSize / Math.max(w, h);
            options.inSampleSize = BitmapUtils.computeSampleSizeLarger(scale);
        }

        options.inJustDecodeBounds = false;
        setOptionsMutable(options);

        Bitmap result = BitmapFactory.decodeFile(filePath, options);
        if (result == null) {
            Log.d(TAG, "decodeThumbnailFromFile decodeFile result = null.");
            return null;
        }

        // We need to resize down if the decoder does not support inSampleSize
        // (For example, GIF images)
        float scale = (float) targetSize / (type == MediaItem.TYPE_MICROTHUMBNAIL
                ? Math.min(result.getWidth(), result.getHeight())
                : Math.max(result.getWidth(), result.getHeight()));

        if (scale <= 0.5) {
            result = BitmapUtils.resizeBitmapByScale(result, scale, true);
        }
        if (result == null) {
            Log.d(TAG, "decodeThumbnailFromFile ensureGLCompatibleBitmap result = null.");
        }
        return ensureGLCompatibleBitmap(result);
    }

    // use picture byte[] to decode bokeh Thumbnail
    public static Bitmap decodeThumbnail(JobContext jc, byte[] bokehPicture, Options options, int targetSize, int type) {
        Log.d(TAG, "decode bokeh thumbnail start.");
        if (options == null) {
            options = new Options();
        }
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bokehPicture, 0, bokehPicture.length, options);
        if (jc.isCancelled()) {
            Log.d(TAG, "decode Bokeh Thumbnail jc cancelled");
            return null;
        }

        int w = options.outWidth;
        int h = options.outHeight;

        if (type == MediaItem.TYPE_MICROTHUMBNAIL) {
            // We center-crop the original image as it's micro thumbnail. In this case,
            // we want to make sure the shorter side >= "targetSize".
            float scale = (float) targetSize / Math.min(w, h);
            options.inSampleSize = BitmapUtils.computeSampleSizeLarger(scale);

            final int MAX_PIXEL_COUNT = 640000; // 400 x 1600
            if ((w / options.inSampleSize) * (h / options.inSampleSize) > MAX_PIXEL_COUNT) {
                options.inSampleSize = BitmapUtils.computeSampleSize(
                        (float) Math.sqrt((double) MAX_PIXEL_COUNT / (w * h)));
            }
        } else {
            // For screen nail, we only want to keep the longer side >= targetSize.
            float scale = (float) targetSize / Math.max(w, h);
            options.inSampleSize = BitmapUtils.computeSampleSizeLarger(scale);
        }

        options.inJustDecodeBounds = false;
        setOptionsMutable(options);

        Bitmap result = BitmapFactory.decodeByteArray(bokehPicture, 0, bokehPicture.length, options);
        if (result == null) {
            Log.d(TAG, "decode bokeh thumbnail decodeByteArray result = null.");
            return null;
        }
        if (jc.isCancelled()) {
            Log.d(TAG, "decode Bokeh Thumbnail result, but jc cancelled");
            return null;
        }

        // We need to resize down if the decoder does not support inSampleSize
        // (For example, GIF images)
        float scale = (float) targetSize / (type == MediaItem.TYPE_MICROTHUMBNAIL
                ? Math.min(result.getWidth(), result.getHeight())
                : Math.max(result.getWidth(), result.getHeight()));

        if (scale <= 0.5) {
            result = BitmapUtils.resizeBitmapByScale(result, scale, true);
        }
        if (result == null) {
            Log.d(TAG, "decode bokeh thumbnail ensureGLCompatibleBitmap result = null.");
        }

        Bitmap bitmap = ensureGLCompatibleBitmap(result);
        Log.d(TAG, "decode bokeh thumbnail end.");
        return bitmap;
    }

    /**
     * Decodes the bitmap from the given byte array if the image size is larger than the given
     * requirement.
     * <p>
     * Note: The returned image may be resized down. However, both width and height must be
     * larger than the <code>targetSize</code>.
     */
    public static Bitmap decodeIfBigEnough(JobContext jc, byte[] data,
                                           Options options, int targetSize) {
        if (options == null) {
            options = new Options();
        }
        jc.setCancelListener(new DecodeCanceller(options));

        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (jc.isCancelled()) {
            return null;
        }
        if (options.outWidth < targetSize || options.outHeight < targetSize) {
            return null;
        }
        options.inSampleSize = BitmapUtils.computeSampleSizeLarger(
                options.outWidth, options.outHeight, targetSize);
        options.inJustDecodeBounds = false;
        setOptionsMutable(options);

        return ensureGLCompatibleBitmap(
                BitmapFactory.decodeByteArray(data, 0, data.length, options));
    }

    // TODO: This function should not be called directly from
    // DecodeUtils.requestDecode(...), since we don't have the knowledge
    // if the bitmap will be uploaded to GL.
    public static Bitmap ensureGLCompatibleBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.getConfig() != null) {
            return bitmap;
        }
        Bitmap newBitmap = bitmap.copy(Config.ARGB_8888, false);
        bitmap.recycle();
        return newBitmap;
    }

    public static BitmapRegionDecoder createBitmapRegionDecoder(
            JobContext jc, byte[] bytes, int offset, int length,
            boolean shareable) {
        if (offset < 0 || length <= 0 || offset + length > bytes.length) {
            throw new IllegalArgumentException(String.format(
                    "offset = %s, length = %s, bytes = %s",
                    offset, length, bytes.length));
        }

        try {
            return BitmapRegionDecoder.newInstance(
                    bytes, offset, length, shareable);
        } catch (Throwable t) {
            Log.w(TAG, t);
            return null;
        }
    }

    public static BitmapRegionDecoder createBitmapRegionDecoder(
            JobContext jc, String filePath, boolean shareable) {
        try {
            return BitmapRegionDecoder.newInstance(filePath, shareable);
        } catch (Throwable t) {
            Log.w(TAG, t);
            return null;
        }
    }

    public static BitmapRegionDecoder createBitmapRegionDecoder(
            JobContext jc, FileDescriptor fd, boolean shareable) {
        try {
            return BitmapRegionDecoder.newInstance(fd, shareable);
        } catch (Throwable t) {
            Log.w(TAG, t);
            return null;
        }
    }

    public static BitmapRegionDecoder createBitmapRegionDecoder(
            JobContext jc, InputStream is, boolean shareable) {
        try {
            return BitmapRegionDecoder.newInstance(is, shareable);
        } catch (Throwable t) {
            // We often cancel the creating of bitmap region decoder,
            // so just log one line.
            Log.w(TAG, "requestCreateBitmapRegionDecoder: " + t);
            return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static Bitmap decodeUsingPool(JobContext jc, byte[] data, int offset,
                                         int length, BitmapFactory.Options options) {
        if (options == null) {
            options = new Options();
        }
        if (options.inSampleSize < 1) {
            options.inSampleSize = 1;
        }
        options.inPreferredConfig = isLowRam ? Bitmap.Config.ARGB_4444 : Bitmap.Config.ARGB_8888;
        options.inBitmap = (options.inSampleSize == 1)
                ? findCachedBitmap(jc, data, offset, length, options) : null;
        try {
            Bitmap bitmap = decode(jc, data, offset, length, options);
            if (options.inBitmap != null && options.inBitmap != bitmap) {
                GalleryBitmapPool.getInstance().put(options.inBitmap);
                options.inBitmap = null;
            }
            return bitmap;
        } catch (IllegalArgumentException e) {
            if (options.inBitmap == null) {
                throw e;
            }

            Log.w(TAG, "decode fail with a given bitmap, try decode to a new bitmap");
            GalleryBitmapPool.getInstance().put(options.inBitmap);
            options.inBitmap = null;
            return decode(jc, data, offset, length, options);
        }
    }

    // This is the same as the method above except the source data comes
    // from a file descriptor instead of a byte array.
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static Bitmap decodeUsingPool(JobContext jc,
                                         FileDescriptor fileDescriptor, Options options) {
        if (options == null) {
            options = new Options();
        }
        if (options.inSampleSize < 1) {
            options.inSampleSize = 1;
        }

        options.inPreferredConfig = isLowRam ? Bitmap.Config.ARGB_4444 : Bitmap.Config.ARGB_8888;
        options.inBitmap = (options.inSampleSize == 1)
                ? findCachedBitmap(jc, fileDescriptor, options) : null;
        try {
            Bitmap bitmap = DecodeUtils.decode(jc, fileDescriptor, options);
            if (options.inBitmap != null && options.inBitmap != bitmap) {
                GalleryBitmapPool.getInstance().put(options.inBitmap);
                options.inBitmap = null;
            }
            return bitmap;
        } catch (IllegalArgumentException e) {
            if (options.inBitmap == null) {
                throw e;
            }

            Log.w(TAG, "decode fail with a given bitmap, try decode to a new bitmap");
            GalleryBitmapPool.getInstance().put(options.inBitmap);
            options.inBitmap = null;
            return decode(jc, fileDescriptor, options);
        }
    }

    private static Bitmap findCachedBitmap(JobContext jc, byte[] data,
                                           int offset, int length, Options options) {
        decodeBounds(jc, data, offset, length, options);
        return GalleryBitmapPool.getInstance().get(options.outWidth, options.outHeight);
    }

    private static Bitmap findCachedBitmap(JobContext jc, FileDescriptor fileDescriptor,
                                           Options options) {
        decodeBounds(jc, fileDescriptor, options);
        return GalleryBitmapPool.getInstance().get(options.outWidth, options.outHeight);
    }

    public static Bitmap decodeThumbnail(
            JobContext jc, Uri uri, Options options, int targetSize, int type) {
        try {
            return decodeThumbnailInternal(jc, uri, options, targetSize, type);
        } catch (Exception ex) {
            Log.w(TAG, "decodeThumbnail Exception: ", ex);
            return null;
        }
    }

    private static Bitmap decodeThumbnailInternal(
            JobContext jc, Uri uri, Options options, int targetSize, int type) {
        Log.d(TAG, "decodeThumbnailInternal uri = " + uri);

        InputStream is;

        if (options == null) {
            options = new Options();
        }

        try {
            // 打开输入流, 用于获取图片大小
            is = GalleryAppImpl.getApplication().getContentResolver().openInputStream(uri);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "decodeThumbnailInternalFromUri FileNotFoundException ", e);
            return null;
        }

        jc.setCancelListener(new DecodeCanceller(options));

        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(is, null, options);
        //关闭流
        Utils.closeSilently(is);

        if (jc.isCancelled()) {
            Log.d(TAG, "decodeThumbnailInternalFromUri jc cancelled");
            return null;
        }

        int w = options.outWidth;
        int h = options.outHeight;
        Log.d(TAG, "decodeThumbnailInternalFromUri w = " + w + ", h = " + h);

        if (type == MediaItem.TYPE_MICROTHUMBNAIL) {
            // We center-crop the original image as it's micro thumbnail. In this case,
            // we want to make sure the shorter side >= "targetSize".
            float scale = (float) targetSize / Math.min(w, h);
            options.inSampleSize = BitmapUtils.computeSampleSizeLarger(scale);

            // For an extremely wide image, e.g. 300x30000, we may got OOM when decoding
            // it for TYPE_MICROTHUMBNAIL. So we add a max number of pixels limit here.
            final int MAX_PIXEL_COUNT = 640000; // 400 x 1600
            if ((w / options.inSampleSize) * (h / options.inSampleSize) > MAX_PIXEL_COUNT) {
                options.inSampleSize = BitmapUtils.computeSampleSize(
                        (float) Math.sqrt((double) MAX_PIXEL_COUNT / (w * h)));
            }
        } else {
            // For screen nail, we only want to keep the longer side >= targetSize.
            float scale = (float) targetSize / Math.max(w, h);
            options.inSampleSize = BitmapUtils.computeSampleSizeLarger(scale);
        }

        options.inJustDecodeBounds = false;
        setOptionsMutable(options);

        try {
            // 打开输入流, 获取图片
            is = GalleryAppImpl.getApplication().getContentResolver().openInputStream(uri);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "decodeThumbnailInternalFromUri FileNotFoundException ", e);
            return null;
        }

        Bitmap result = BitmapFactory.decodeStream(is, null, options);
        //关闭流
        Utils.closeSilently(is);

        if (result == null) {
            Log.d(TAG, "decodeThumbnailInternalFromUri decodeFile result = null.");
            return null;
        }

        // We need to resize down if the decoder does not support inSampleSize
        // (For example, GIF images)
        float scale = (float) targetSize / (type == MediaItem.TYPE_MICROTHUMBNAIL
                ? Math.min(result.getWidth(), result.getHeight())
                : Math.max(result.getWidth(), result.getHeight()));

        if (scale <= 0.5) {
            result = BitmapUtils.resizeBitmapByScale(result, scale, true);
        }
        if (result == null) {
            Log.d(TAG, "decodeThumbnailInternalFromUri ensureGLCompatibleBitmap result = null.");
        }
        return ensureGLCompatibleBitmap(result);
    }

    public static RequestOptions withOptions(int thumbSize, int itemWidth, int itemHeight) {
        RequestOptions options = new RequestOptions();
        if ((itemWidth > thumbSize && itemHeight > thumbSize)
                || (itemWidth <= 0 || itemHeight <= 0)) {
            options = options.override(thumbSize);
        } else {
            options = options.override(itemWidth, itemHeight);
        }
        return options;
    }
}
