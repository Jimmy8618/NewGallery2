package com.android.gallery3d.v2.discover.utils;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.app.GalleryAppImpl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class ImageUtils {
    private static final String TAG = ImageUtils.class.getSimpleName();

    private static final String FACE_HEAD_DIR_NAME = "face_head";
    private static final boolean MAINTAIN_ASPECT = true;
    private static final int DEFAULT_SIZE = 1024;

    /**
     * 根据指定options创建Bitmap
     *
     * @param path    文件路径
     * @param options options参数
     * @return 创建的Bitmap
     */
    @Nullable
    public static Bitmap createBitmap(String path, BitmapFactory.Options options) {
        if (TextUtils.isEmpty(path)) {
            Log.e(TAG, "createBitmap: path is empty.");
            return null;
        }
        return BitmapFactory.decodeFile(path, options);
    }

    @Nullable
    public static Bitmap createBitmap(ContentResolver resolver, Uri uri, BitmapFactory.Options options) {
        if (uri == null) {
            Log.e(TAG, "createBitmap: uri is empty.");
            return null;
        }
        InputStream is = null;
        try {
            is = resolver.openInputStream(uri);
            return BitmapFactory.decodeStream(is, null, options);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            Utils.closeSilently(is);
        }
    }

    /**
     * 将Bitmap缩放到指定大小
     *
     * @param bitmap       待缩放原图
     * @param targetWidth  指定宽度
     * @param targetHeight 指定高度
     * @param orientation  图片方向
     * @param recycle      是否回收原图
     * @return 缩放后的新图
     */
    @Nullable
    public static Bitmap scale(Bitmap bitmap, int targetWidth, int targetHeight,
                               int orientation, boolean recycle) {
        if (bitmap == null) {
            return null;
        }
        Bitmap scaled = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.RGB_565);
        Matrix matrix = getTransformationMatrix(bitmap.getWidth(), bitmap.getHeight(),
                targetWidth, targetHeight, orientation, MAINTAIN_ASPECT);
        Canvas canvas = new Canvas(scaled);
        canvas.drawBitmap(bitmap, matrix, null);
        if (recycle) {
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
                bitmap = null;
            }
        }
        return scaled;
    }

    /**
     * 获取Options参数
     *
     * @param path      文件路径
     * @param maxWidth  输出最大宽度
     * @param maxHeight 输出最大高度
     * @return Options
     */
    @NonNull
    public static BitmapFactory.Options getOptions(String path, int maxWidth, int maxHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();

        if (TextUtils.isEmpty(path)) {
            Log.e(TAG, "getOptions: path is empty.");
            return options;
        }

        if (maxWidth <= 0) {
            maxWidth = DEFAULT_SIZE;
        }
        if (maxHeight <= 0) {
            maxHeight = DEFAULT_SIZE;
        }

        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        int maxScreenW = Math.max(maxWidth, maxHeight);
        int minScreenH = Math.min(maxWidth, maxHeight);
        int maxImageW = Math.max(options.outWidth, options.outHeight);
        int minImageH = Math.min(options.outWidth, options.outHeight);
        int inSampleSize = (int) (Math.max(1.0f * maxImageW / maxScreenW,
                1.0f * minImageH / minScreenH));
        if (inSampleSize <= 0) {
            inSampleSize = 1;
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = inSampleSize;
        options.inPreferredConfig = Bitmap.Config.RGB_565;

        return options;
    }

    @NonNull
    public static BitmapFactory.Options getOptions(ContentResolver resolver, Uri uri, int maxWidth, int maxHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();

        if (uri == null) {
            Log.e(TAG, "getOptions: uri is empty.");
            return options;
        }

        if (maxWidth <= 0) {
            maxWidth = DEFAULT_SIZE;
        }
        if (maxHeight <= 0) {
            maxHeight = DEFAULT_SIZE;
        }

        options.inJustDecodeBounds = true;
        InputStream is = null;
        try {
            is = resolver.openInputStream(uri);
            BitmapFactory.decodeStream(is, null, options);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Utils.closeSilently(is);
        }
        int maxScreenW = Math.max(maxWidth, maxHeight);
        int minScreenH = Math.min(maxWidth, maxHeight);
        int maxImageW = Math.max(options.outWidth, options.outHeight);
        int minImageH = Math.min(options.outWidth, options.outHeight);
        int inSampleSize = (int) (Math.max(1.0f * maxImageW / maxScreenW,
                1.0f * minImageH / minScreenH));
        if (inSampleSize <= 0) {
            inSampleSize = 1;
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = inSampleSize;
        options.inPreferredConfig = Bitmap.Config.RGB_565;

        return options;
    }

    /**
     * 将图片宽高修整为2的倍数
     *
     * @param bitmap      待修整原图
     * @param orientation 图片方向
     * @param recycle     是否回收原图
     * @return 修整后的新图
     */
    @Nullable
    public static Bitmap round(Bitmap bitmap, int orientation, boolean recycle) {
        if (bitmap == null) {
            return null;
        }
        int targetW = (bitmap.getWidth() / 2) * 2;
        int targetH = (bitmap.getHeight() / 2) * 2;
        if (targetW <= 0 || targetH <= 0) {
            return null;
        }
        return scale(bitmap, targetW, targetH, orientation, recycle);
    }

    /**
     * 在Bitmap中指定位置抠出人脸图
     *
     * @param bitmap  包含人脸的图
     * @param x       人脸x坐标
     * @param y       人脸y坐标
     * @param width   人脸宽度
     * @param height  人脸高度
     * @param recycle 是否回收原图
     * @return 抠出的人脸图
     */
    @Nullable
    public static Bitmap cropHead(Bitmap bitmap, int x, int y, int width, int height, boolean recycle) {
        if (bitmap == null) {
            return null;
        }

        int originSize = Math.min(width, height);
        int scaleSize = 2 * originSize;
        int delta = (scaleSize - originSize) / 2;

        int sX = x - delta < 0 ? 0 : x - delta;
        int sY = y - delta < 0 ? 0 : y - delta;
        int sW = sX + scaleSize > bitmap.getWidth() ? bitmap.getWidth() - sX : scaleSize;
        int sH = sY + scaleSize > bitmap.getHeight() ? bitmap.getHeight() - sY : scaleSize;

        Log.d(TAG, "cropHead: bitmap size: " + bitmap.getWidth() + " x " + bitmap.getHeight() +
                "; head pos: " + x + "," + y + "; head size: " + width + " x " + height +
                " ==> new pos: " + sX + "," + sY + "; new size: " + sW + " x " + sH);
        Bitmap scaledBitmap = scale(Bitmap.createBitmap(bitmap, sX, sY, sW, sH),
                300, 300, 0, true);

        if (recycle) {
            bitmap.recycle();
            bitmap = null;
        }

        return scaledBitmap;
    }

    /**
     * 保存图片
     *
     * @param bitmap 待保存的图片
     * @param file   文件路径
     * @return 保存后的文件路径
     */
    public static String save(Bitmap bitmap, String file) {
        if (bitmap == null) {
            return "";
        }
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        } finally {
            Utils.closeSilently(out);
        }
        return file;
    }

    /**
     * Returns a transformation matrix from one reference frame into another.
     * Handles cropping (if maintaining aspect ratio is desired) and rotation.
     *
     * @param srcWidth            Width of source frame.
     * @param srcHeight           Height of source frame.
     * @param dstWidth            Width of destination frame.
     * @param dstHeight           Height of destination frame.
     * @param applyRotation       Amount of rotation to apply from one frame to another.
     *                            Must be a multiple of 90.
     * @param maintainAspectRatio If true, will ensure that scaling in x and y remains constant,
     *                            cropping the image if necessary.
     * @return The transformation fulfilling the desired requirements.
     */
    private static Matrix getTransformationMatrix(
            final int srcWidth,
            final int srcHeight,
            final int dstWidth,
            final int dstHeight,
            final int applyRotation,
            final boolean maintainAspectRatio) {
        final Matrix matrix = new Matrix();

        if (applyRotation != 0) {
            if (applyRotation % 90 != 0) {
                Log.w(TAG, "Rotation of " + applyRotation + " % 90 != 0");
            }
            // Translate so center of image is at origin.
            matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f);
            // Rotate around origin.
            matrix.postRotate(applyRotation);
        }

        // Account for the already applied rotation, if any, and then determine how
        // much scaling is needed for each axis.
        final boolean transpose = (Math.abs(applyRotation) + 90) % 180 == 0;
        final int inWidth = transpose ? srcHeight : srcWidth;
        final int inHeight = transpose ? srcWidth : srcHeight;

        // Apply scaling if necessary.
        if (inWidth != dstWidth || inHeight != dstHeight) {
            final float scaleFactorX = dstWidth / (float) inWidth;
            final float scaleFactorY = dstHeight / (float) inHeight;

            if (maintainAspectRatio) {
                // Scale by minimum factor so that dst is filled completely while
                // maintaining the aspect ratio. Some image may fall off the edge.
                final float scaleFactor = Math.max(scaleFactorX, scaleFactorY);
                matrix.postScale(scaleFactor, scaleFactor);
            } else {
                // Scale exactly to fill dst from src.
                matrix.postScale(scaleFactorX, scaleFactorY);
            }
        }
        if (applyRotation != 0) {
            // Translate back from origin centered reference to destination frame.
            matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f);
        }
        return matrix;
    }

    /**
     * 抠出人脸头像并保存成文件
     *
     * @param path        图片路径
     * @param orientation 图片方向
     * @param x           人脸在图片中的x坐标
     * @param y           人脸在图片中的y坐标
     * @param width       人脸在图片中的宽度
     * @param height      人脸在图片中的高度
     * @param vecId       特征Id
     * @return 保存头像后的头像文件路径
     */
    public static String saveHead(String path, int orientation, int x, int y, int width, int height, int vecId) {
        String dir = GalleryAppImpl.getApplication().getFilesDir().getAbsolutePath() + "/" + FACE_HEAD_DIR_NAME;
        mkdirs(dir);
        String head_file = dir + "/" + vecId + ".jpg";
        BitmapFactory.Options options = ImageUtils.getOptions(path, 0, 0);
        Bitmap cropped = ImageUtils.cropHead(
                ImageUtils.round(ImageUtils.createBitmap(path, options), orientation, true),
                x, y, width, height, true);
        String file = ImageUtils.save(cropped, head_file);
        if (cropped != null && !cropped.isRecycled()) {
            cropped.recycle();
            cropped = null;
        }
        return file;
    }

    /**
     * 创建文件夹
     *
     * @param dir dir name
     */
    private static void mkdirs(String dir) {
        File file = new File(dir);
        if (!file.exists()) {
            file.mkdirs();
        }
    }
}
