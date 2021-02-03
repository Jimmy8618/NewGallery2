package com.sprd.gallery3d.smarterase;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;

import androidx.annotation.Nullable;

import com.android.gallery3d.app.GalleryStorageUtil;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.v2.util.SdCardPermission;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

class SmartEraseUtils {
    private static String TAG = "SmartEraseUtils";
    private static boolean DEBUG = true;
    private static final String PREFIX_IMG = "IMG";
    private static final String POSTFIX_JPG = ".jpg";
    private static final String DEFAULT_SAVE_DIRECTORY = "EditedOnlinePhotos";
    private static final String TIME_STAMP_NAME = "_yyyyMMdd_HHmmss_";

    public static int getScale(float displayWidth, float displayHeight, float bitmapWidth, float bitmapHeight) {
        int widthScale = Math.round(Math.max(displayWidth, bitmapWidth) / Math.min(displayWidth, bitmapWidth));
        int heightScale = Math.round(Math.max(displayHeight, bitmapHeight) / Math.min(displayHeight, bitmapHeight));
        return Math.max(widthScale, heightScale);
    }

    public static Size getBitmapSize(ContentResolver resolver, Uri fileUri) {
        InputStream is = null;
        Size bitmapSize = new Size(0, 0);
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            is = resolver.openInputStream(fileUri);
            BitmapFactory.decodeStream(is, null, options);
            bitmapSize = new Size(options.outWidth, options.outHeight);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Utils.closeSilently(is);
        }
        print("bitmapSize = " + bitmapSize.toString());
        return bitmapSize;
    }

    public static Bitmap getOriginalBitmap(ContentResolver resolver, Uri fileUri) {
        InputStream is = null;
        Bitmap bitmap = null;
        try {
            is = resolver.openInputStream(fileUri);
            bitmap = BitmapFactory.decodeStream(is);
            Log.d(TAG, "getOriginalBitmap: bitmap=" + bitmap);
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Utils.closeSilently(is);
        }
        return bitmap;
    }

    public static Bitmap getScaledBitmap(ContentResolver resolver, Uri fileUri, int scale) {
        InputStream is = null;
        Bitmap bitmap = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = scale;
            is = resolver.openInputStream(fileUri);
            bitmap = BitmapFactory.decodeStream(is, null, options);//.copy(Bitmap.Config.RGB_565, true);
            Log.d(TAG, "getScaledBitmap: bitmap=" + bitmap);
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Utils.closeSilently(is);
        }
        return bitmap;
    }

    public static void print(String s) {
        if (DEBUG) {
            Log.e(TAG, s);
        }
    }

    public static Bitmap mergeBitmap(Bitmap backBitmap, Bitmap frontBitmap) {

        if (backBitmap == null || backBitmap.isRecycled()
                || frontBitmap == null || frontBitmap.isRecycled()) {
            Log.e(TAG, "backBitmap=" + backBitmap + ";frontBitmap=" + frontBitmap);
            return null;
        }
        Bitmap bitmap = backBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(bitmap);
        Rect baseRect = new Rect(0, 0, backBitmap.getWidth(), backBitmap.getHeight());
        Rect frontRect = new Rect(0, 0, frontBitmap.getWidth(), frontBitmap.getHeight());
        canvas.drawBitmap(frontBitmap, frontRect, baseRect, null);
        return bitmap;
    }

    public static boolean saveBitmap(Bitmap mBitmap, String savePath) {
        Log.d(TAG, "saveBitmap: " + savePath);
        OutputStream fos = null;
        try {

            if (GalleryStorageUtil.isInInternalStorage(savePath)) {
                fos = new FileOutputStream(savePath);
            } else {
                SdCardPermission.mkFile(savePath);
                fos = SdCardPermission.createExternalOutputStream(savePath);
            }
            mBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            Utils.closeSilently(fos);
        }
        return true;
    }

    public interface ContentResolverQueryCallback {
        void onCursorResult(Cursor cursor);
    }

    public static File getNewFile(Context context, Uri sourceUri) {
        File saveDirectory = getFinalSaveDirectory(context, sourceUri);
        long timeMillis = System.currentTimeMillis();
        String filename = new SimpleDateFormat(TIME_STAMP_NAME).format(new Date(
                timeMillis)) + timeMillis;

        return new File(saveDirectory, PREFIX_IMG + filename + POSTFIX_JPG);
    }

    public static File getFinalSaveDirectory(Context context, Uri sourceUri) {
        File saveDirectory = getSaveDirectory(context, sourceUri);
        if ((saveDirectory == null) /*|| !saveDirectory.canWrite()*/) {
            saveDirectory = new File(Environment.getExternalStorageDirectory(),
                    DEFAULT_SAVE_DIRECTORY);
        }
        // Create the directory if it doesn't exist
        if (!saveDirectory.exists()) {
            saveDirectory.mkdirs();
        }
        return saveDirectory;
    }

    public static File getSaveDirectory(Context context, Uri sourceUri) {
        File file = getLocalFileFromUri(context, sourceUri);
        if (file != null) {
            return file.getParentFile();
        } else {
            return null;
        }
    }

    public static File getLocalFileFromUri(Context context, Uri srcUri) {
        if (srcUri == null) {
            Log.e(TAG, "srcUri is null.");
            return null;
        }

        String scheme = srcUri.getScheme();
        if (scheme == null) {
            Log.e(TAG, "scheme is null.");
            return null;
        }

        final File[] file = new File[1];
        // sourceUri can be a file path or a content Uri, it need to be handled
        // differently.
        if (scheme.equals(ContentResolver.SCHEME_CONTENT)) {
            if (srcUri.getAuthority().equals(MediaStore.AUTHORITY)) {
                querySource(context, srcUri, new String[]{
                                MediaStore.Images.ImageColumns.DATA
                        },
                        new ContentResolverQueryCallback() {

                            @Override
                            public void onCursorResult(Cursor cursor) {
                                file[0] = new File(cursor.getString(0));
                            }
                        });
            }
        } else if (scheme.equals(ContentResolver.SCHEME_FILE)) {
            file[0] = new File(srcUri.getPath());
        }
        return file[0];
    }

    public static void querySource(Context context, Uri sourceUri, String[] projection,
                                   ContentResolverQueryCallback callback) {
        ContentResolver contentResolver = context.getContentResolver();
        querySourceFromContentResolver(contentResolver, sourceUri, projection, callback);
    }

    private static void querySourceFromContentResolver(
            ContentResolver contentResolver, Uri sourceUri, String[] projection,
            ContentResolverQueryCallback callback) {
        Cursor cursor = null;
        try {
            cursor = contentResolver.query(sourceUri, projection, null, null,
                    null);
            if ((cursor != null) && cursor.moveToNext()) {
                callback.onCursorResult(cursor);
            }
        } catch (Exception e) {
            // Ignore error for lacking the data column from the source.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }


    public static byte[] toByteArray(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] ints = new int[width * height];
        bitmap.getPixels(ints, 0, width, 0, 0, width, height);

        /*channel = 3*/
        byte[] bytes = new byte[ints.length * 3];
        for (int i = 0, offset = 0; i < ints.length; i++, offset += 3) {
            bytes[offset] = (byte) ((ints[i] & 0x00FF0000) >> 16);
            bytes[offset + 1] = (byte) ((ints[i] & 0x0000FF00) >> 8);
            bytes[offset + 2] = (byte) ((ints[i] & 0x000000FF));
        }
        return bytes;
    }

    @Nullable
    public static Bitmap toBitmap(byte buf[], int width, int height) {
        if (width > 0 && height > 0) {
            int[] ints = new int[buf.length / 3];
            for (int i = 0, offset = 0; i < ints.length; i++, offset += 3) {
                ints[i] = 0xFF << 24 |
                        ((buf[0 + offset] & 0xFF) << 16) |
                        ((buf[1 + offset] & 0xFF) << 8) |
                        ((buf[2 + offset] & 0xFF));
            }
            Log.d(TAG, "toBitmap ints.length = " + ints.length);
            return Bitmap.createBitmap(ints, width, height, Bitmap.Config.ARGB_8888);
        } else {
            return null;
        }
    }
}
