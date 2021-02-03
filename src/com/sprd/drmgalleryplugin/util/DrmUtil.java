package com.sprd.drmgalleryplugin.util;

import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.drm.DrmManagerClient;
import android.drm.DrmStore;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory.Options;
import android.net.Uri;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import com.android.gallery3d.R;
import com.android.gallery3d.common.BitmapUtils;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.util.ThreadPool.CancelListener;
import com.android.gallery3d.util.ThreadPool.JobContext;
import com.sprd.drmgalleryplugin.app.AddonGalleryAppImpl;
import com.sprd.frameworks.StandardFrameworks;
import com.sprd.gallery3d.app.NewVideoActivity;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DrmUtil {

    private static final String TAG = "DrmUtil";

    public static final boolean DRMSWITCH = StandardFrameworks.getInstances().getIsDrmSupported();

    public static final String DRM_FILE_TYPE = "extended_data";
    public static String FL_DRM_FILE = "fl";
    public static String CD_DRM_FILE = "cd";
    public static String SD_DRM_FILE = "sd";
    public static final String RIGHTS_NO_lIMIT = "-1";

    public static final String ACTION_DRM = "sprd.android.intent.action.VIEW_DOWNLOADS_DRM";
    public static final String FILE_NAME = "filename";
    public static final String IS_RENEW = "isrenew";
    public static final String KEY_DRM_MIMETYPE = "mimetype";
    public static final String DCF_FILE_MIMETYPE = "application/vnd.oma.drm.content";

    public static boolean isDrmFile(String filePath, String mimeType) {
        DrmManagerClient drmManagerClient = AddonGalleryAppImpl.getDrmManagerClient();

        boolean isDRMFile = false;
        try {
            // SPRD: Modify 20160115 for bug: VOD video will be wrongly identified as DRM file @{
            isDRMFile = drmManagerClient.canHandle(filePath, mimeType)
                    && drmManagerClient.getMetadata(filePath) != null;
            // @}
        } catch (IllegalArgumentException ex) {
            Log.w(TAG, "filePath is null or empty string.");
            return false;
        } catch (IllegalStateException ex) {
            Log.w(TAG, "DrmManagerClient didn't initialize properly.");
            return false;
        }
        return isDRMFile;
    }

    public static boolean isDrmFile(Uri uri, String mimeType) {
        DrmManagerClient drmManagerClient = AddonGalleryAppImpl.getDrmManagerClient();

        boolean isDRMFile = false;
        try {
            // SPRD: Modify 20160115 for bug: VOD video will be wrongly identified as DRM file @{
            isDRMFile = drmManagerClient.canHandle(uri, mimeType)
                    && drmManagerClient.getMetadata(uri) != null;
            // @}
        } catch (IllegalArgumentException ex) {
            Log.w(TAG, "filePath is null or empty string.");
            return false;
        } catch (IllegalStateException ex) {
            Log.w(TAG, "DrmManagerClient didn't initialize properly.");
            return false;
        }
        return isDRMFile;
    }

    public static DrmManagerClient getDrmManagerClient() {
        return AddonGalleryAppImpl.getDrmManagerClient();
    }

    public static boolean isDrmValid(String filePath) {
        return DrmStore.RightsStatus.RIGHTS_VALID == getDrmRightsStatus(filePath);
    }

    public static int getDrmRightsStatus(String filePath) {
        DrmManagerClient drmManagerClient = AddonGalleryAppImpl.getDrmManagerClient();
        /* SPRD: Modify for bug525867, java.lang.IllegalArgumentException: Given path or action is n
        ot valid @{ */
        int status = DrmStore.RightsStatus.RIGHTS_INVALID;
        try {
            status = drmManagerClient.checkRightsStatus(filePath);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "getDrmRightsStatus error:" + e);
        }
        return status;
        /* @} */
    }

    public static boolean isDrmValid(Uri uri) {
        return DrmStore.RightsStatus.RIGHTS_VALID == getDrmRightsStatus(uri);
    }

    public static int getDrmRightsStatus(Uri uri) {
        DrmManagerClient drmManagerClient = AddonGalleryAppImpl.getDrmManagerClient();
        /* SPRD: Modify for bug525867, java.lang.IllegalArgumentException: Given path or action is n
        ot valid @{ */
        int status = DrmStore.RightsStatus.RIGHTS_INVALID;
        try {
            status = drmManagerClient.checkRightsStatus(uri);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "getDrmRightsStatus error:" + e);
        }
        return status;
        /* @} */
    }

    public static Object compareDrmRemainRight(String filePath, Object object, Context context) {
        if (object == null) {
            if (getDrmFileType(filePath).equals(FL_DRM_FILE)) {
                return context.getString(R.string.drm_rights_no_limit);
            } else {
                return context.getString(R.string.drm_rights_unknown);
            }
        }
        return object.toString().equals(RIGHTS_NO_lIMIT) ?
                context.getString(R.string.drm_rights_no_limit) : object;
    }

    public static Object compareDrmRemainRight(Uri uri, Object object, Context context) {
        if (object == null) {
            if (getDrmFileType(uri).equals(FL_DRM_FILE)) {
                return context.getString(R.string.drm_rights_no_limit);
            } else {
                return context.getString(R.string.drm_rights_unknown);
            }
        }
        return object.toString().equals(RIGHTS_NO_lIMIT) ?
                context.getString(R.string.drm_rights_no_limit) : object;
    }

    public static Object compareDrmExpirationTime(Object object, byte[] clickTime,
                                                  Context context) {
        if (object == null) {
            return context.getString(R.string.drm_rights_unknown);
        } else if (clickTime == null) {
            return context.getString(R.string.drm_rights_inactive);
        } else if (object.toString().equals(RIGHTS_NO_lIMIT)) {
            return context.getString(R.string.drm_rights_no_limit);
        } else {
            String cTime = new String(clickTime);
            Long time = Long.valueOf(object.toString()) + Long.valueOf(cTime);
            return transferDate(time, context);
        }
    }

    public static Object transferDate(Long time, Context context) {
        if (time == null) {
            return context.getString(R.string.drm_rights_unknown);
        }
        if (time == -1) {
            return context.getString(R.string.drm_rights_no_limit);
        }
        Date date = new Date(time * 1000);
        SimpleDateFormat sdformat = new SimpleDateFormat(
                context.getString(R.string.drm_date_format));
        return sdformat.format(date);
    }

    public static boolean isDrmSupportTransfer(String path) {
        DrmManagerClient drmManagerClient = AddonGalleryAppImpl.getDrmManagerClient();
        return DrmStore.RightsStatus.RIGHTS_VALID
                == drmManagerClient.checkRightsStatus(path, DrmStore.Action.TRANSFER);
    }

    public static boolean isDrmSupportTransfer(Uri uri) {
        DrmManagerClient drmManagerClient = AddonGalleryAppImpl.getDrmManagerClient();
        return DrmStore.RightsStatus.RIGHTS_VALID
                == drmManagerClient.checkRightsStatus(uri, DrmStore.Action.TRANSFER);
    }

    /* add for bug 318505 start @{ */
    public static String getDrmFileType(String filePath) {
        String drmFileType = "";
        DrmManagerClient drmManagerClient = AddonGalleryAppImpl.getDrmManagerClient();
        try {
            ContentValues values = drmManagerClient.getMetadata(filePath);
            if (values != null) {
                drmFileType = values.getAsString(DRM_FILE_TYPE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Get extended_data error");
        }
        return drmFileType;
    }
    /* @} add for bug 318505 end */

    public static String getDrmFileType(Uri uri) {
        String drmFileType = "";
        DrmManagerClient drmManagerClient = AddonGalleryAppImpl.getDrmManagerClient();
        try {
            ContentValues values = drmManagerClient.getMetadata(uri);
            if (values != null) {
                drmFileType = values.getAsString(DRM_FILE_TYPE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Get extended_data error");
        }
        return drmFileType;
    }

    public static String getFilePathByUri(Uri uri, Context context) {
        Log.d(TAG, "Uri=" + uri.toString());
        String filePath = "";
        if (uri.getScheme().compareTo("content") == 0) {
            String[] projection = {MediaStore.Images.Media.DATA};

            if (Looper.myLooper() == null) {
                Looper.prepare();
            }
            CursorLoader loader = new CursorLoader(context, uri, projection, null, null, null);
            Cursor cursor = null;
            try {
                cursor = loader.loadInBackground();
                if (cursor != null) {
                    int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                    cursor.moveToFirst();
                    filePath = cursor.getString(column_index);
                    //remove cursor close to finally for bug 450517
                    //cursor.close();
                }
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (NullPointerException e) {
                e.printStackTrace();
                // add for bug 309910
            } catch (CursorIndexOutOfBoundsException e) {
                e.printStackTrace();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (SecurityException e) {
                e.printStackTrace();
            } finally {
                if (cursor != null) {
                    Log.d(TAG, "cursor close");
                    cursor.close();
                    cursor = null;
                }
            }

        } else if (uri.getScheme().compareTo("file") == 0) {
            filePath = uri.getPath();
        }
        Log.d(TAG, "path=" + filePath);
        return filePath;
    }

    public static Bitmap decodeDrmThumbnail(
            JobContext jc, String filePath, Options options, int targetSize, int type) {
        Log.d(TAG, "decodeDrmThumbnail, filePath = " + filePath);
        DrmManagerClient client = AddonGalleryAppImpl.getDrmManagerClient();
        if (options == null) {
            options = new Options();
        }
        jc.setCancelListener(new DecodeCanceller(options));
        options.inJustDecodeBounds = true;
        if (client == null) {
            Log.w(TAG, "DrmManagerClient is null!");
            return null;
        }
        StandardFrameworks.getInstances().decodeDRMBitmapWithBitmapOptions(client, filePath, options);
        if (jc.isCancelled()) {
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
                        (float) Math.sqrt((float) MAX_PIXEL_COUNT / (w * h)));
            }
        } else {
            // For screen nail, we only want to keep the longer side >= targetSize.
            float scale = (float) targetSize / Math.max(w, h);
            options.inSampleSize = BitmapUtils.computeSampleSizeLarger(scale);
        }

        options.inJustDecodeBounds = false;
        Bitmap result = null;
        result = StandardFrameworks.getInstances().decodeDRMBitmapWithBitmapOptions(client, filePath, options);
        if (result == null) {
            Log.w(TAG, "Drm bitmap decode result is null!");
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
        if (result == null || result.getConfig() != null) {
            return result;
        }
        Bitmap newBitmap = result.copy(Config.ARGB_8888, false);
        result.recycle();
        return newBitmap;
    }

    public static Bitmap decodeDrmThumbnail(
            JobContext jc, Uri uri, Options options, int targetSize, int type) {
        Log.d(TAG, "decodeDrmThumbnail, uri = " + uri);
        DrmManagerClient client = AddonGalleryAppImpl.getDrmManagerClient();
        if (options == null) {
            options = new Options();
        }
        jc.setCancelListener(new DecodeCanceller(options));
        options.inJustDecodeBounds = true;
        if (client == null) {
            Log.w(TAG, "DrmManagerClient is null!");
            return null;
        }
        StandardFrameworks.getInstances().decodeDRMBitmapWithBitmapOptions(client, uri, options);
        if (jc.isCancelled()) {
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
                        (float) Math.sqrt((float) MAX_PIXEL_COUNT / (w * h)));
            }
        } else {
            // For screen nail, we only want to keep the longer side >= targetSize.
            float scale = (float) targetSize / Math.max(w, h);
            options.inSampleSize = BitmapUtils.computeSampleSizeLarger(scale);
        }

        options.inJustDecodeBounds = false;
        Bitmap result = null;
        result = StandardFrameworks.getInstances().decodeDRMBitmapWithBitmapOptions(client, uri, options);
        if (result == null) {
            Log.w(TAG, "Drm bitmap decode result is null!");
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
        if (result == null || result.getConfig() != null) {
            return result;
        }
        Bitmap newBitmap = result.copy(Config.ARGB_8888, false);
        result.recycle();
        return newBitmap;
    }

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

    /* SPRD: Add for drm feature @{ */
    public static Object newCompareDrmRemainRight(String filePath, Object object, NewVideoActivity activity) {
        if (object == null) {
            if (getDrmFileType(filePath).equals(FL_DRM_FILE)) {
                return activity.getStringForDrm(2);
            } else {
                return activity.getStringForDrm(0);
            }
        }
        return object.toString().equals(RIGHTS_NO_lIMIT) ?
                activity.getStringForDrm(2) : object;
    }

    public static Object newCompareDrmExpirationTime(Object object, byte[] clickTime,
                                                     NewVideoActivity activity) {
        if (object == null) {
            return activity.getStringForDrm(0);
        } else if (clickTime == null) {
            return activity.getStringForDrm(1);
        } else if (object.toString().equals(RIGHTS_NO_lIMIT)) {
            return activity.getStringForDrm(2);
        } else {
            String cTime = new String(clickTime);
            Long time = Long.valueOf(object.toString()) + Long.valueOf(cTime);
            return newTransferDate(time, activity);
        }
    }

    public static Object newTransferDate(Long time, NewVideoActivity activity) {
        if (time == null) {
            return activity.getStringForDrm(0);
        }
        if (time == -1) {
            return activity.getStringForDrm(2);
        }
        Date date = new Date(time * 1000);
        SimpleDateFormat sdformat = new SimpleDateFormat(
                activity.getStringForDrm(3));
        return sdformat.format(date);
    }

    /* SPRD: Add for bug599941 non-sd drm videos are not supported to share @{ */
    public static boolean newIsSupportShare(String filePath) {
        return getDrmFileType(filePath).equals(SD_DRM_FILE);
    }
    /* Bug599941 end @} */
    /* Drm feature end @} */
}
