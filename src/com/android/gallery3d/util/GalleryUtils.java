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

package com.android.gallery3d.util;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.ConditionVariable;
import android.os.Environment;
import android.os.Looper;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.app.DetailsActivity;
import com.android.gallery3d.app.GalleryActivity;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.app.GalleryStorageUtil;
import com.android.gallery3d.app.PackagesMonitor;
import com.android.gallery3d.app.PhotoPage;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.tools.SaveImage;
import com.android.gallery3d.ui.DetailsHelper;
import com.android.gallery3d.ui.TiledScreenNail;
import com.android.gallery3d.util.ThreadPool.CancelListener;
import com.android.gallery3d.util.ThreadPool.JobContext;
import com.android.gallery3d.v2.app.GalleryActivity2;
import com.android.gallery3d.v2.app.MotionActivity;
import com.android.gallery3d.v2.data.CameraMergeAlbum;
import com.android.gallery3d.v2.data.SecureCameraAlbum;
import com.android.gallery3d.v2.interact.ViewActionListener;
import com.android.gallery3d.v2.util.Constants;
import com.android.gallery3d.v2.util.SdCardPermission;
import com.sprd.frameworks.StandardFrameworks;
import com.sprd.gallery3d.app.PermissionsActivity;
import com.sprd.gallery3d.app.VideoActivity;
import com.sprd.gallery3d.smarterase.SmartEraseActivity;
import com.sprd.gallery3d.tools.LargeImageProcessingUtils;
import com.sprd.refocus.RefocusUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.print.PrintHelper;

public class GalleryUtils {
    private static final String TAG = "GalleryUtils";
    private static final String MAPS_PACKAGE_NAME = "com.google.android.apps.maps";
    private static final String MAPS_CLASS_NAME = "com.google.android.maps.MapsActivity";
    private static final String MAPS_GO_PACKAGE_NAME = "com.google.android.apps.mapslite";
    private static final String MAPS_GO_CLASS_NAME = "org.chromium.webapk.shell_apk.MainActivity";
    private static final String CAMERA_LAUNCHER_NAME = "com.android.camera.CameraLauncher";

    private static final String FILE_AUTHORITY = "com.android.gallery3d.fileprovider2";

    public static final String MIME_TYPE_IMAGE = "image/*";
    public static final String MIME_TYPE_VIDEO = "video/*";
    public static final String MIME_TYPE_PANORAMA360 = "application/vnd.google.panorama360+jpg";
    public static final String MIME_TYPE_ALL = "*/*";

    private static final String DIR_TYPE_IMAGE = "vnd.android.cursor.dir/image";
    private static final String DIR_TYPE_VIDEO = "vnd.android.cursor.dir/video";

    private static final String PREFIX_PHOTO_EDITOR_UPDATE = "editor-update-";
    private static final String PREFIX_HAS_PHOTO_EDITOR = "has-editor-";

    private static final String KEY_CAMERA_UPDATE = "camera-update";
    private static final String KEY_HAS_CAMERA = "has-camera";
    private static final String TEST_INTENT_KEY = "test_key";

    private final static String TARGET_TEST_MODE = "refocus.debug";
    private final static String TARGET_NEW_JPEG_DATA = "new.jpeg.data";
    private final static String TARGET_TS_BLENDING = "persist.sys.blending.enable";
    private final static String TARGET_SPRD_BOKEH = "persist.sys.sprd.refocus.bokeh";
    private final static String TARGET_ARC_BOKEH = "persist.sys.cam.api.version";
    private final static String TARGET_SR_PROCESSING = "persist.sys.cam.sr.enable";
    private final static String TARGET_PERFORMANCE_MODE = "performance.debug";

    private static float sPixelDensity = -1f;
    private static boolean sCameraAvailableInitialized = false;
    private static boolean sCameraAvailable;
    private static boolean sPerformanceDebug;
    private static boolean sJpegHwCodecAvailable;
    public static boolean isLowRam = false;

    /* sprd: add to support play gif @{ */
    public static final String MIME_TYPE_IMAGE_GIF = "image/gif";
    public static final boolean SUPPORT_GIF = true;
    /* @} */
    // SPRD: fix bug 387548, WBMP don't support edit
    public static final String MIME_TYPE_IMAGE_WBMP = "image/vnd.wap.wbmp";
    /* SPRD: Fix Bug 535131, add slide music feature @{ */
    private static final String SLIDESHOW_MUSIC = "slideshow_music";
    private static final String SLIDESHOW_MUSIC_KEY = "slideshow_music_key";
    private static final String SLIDESHOW_MUSIC_DIALOG_SELECT = "slideshow_music_dialog_select";
    /* @} */

    public static final int DONT_SUPPORT_VIEW_PHOTOS = 1;
    public static final int DONT_SUPPORT_VIEW_VIDEO = 2;
    private static Toast mToast = null;

    public static final String ACTION_NEXTGEN_EDIT = "action_nextgen_edit";
    public static final String PERFORMANCE_TAG = "PERFORMANCE";
    public static final int DEHAZE_MAX_SIZE = 100 * 100;

    private static int sScreenWidth;
    private static int sScreenHeight;

    private static final String[] MIME_TYPE_IMAGE_RAW = new String[]{"image/x-adobe-dng", "image/x-canon-cr2", "image/x-nikon-nef",
            "image/x-nikon-nrw", "image/x-sony-arw", "image/x-panasonic-rw2", "image/x-olympus-orf",
            "image/x-fuji-raf", "image/x-pentax-pef", "image/x-samsung-srw"};

    //用于匹配motion photo文件名的正则表达式
    private static Pattern mMotionPattern = Pattern.compile("IMG_[a-zA-Z0-9_]+_MP.(JPG|jpg|JPEG|jpeg)");

    private static boolean mUseStandard = false;

    public static void initialize(Context context) {
        //
        try {
            Class.forName("com.android.gallery3d.os.UseStandard");
            mUseStandard = true;
        } catch (ClassNotFoundException e) {
            mUseStandard = false;
        }
        //
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager)
                context.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(metrics);
        sPixelDensity = metrics.density;
        Resources r = context.getResources();
        TiledScreenNail.setPlaceholderColor(r.getColor(
                R.color.bitmap_screennail_placeholder));
        initializeThumbnailSizes(metrics, r);
        /* SPRD: for bug 530910, low ram phone limit Region Decoder @{ */
        final ActivityManager am = (ActivityManager) context.getSystemService(
                Context.ACTIVITY_SERVICE);
        isLowRam = am.isLowRamDevice();
        sPerformanceDebug = isPerDebug();
        sJpegHwCodecAvailable = StandardFrameworks.getInstances().isSupportHwCodec();
        /* @} */
        sScreenWidth = metrics.widthPixels;
        sScreenHeight = metrics.heightPixels;
    }

    private static void initializeThumbnailSizes(DisplayMetrics metrics, Resources r) {
        int maxPixels = Math.max(metrics.heightPixels, metrics.widthPixels);

        // For screen-nails, we never need to completely fill the screen
        // Modify for bug#596005, to enhance image clarity.
        // original code: MediaItem.setThumbnailSizes(maxPixels / 2, maxPixels / 5);
        MediaItem.setThumbnailSizes(maxPixels, maxPixels / 5);
        TiledScreenNail.setMaxSide(maxPixels / 2);
    }

    public static float[] intColorToFloatARGBArray(int from) {
        return new float[]{
                Color.alpha(from) / 255f,
                Color.red(from) / 255f,
                Color.green(from) / 255f,
                Color.blue(from) / 255f
        };
    }

    public static float dpToPixel(float dp) {
        return sPixelDensity * dp;
    }

    public static int dpToPixel(int dp) {
        return Math.round(dpToPixel((float) dp));
    }

    public static int meterToPixel(float meter) {
        // 1 meter = 39.37 inches, 1 inch = 160 dp.
        return Math.round(dpToPixel(meter * 39.37f * 160));
    }

    public static byte[] getBytes(String in) {
        byte[] result = new byte[in.length() * 2];
        int output = 0;
        for (char ch : in.toCharArray()) {
            result[output++] = (byte) (ch & 0xFF);
            result[output++] = (byte) (ch >> 8);
        }
        return result;
    }

    // Below are used the detect using database in the render thread. It only
    // works most of the time, but that's ok because it's for debugging only.

    private static volatile Thread sCurrentThread;
    private static volatile boolean sWarned;

    public static void setRenderThread() {
        sCurrentThread = Thread.currentThread();
    }

    public static void assertNotInRenderThread() {
        if (!sWarned) {
            if (Thread.currentThread() == sCurrentThread) {
                sWarned = true;
                Log.w(TAG, new Throwable("Should not do this in render thread"));
            }
        }
    }

    private static final double RAD_PER_DEG = Math.PI / 180.0;
    private static final double EARTH_RADIUS_METERS = 6367000.0;

    public static double fastDistanceMeters(double latRad1, double lngRad1,
                                            double latRad2, double lngRad2) {
        if ((Math.abs(latRad1 - latRad2) > RAD_PER_DEG)
                || (Math.abs(lngRad1 - lngRad2) > RAD_PER_DEG)) {
            return accurateDistanceMeters(latRad1, lngRad1, latRad2, lngRad2);
        }
        // Approximate sin(x) = x.
        double sineLat = (latRad1 - latRad2);

        // Approximate sin(x) = x.
        double sineLng = (lngRad1 - lngRad2);

        // Approximate cos(lat1) * cos(lat2) using
        // cos((lat1 + lat2)/2) ^ 2
        double cosTerms = Math.cos((latRad1 + latRad2) / 2.0);
        cosTerms = cosTerms * cosTerms;
        double trigTerm = sineLat * sineLat + cosTerms * sineLng * sineLng;
        trigTerm = Math.sqrt(trigTerm);

        // Approximate arcsin(x) = x
        return EARTH_RADIUS_METERS * trigTerm;
    }

    public static double accurateDistanceMeters(double lat1, double lng1,
                                                double lat2, double lng2) {
        double dlat = Math.sin(0.5 * (lat2 - lat1));
        double dlng = Math.sin(0.5 * (lng2 - lng1));
        double x = dlat * dlat + dlng * dlng * Math.cos(lat1) * Math.cos(lat2);
        return (2 * Math.atan2(Math.sqrt(x), Math.sqrt(Math.max(0.0,
                1.0 - x)))) * EARTH_RADIUS_METERS;
    }


    public static final double toMile(double meter) {
        return meter / 1609;
    }

    // For debugging, it will block the caller for timeout millis.
    public static void fakeBusy(JobContext jc, int timeout) {
        final ConditionVariable cv = new ConditionVariable();
        jc.setCancelListener(new CancelListener() {
            @Override
            public void onCancel() {
                cv.open();
            }
        });
        cv.block(timeout);
        jc.setCancelListener(null);
    }

    public static boolean isCropAvailable(Context context, String mimeType) {
        return !GalleryUtils.MIME_TYPE_IMAGE_GIF.equals(mimeType);
    }

    public static boolean isEditorAvailable(Context context, String mimeType) {
        /* SPRD: add to support play gif @{ */
        if (GalleryUtils.MIME_TYPE_IMAGE_GIF.equals(mimeType)
                // SPRD: Add for bug421702, WBMP do not support edit
                || GalleryUtils.MIME_TYPE_IMAGE_WBMP.equals(mimeType)) {
            return false;
        }
        /* @} */
        int version = PackagesMonitor.getPackagesVersion(context);

        String updateKey = PREFIX_PHOTO_EDITOR_UPDATE + mimeType;
        String hasKey = PREFIX_HAS_PHOTO_EDITOR + mimeType;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getInt(updateKey, 0) != version) {
            PackageManager packageManager = context.getPackageManager();
            List<ResolveInfo> infos = packageManager.queryIntentActivities(
                    new Intent(Intent.ACTION_EDIT).setType(mimeType), 0);
            prefs.edit().putInt(updateKey, version)
                    .putBoolean(hasKey, !infos.isEmpty())
                    .commit();
        }

        return prefs.getBoolean(hasKey, true);
    }

    public static boolean isAnyCameraAvailable(Context context) {
        int version = PackagesMonitor.getPackagesVersion(context);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getInt(KEY_CAMERA_UPDATE, 0) != version) {
            PackageManager packageManager = context.getPackageManager();
            List<ResolveInfo> infos = packageManager.queryIntentActivities(
                    new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), 0);
            prefs.edit().putInt(KEY_CAMERA_UPDATE, version)
                    .putBoolean(KEY_HAS_CAMERA, !infos.isEmpty())
                    .commit();
        }
        return prefs.getBoolean(KEY_HAS_CAMERA, true);
    }

    public static boolean isCameraAvailable(Context context) {
        if (sCameraAvailableInitialized) {
            return sCameraAvailable;
        }
        PackageManager pm = context.getPackageManager();
        Intent cameraIntent = IntentHelper.getCameraIntent(context);
        List<ResolveInfo> apps = pm.queryIntentActivities(cameraIntent, 0);
        sCameraAvailableInitialized = true;
        sCameraAvailable = !apps.isEmpty();
        return sCameraAvailable;
    }

    public static void startCameraActivity(Context context) {
        /* SPRD: bug 473267 add video entrance */
        /* old bug info: Bug 378480 it will start camera mode when open the camera from videoplayer.
        Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NEW_TASK);
        */
        Intent intent = new Intent();
        if (context instanceof VideoActivity) {
            intent.setAction(MediaStore.INTENT_ACTION_VIDEO_CAMERA);
        } else {
            intent.setAction(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        /* @} */
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // This will only occur if Camera was disabled while Gallery is open
            // since we cache our availability check. Just abort the attempt.
            Log.e(TAG, "Camera activity previously detected but cannot be found", e);
        }
    }

    public static void startGalleryActivity(Context context) {
        Intent intent = new Intent(context, GalleryActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static boolean isValidLocation(double latitude, double longitude) {
        // TODO: change || to && after we fix the default location issue
        return (latitude != MediaItem.INVALID_LATLNG || longitude != MediaItem.INVALID_LATLNG);
    }

    public static String formatLatitudeLongitude(String format, double latitude,
                                                 double longitude) {
        // We need to specify the locale otherwise it may go wrong in some language
        // (e.g. Locale.FRENCH)
        return String.format(Locale.ENGLISH, format, latitude, longitude);
    }

    public static void showOnMap(Context context, double latitude, double longitude) {
        boolean lowRam = StandardFrameworks.getInstances().isLowRam();
        try {
            // We don't use "geo:latitude,longitude" because it only centers
            // the MapView to the specified location, but we need a marker
            // for further operations (routing to/from).
            // The q=(lat, lng) syntax is suggested by geo-team.
            // If Android Go version,use Google Maps Go, Otherwise,use Google Maps
            String uri = formatLatitudeLongitude("http://maps.google.com/maps?f=q&q=(%f,%f)",
                    latitude, longitude);
            ComponentName compName = lowRam
                    ? new ComponentName(MAPS_GO_PACKAGE_NAME, MAPS_GO_CLASS_NAME)
                    : new ComponentName(MAPS_PACKAGE_NAME, MAPS_CLASS_NAME);
            Intent mapsIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(uri)).setComponent(compName);
            context.startActivity(mapsIntent);
        } catch (ActivityNotFoundException e) {
            // Use the "geo intent" if no GMM is installed
            Log.e(TAG, "GMM activity not found!");
            try {
                // Use Third party Maps app, Like baidu Maps.
                String url = formatLatitudeLongitude("geo:%f,%f", latitude, longitude);
                Intent mapsIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                context.startActivity(mapsIntent);
            } catch (ActivityNotFoundException t) {
                Log.e(TAG, "Third party Maps app activity not found!");

                // Use browser to show location
                String url = formatLatitudeLongitude("http://maps.google.com/maps?f=q&q=(%f,%f)",
                        latitude, longitude);
                Intent mapsIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                context.startActivity(mapsIntent);
            }
        }
    }

    public static void setViewPointMatrix(
            float matrix[], float x, float y, float z) {
        // The matrix is
        // -z,  0,  x,  0
        //  0, -z,  y,  0
        //  0,  0,  1,  0
        //  0,  0,  1, -z
        Arrays.fill(matrix, 0, 16, 0);
        matrix[0] = matrix[5] = matrix[15] = -z;
        matrix[8] = x;
        matrix[9] = y;
        matrix[10] = matrix[11] = 1;
    }

    public static int getBucketId(String path) {
        if (path == null) {
            return -1;
        }
        return path.toLowerCase(Locale.US).hashCode();
    }

    // Return the local path that matches the given bucketId. If no match is
    // found, return null
    public static String searchDirForPath(File dir, int bucketId) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    String path = file.getAbsolutePath();
                    if (GalleryUtils.getBucketId(path) == bucketId) {
                        return path;
                    } else {
                        path = searchDirForPath(file, bucketId);
                        if (path != null) {
                            return path;
                        }
                    }
                }
            }
        }
        return null;
    }

    // Returns a (localized) string for the given duration (in seconds).
    public static String formatDuration(final Context context, int duration) {
        int h = duration / 3600;
        int m = (duration - h * 3600) / 60;
        int s = duration - (h * 3600 + m * 60);
        String durationValue;
        if (h == 0) {
            durationValue = String.format(context.getString(R.string.details_ms), m, s);
        } else {
            durationValue = String.format(context.getString(R.string.details_hms), h, m, s);
        }
        return durationValue;
    }

    @TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
    public static int determineTypeBits(Context context, Intent intent) {
        int typeBits = 0;
        String type = intent.resolveType(context);
        Uri uri = intent.getData();
        String action = intent.getAction();
        if (MIME_TYPE_ALL.equals(type) ||
                (uri == null && Intent.ACTION_VIEW.equals(action))) {
            typeBits = DataManager.INCLUDE_ALL;
        } else if (MIME_TYPE_IMAGE.equals(type) ||
                DIR_TYPE_IMAGE.equals(type)) {
            typeBits = DataManager.INCLUDE_IMAGE;
        } else if (MIME_TYPE_VIDEO.equals(type) ||
                DIR_TYPE_VIDEO.equals(type)) {
            typeBits = DataManager.INCLUDE_VIDEO;
        } else {
            typeBits = DataManager.INCLUDE_ALL;
        }

        if (ApiHelper.HAS_INTENT_EXTRA_LOCAL_ONLY) {
            if (intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false)) {
                typeBits |= DataManager.INCLUDE_LOCAL_ONLY;
            }
        }

        return typeBits;
    }

    public static int getSelectionModePrompt(int typeBits) {
        if ((typeBits & DataManager.INCLUDE_VIDEO) != 0) {
            return (typeBits & DataManager.INCLUDE_IMAGE) == 0
                    ? R.string.select_video
                    : R.string.select_item;
        }
        return R.string.select_image;
    }

    public static boolean hasSpaceForSize(long size) {
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            return false;
        }

        String path = Environment.getExternalStorageDirectory().getPath();
        try {
            StatFs stat = new StatFs(path);
            return stat.getAvailableBlocks() * (long) stat.getBlockSize() > size;
        } catch (Exception e) {
            Log.i(TAG, "Fail to access external storage", e);
        }
        return false;
    }

    public static boolean isPanorama(MediaItem item) {
        if (item == null) {
            return false;
        }
        int w = item.getWidth();
        int h = item.getHeight();
        return (h > 0 && w / h >= 2);
    }

    /**
     * SPRD:Bug510007  check storage permission  @{
     */
    public static boolean checkStoragePermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
    /** @ } */

    /**
     * SPRD:Bug 474639 add phone call reaction @{
     */
    public static boolean checkReadPhonePermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /**
     * @}
     */

    /* SPRD: Modify for bug576760, check access location permission for Gallery @{ */
    public static boolean checkLocationPermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
    /* @} */

    /* SPRD: Modify for bug592606, check access sms permission for Gallery @{ */
    public static boolean checkSmsPermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkSelfPermission(Manifest.permission.RECEIVE_SMS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
    /* Bug592606 end @} */

    public static boolean checkCameraPermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkSelfPermission(Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /* SPRD: Fix Bug 535131, bug 569414 add slide music feature @{ */
    public static String getSlideMusicUri(Context context) {
        String uriString = context.getSharedPreferences(SLIDESHOW_MUSIC, Context.MODE_PRIVATE).getString(SLIDESHOW_MUSIC_KEY, null);
        return uriString;
    }

    public static void setSlideMusicUri(Context context, String uriString) {
        Editor editor = context.getSharedPreferences(SLIDESHOW_MUSIC, Context.MODE_PRIVATE).edit();
        if (editor != null) {
            editor.putString(SLIDESHOW_MUSIC_KEY, uriString);
            editor.commit();
        }
    }
    /* @} */

    public static boolean isMonkey() {
        return ActivityManager.isUserAMonkey();
    }

    public static void saveSelected(Context context, int selected) {
        Editor editor = context.getSharedPreferences(SLIDESHOW_MUSIC, Context.MODE_PRIVATE).edit();
        if (editor != null) {
            editor.putInt(SLIDESHOW_MUSIC_DIALOG_SELECT, selected);
            editor.commit();
        }
    }

    public static int getSelected(Context context) {
        int selected = context.getSharedPreferences(SLIDESHOW_MUSIC, Context.MODE_PRIVATE).getInt(SLIDESHOW_MUSIC_DIALOG_SELECT, 0);
        return selected;
    }
    /* @} */

    public static void startDetailsActivity(Activity context, DetailsHelper.DetailsSource source) {
        // DetailsActivity.setDetailsSource(source);
        // DetailsActivity.setAbstractGalleryActivity(context);
        Intent intent = new Intent(context, DetailsActivity.class);
        try {
            intent.putExtra("Index", source.setIndex());
        } catch (Exception e) {
            Log.d(TAG, "startDetailsActivity putExtra Exception : ");
            e.printStackTrace();
        }
        intent.putExtra("Size", source.size());
        intent.putExtra("Details", source.getDetails());
        intent.putExtra(PhotoPage.KEY_SECURE_CAMERA, context.getIntent().getBooleanExtra(PhotoPage.KEY_SECURE_CAMERA, false));
        context.startActivity(intent);
    }

    public static boolean killActivityInMultiWindow(Activity context, int photoOrvideo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && context.isInMultiWindowMode()) {
            int id = 0;
            if (photoOrvideo == DONT_SUPPORT_VIEW_VIDEO) {
                id = R.string.exit_multiwindow_video_tips;
            } else if (photoOrvideo == DONT_SUPPORT_VIEW_PHOTOS) {
                id = R.string.exit_multiwindow_tips;
            } else {
                return false;
            }

            if (mToast == null) {
                mToast = Toast.makeText(GalleryAppImpl.getApplication(), id, Toast.LENGTH_SHORT);
            } else {
                mToast.setText(id);
            }
            mToast.show();
            Log.d(TAG, "killActivityInMultiWindow");
            context.finish();
            mToast = null;
            return true;
        }
        return false;
    }

    /* SPRD :bug 630329 file Non-existent,get permissions and open it crash. */
    public static boolean isValidUri(Context context, Uri uri) {
        if (uri == null) {
            return false;
        }
        try {
            AssetFileDescriptor f = context.getContentResolver().openAssetFileDescriptor(uri, "r");
            f.close();
            return true;
        } catch (Throwable e) {
            Log.w(TAG, "cannot open uri: " + uri, e);
            return false;
        }
    }
    /* @} */

    public static void animatorIn(View viewColor, View viewAlpha, long duration) {
        if (viewColor != null) {
            ObjectAnimator animator = ObjectAnimator.ofInt(viewColor, "backgroundColor", Color.parseColor("#1A1A1A"), Color.parseColor("#FFFFFF"));
            animator.setEvaluator(new ArgbEvaluator());
            animator.setInterpolator(new LinearInterpolator());
            animator.setDuration(duration).start();
        }
        if (viewAlpha != null) {
            ObjectAnimator animator = ObjectAnimator.ofFloat(viewAlpha, "alpha", 0.3f, 1.0f);
            animator.setInterpolator(new LinearInterpolator());
            animator.setDuration(duration).start();
        }
    }

    public static Size getImageSize(Context context, int resId) {
        Drawable drawable = context.getDrawable(resId);
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        return new Size(width, height);
    }

    public static void animatorAlpha(final View coverView, long duration) {
        if (coverView != null) {
            coverView.setVisibility(View.VISIBLE);
            AlphaAnimation animation = new AlphaAnimation(1.0f, 0.2f);
            animation.setDuration(duration);
            animation.setInterpolator(new AccelerateInterpolator());
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    coverView.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            coverView.startAnimation(animation);
        }
    }

    public static void launchEditor(Activity activity, MediaItem current, int requestCode) {
        if (current == null || (current.getSupportedOperations()
                & MediaObject.SUPPORT_EDIT) == 0) {
            return;
        }

        Intent intent = new Intent(ACTION_NEXTGEN_EDIT);

        intent.setDataAndType(current.getContentUri(), current.getMimeType())
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (activity.getPackageManager()
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).size() == 0) {
            intent.setAction(Intent.ACTION_EDIT);
        }
        intent.putExtra(FilterShowActivity.LAUNCH_FULLSCREEN, false);
        if (requestCode == -1) {
            activity.startActivity(Intent.createChooser(intent, null));
        } else {
            activity.startActivityForResult(Intent.createChooser(intent, null),
                    requestCode);
        }
    }

    public static void launchEditor(Fragment fragment, MediaItem current, int requestCode) {
        if (current == null || (current.getSupportedOperations()
                & MediaObject.SUPPORT_EDIT) == 0) {
            return;
        }

        Intent intent = new Intent(ACTION_NEXTGEN_EDIT);

        intent.setDataAndType(current.getContentUri(), current.getMimeType())
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (fragment.getActivity().getPackageManager()
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).size() == 0) {
            intent.setAction(Intent.ACTION_EDIT);
        }
        intent.putExtra(FilterShowActivity.LAUNCH_FULLSCREEN, false);
        if (requestCode == -1) {
            fragment.startActivity(Intent.createChooser(intent, null));
        } else {
            fragment.startActivityForResult(Intent.createChooser(intent, null),
                    requestCode);
        }
    }

    public static void launchSmartErase(Context context, Uri uri) {
        Log.d(TAG, "launchSmartErase: uri=" + uri);
        Intent intent = new Intent(context, SmartEraseActivity.class);
        intent.setData(uri);
        context.startActivity(intent);
    }

    public static boolean requestPermission(Activity fromActivity, Class<?> targetActivity, int startFrom) {
        Intent fromIntent = fromActivity.getIntent();
        Intent intent = new Intent(fromActivity, targetActivity);
        if (fromIntent.getAction() != null) {
            intent.setAction(fromIntent.getAction());
        }
        if (fromIntent.getType() != null) {
            intent.setType(fromIntent.getType());
        }
        if (fromIntent.getData() != null) {
            intent.setData(fromIntent.getData());
        }
        if (fromIntent.getExtras() != null) {
            intent.putExtras(fromIntent.getExtras());
        }
        intent.putExtra(PermissionsActivity.UI_START_BY, startFrom);
        if (!fromActivity.isFinishing()) {
            Log.d(TAG, "requestPermission startActivity intent=" + intent, new Throwable());
            fromActivity.startActivityForResult(intent, PermissionsActivity.START_PERIMISSION_SUCESS);
            return true;
        }
        return false;
    }

    // SPRD:  Fix bug 659304, there is a general denial of service vulnerability  for intent with some malformed extras.
    public static boolean isAlnormalIntent(Intent intent) {
        boolean isAlnormalIntent = false;
        try {
            intent.getIntExtra(TEST_INTENT_KEY, -1);
        } catch (RuntimeException e) {
            Log.e(TAG, "alnormal intent is:" + intent, e);
            isAlnormalIntent = true;
        }
        return isAlnormalIntent;
    }

    public static boolean isValidMtpUri(Context context, Uri uri) {
        boolean uriValid = true;
        if (isMtpUri(uri)) {
            InputStream ips = null;
            try {
                ips = context.getContentResolver().openInputStream(uri);
                byte[] buf = new byte[16];
                while (ips.read(buf) > 0) {
                    break;
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                uriValid = false;
            } catch (IOException ioe) {
                ioe.printStackTrace();
                uriValid = false;
            }
            if (ips != null) {
                try {
                    ips.close();
                } catch (IOException e) {
                }
            }
            Log.d(TAG, "isValidMtpUri : " + uriValid);
        }
        return uriValid;
    }

    public static boolean isMtpUri(Uri uri) {
        return "com.android.mtp.documents".equals(uri.getAuthority());
    }

    public static boolean isBlendingEnable() {
        boolean BlendingEnable = StandardFrameworks.getInstances().getBooleanFromSystemProperties(TARGET_TS_BLENDING, false);
        return BlendingEnable;
    }

    public static boolean isPerDebug() {
        return StandardFrameworks.getInstances().getBooleanFromSystemProperties(TARGET_PERFORMANCE_MODE, true);
    }

    /**
     * get persist.sys.cam.ba.blur.version
     *
     * @return 1 -> blur1.0, blur1.2
     * 3 -> blur3.0
     * 6 -> real-bokeh (sprd, arc)
     * 7 -> sbs preview
     * 8 -> sbs capture
     */


    public static boolean isFileUri(Uri sourceUri) {
        String scheme = sourceUri.getScheme();
        return scheme != null && scheme.equals(ContentResolver.SCHEME_FILE);
    }

    public static Uri transFileToContentType(Uri uri, Context context) {
        Uri contentUri = uri;
        if (isFileUri(uri)) {
            File file = new File(uri.getPath());
            contentUri = FileProvider.getUriForFile(context, FILE_AUTHORITY, file);
        }
        return contentUri;
    }


    private static int getInternalDimensionSize(Resources res, String key) {
        int result = 0;
        int resourceId = res.getIdentifier(key, "dimen", "android");
        if (resourceId > 0) {
            result = res.getDimensionPixelSize(resourceId);
        }
        Log.d(TAG, "getInternalDimensionSize " + key + " " + result);
        return result;
    }

    public static int getStatusBarHeight(Activity activity) {
        return getInternalDimensionSize(activity.getResources(), "status_bar_height");
    }

    public static Uri transformUri(Uri uri, Context context) {
        Log.d(TAG, "transformUri start uri = " + uri);
        if (uri != null) {
            if (uri.getAuthority().equals("com.android.providers.media.documents")) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String id = split[1];
                uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath(split[1])
                        .build();
            } else if (uri.getAuthority().equals("com.android.externalstorage.documents")) {
                final String docId = DocumentsContract.getDocumentId(uri);
                String[] split = docId.split(":");
                String filePath = null;
                if (split.length >= 2) {
                    String type = split[0];
                    if ("primary".equalsIgnoreCase(type)) {
                        filePath = Environment.getExternalStorageDirectory() + "/"
                                + split[1];
                    }
                }
                Log.i(TAG, "transformUri filePath =  " + filePath);
                uri = Uri.parse(filePath);
            } else if (uri.getAuthority().equals("com.android.providers.downloads.documents")) {
                final String docId = DocumentsContract.getDocumentId(uri);
                String filePath = null;
                Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(docId));
                filePath = getDataColumn(context, contentUri, null, null);
                uri = Uri.parse(filePath);
            }

        }
        Log.d(TAG, "transformUri end uri = " + uri);
        return uri;
    }

    public static String getDataColumn(Context context, Uri uri, String selection,
                                       String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {column};

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } catch (Exception e) {
            Log.d(TAG, "getDataColumn Exception e = " + e);
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    public static Uri transFileToContentUri(Context context, File file) {
        return FileProvider.getUriForFile(context, FILE_AUTHORITY, file);
    }

    public static String generateVideoName() {
        SimpleDateFormat format = new SimpleDateFormat("'VID'_yyyyMMdd_HHmmss");
        return format.format(new Date(System.currentTimeMillis()));
    }

    public static String generateImageName(long time) {
        SimpleDateFormat format = new SimpleDateFormat("'IMG'_yyyyMMdd_HHmmss");
        return format.format(new Date(time));
    }

    public static String getFilePath(Uri uri, Context context) {
        String filePath = "unknown";
        if (uri == null || context == null) {
            return filePath;
        }
        String scheme = uri.getScheme();
        if (!TextUtils.isEmpty(scheme)) {
            if (scheme.equals(ContentResolver.SCHEME_CONTENT)) {
                Cursor cursor = null;
                try {
                    cursor = context.getContentResolver().query(uri,
                            new String[]{MediaStore.Files.FileColumns.DATA},
                            null, null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        filePath = cursor.getString(0);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "getFilePath error:" + e.toString());
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            } else if (scheme.equals(ContentResolver.SCHEME_FILE)) {
                filePath = uri.getPath();
            }
        }
        return filePath;
    }

    public static boolean isSprdPhotoEdit() {
        boolean isPhotoEdit = GalleryAppImpl.getApplication().getResources().getBoolean(R.bool.is_sprdphoto_edit);
        return isPhotoEdit;
    }

    public static void start(Class className, String medthedName) {
        if (sPerformanceDebug) {
            Log.d(PERFORMANCE_TAG + " " + className.getSimpleName(), medthedName + ", start.");
        }
    }

    public static void end(Class className, String medthedName) {
        if (sPerformanceDebug) {
            Log.d(PERFORMANCE_TAG + " " + className.getSimpleName(), medthedName + ", end.");
        }
    }

    public static void logs(Class className, String logs) {
        if (sPerformanceDebug) {
            Log.d(PERFORMANCE_TAG + " " + className.getSimpleName(), logs);
        }
    }

    public static boolean isJpegHwCodecAvailable() {
        return sJpegHwCodecAvailable;
    }

    public static String getContentType(Context context, Intent intent) {
        String type = intent.getType();
        if (type != null) {
            return GalleryUtils.MIME_TYPE_PANORAMA360.equals(type)
                    ? MediaItem.MIME_TYPE_JPEG : type;
        }
        Uri uri = intent.getData();
        try {
            return LargeImageProcessingUtils.getType(uri, context);
        } catch (Throwable t) {
            Log.e(TAG, "get type fail", t);
            return null;
        }
    }

    public static boolean needChangeToContent(Uri uri) {
        return uri.getScheme().compareTo("content") != 0;
    }

    public static Uri changeToContent(Context context, Uri uri) {
        Uri baseUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Uri query = baseUri.buildUpon().appendQueryParameter("limit", "1").build();
        String[] projection = new String[]{
                MediaStore.Images.ImageColumns._ID
        };
        String selection = MediaStore.Images.ImageColumns.DATA + '=' + "\"" + uri.getPath() + "\"";

        Cursor cursor = null;
        long id = -1;
        try {
            cursor = context.getContentResolver().query(query, projection, selection, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                id = cursor.getLong(0);
            }
        } catch (Exception e) {
            Log.w(TAG, "changeToContent error:" + e.toString());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (id != -1) {
            uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath(String.valueOf(id))
                    .build();
        }
        return uri;
    }

    public static void onViewAction(GalleryActivity2 context, ViewActionListener listener) {
        DataManager dataManager = context.getDataManager();
        Uri uri = context.getIntent().getData();
        String contentType = getContentType(context, context.getIntent());
        if (contentType == null) {
            Toast.makeText(context, R.string.no_such_item, Toast.LENGTH_SHORT).show();
            context.finish();
            return;
        }
        if (uri == null) {
            Log.d(TAG, "onViewAction uri is null");
            if (listener != null) {
                listener.onViewAction(null, null);
            }
        } else {
            if (needChangeToContent(uri)) {
                uri = changeToContent(context, uri);
            }
            Path itemPath = dataManager.findPathByUri(uri, contentType);
            Path albumPath = dataManager.getDefaultSetOf(false, itemPath, context.getIntent().getAction());

            if (context.getIntent().getBooleanExtra(Constants.KEY_CAMERA_ALBUM, false) && albumPath != null) {
                String ap = albumPath.toString();
                if (ap != null) {
                    albumPath = CameraMergeAlbum.PATH;
                    context.getIntent().putExtra(Constants.KEY_CAMERA_ALBUM, true);
                    boolean isSecureCamera = context.getIntent().getBooleanExtra(PhotoPage.KEY_SECURE_CAMERA, false);
                    long[] photoIds = context.getIntent().getLongArrayExtra(Constants.KEY_SECURE_CAMERA_PHOTOS_IDS);
                    if (isSecureCamera && photoIds != null) {
                        albumPath = SecureCameraAlbum.PATH;
                        Log.d(TAG, "onViewAction use SecureCameraAlbum");
                    }
                    context.getIntent().putExtra(Constants.KEY_SECURE_CAMERA, isSecureCamera);
                    context.getIntent().putExtra(Constants.KEY_SECURE_CAMERA_PHOTOS_IDS, photoIds);
                    context.getIntent().putExtra(Constants.KEY_SECURE_CAMERA_ENTER_TIME, photoIds != null ? -1L : context.getIntent().getLongExtra(PhotoPage.KEY_SECURE_CAMERA_ENTER_TIME, -1L));
                }
            }

            if (!GalleryUtils.isValidMtpUri(context, uri)) {
                Toast.makeText(context, R.string.fail_to_load, Toast.LENGTH_SHORT).show();
                context.finish();
                return;
            }

            if (listener != null) {
                listener.onViewAction(albumPath != null ? albumPath.toString() : null, itemPath.toString());
            }
        }
    }

    public static void launchMotionActivity(Activity activity, MediaItem item) {
        MotionActivity.launch(activity, item);
    }

    public static void launchMotionActivity(Fragment fragment, MediaItem item) {
        MotionActivity.launch(fragment, item);
    }

    public static int getScreenWidth() {
        return sScreenWidth;
    }

    public static int getScreenHeight() {
        return sScreenHeight;
    }

    public static boolean isMotionPhoto(String name, int flag) {
        Matcher matcher = mMotionPattern.matcher(name);
        return matcher.matches() || flag == LocalImage.IMG_TYPE_MODE_MOTION_PHOTO
                || flag == LocalImage.IMG_TYPE_MODE_MOTION_HDR_PHOTO
                || flag == LocalImage.IMG_TYPE_MODE_MOTION_AI_PHOTO
                || flag == LocalImage.IMG_TYPE_MODE_MOTION_HDR_AI_PHOTO
                || flag == LocalImage.IMG_TYPE_MODE_MOTION_FDR_PHOTO
                || flag == LocalImage.IMG_TYPE_MODE_MOTION_FDR_AI_PHOTO;
    }

    public static void printerBitmap(PrintHelper printer,
                                     String path,
                                     Uri uri,
                                     PrintHelper.OnPrintFinishCallback callback,
                                     Looper looper) throws FileNotFoundException {
        printer.printBitmap(path, uri, callback);
    }

    public static boolean isSprdPlatform() {
        return !mUseStandard;
    }

    public static boolean isSupportV2UI() {
        return GalleryAppImpl.getApplication().getResources().getBoolean(R.bool.is_support_v2_ui);
    }

    public static boolean isSupportRecentlyDelete() {
        return isSupportV2UI();
    }

    public static void saveBokehJpeg(Context context, byte[] data, Uri uri, String path) {
        boolean success = true;
        OutputStream output = null;
        File src = new File(path);
        File dst = new File(path + ".tmp");
        Log.d(TAG, "saveBokehJpeg src = " + src.getAbsolutePath() + ", dst = " + dst.getAbsolutePath());
        try {
            if (!GalleryStorageUtil.isInInternalStorage(dst.getAbsolutePath())) {
                //先创建文件
                SdCardPermission.mkFile(dst);
                output = SdCardPermission.createExternalOutputStream(dst.getAbsolutePath());
            } else {
                output = new FileOutputStream(dst);
            }
            output.write(data);
            output.flush();
            if (src.delete()) {
                Log.d(TAG, "saveNewJpeg delete src success.");
                if (dst.renameTo(src)) {
                    Log.d(TAG, "saveNewJpeg renameTo to src success.");
                    if (!TextUtils.isEmpty(path)) {
                        Log.d(TAG, "save new jpeg, and scanFile!");
                        android.media.MediaScannerConnection.scanFile(context,
                                new String[]{path}, null, null);
                    }
                } else {
                    Log.d(TAG, "saveNewJpeg renameTo to src error.");
                    success = false;
                }
            } else {
                Log.e(TAG, "saveNewJpeg delete src error.");
                success = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "saveNewJpeg error", e);
            success = false;
        } finally {
            Utils.closeSilently(output);
        }
        if (!success) {
            if (dst.exists() && dst.delete()) {
                Log.d(TAG, "saveNewJpeg delete dst = " + dst.getAbsolutePath());
            }
            saveNewJpeg(context, data, uri, path);
        }
    }

    public static void saveNewJpeg(Context context, byte[] data, Uri uri, String path) {
        OutputStream outputStream = null;
        try {
            if (GalleryStorageUtil.isInInternalStorage(path)) {
                outputStream = context.getContentResolver().openOutputStream(uri);
            } else {
                outputStream = SdCardPermission.createExternalOutputStream(path);
            }
            outputStream.write(data);
            if (!TextUtils.isEmpty(path)) {
                Log.d(TAG, "save new jpeg,and scanFile !");
                android.media.MediaScannerConnection.scanFile(context,
                        new String[]{path}, null, null);
            }
        } catch (IOException e) {
            Log.e(TAG, "Exception while writing debug jpeg file", e);
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (Throwable t) {
                // do nothing
            }
        }
    }

    public static Uri saveJpegByYuv(Context context, byte[] bytes, int yuvW, int yuvH, int rotate, Uri uri, String originPath, String fNum) {
        OutputStream output = null;
        Uri result = null;
        try {
            byte[] jdata = GalleryUtils.isJpegHwCodecAvailable()
                    ? RefocusUtils.hwYuv2jpeg(bytes, yuvW, yuvH, rotate)
                    : RefocusUtils.yuv2jpeg(bytes, yuvW, yuvH, rotate);
            File dest = SaveImage.getNewFile(context, uri);
            Log.d(TAG, "saveJpegByYuv dest = " + dest);
            String destPath = dest.getAbsolutePath();
            if (!GalleryStorageUtil.isInInternalStorage(dest.getAbsolutePath())) {
                //先创建文件
                SdCardPermission.mkFile(dest);
                output = SdCardPermission.createExternalOutputStream(dest.getAbsolutePath());
            } else {
                output = new FileOutputStream(dest);
            }
            output.write(jdata);
            output.flush();
            copyJpegExifInfo(originPath, destPath, fNum);
            long time = System.currentTimeMillis();
            result = SaveImage.linkNewFileToUri(context, uri, dest, time, false);
            if (rotate == 90 || rotate == 270){
                SaveImage.updateFileByUri(context, result, yuvH, yuvW);
            }else{
                SaveImage.updateFileByUri(context, result, yuvW, yuvH);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null != output) {
                try {
                    output.close();
                } catch (Exception t) {
                    t.printStackTrace();
                }
            }
        }
        return result;
    }

    public static void copyJpegExifInfo(String originFile, String targetFile, String fNum) {
        try {
            ExifInterface oldExif = new ExifInterface(originFile);
            ExifInterface newExif = new ExifInterface(targetFile);

            Class<ExifInterface> exifCls = ExifInterface.class;
            Field[] fields = exifCls.getFields();

            for (Field f : fields) {
                String name = f.getName();
                if (!TextUtils.isEmpty(name) && name.startsWith("TAG_")) {
                    Object attr = f.get(exifCls);
                    if (attr == null) {
                        continue;
                    }
                    if(attr.toString().equalsIgnoreCase("FNumber")){
                        newExif.setAttribute(attr.toString(),fNum);
                        continue;
                    }
                    String value = oldExif.getAttribute(attr.toString());
                    if (value == null) {
                        continue;
                    }
                    newExif.setAttribute(attr.toString(), value);
                }
            }
            newExif.saveAttributes();
            Log.d(TAG, "write exif success.");
        } catch (Exception e) {
            Log.e(TAG, "addExifInfo error.", e);
        }
    }

    public static boolean isRawPhoto(String type) {
        int i = MIME_TYPE_IMAGE_RAW.length;
        while (i-- > 0) {
            if (type != null && type.equalsIgnoreCase(MIME_TYPE_IMAGE_RAW[i])) {
                return true;
            }
        }
        return false;
    }

    public static int getMediaType(String mimeType, int fileFlag) {
        if (mimeType != null) {
            if (isRawPhoto(mimeType)) {
                return MediaObject.MEDIA_TYPE_IMAGE_RAW;
            }
            /* SPRD: add to support play gif @{ */
            if (SUPPORT_GIF && mimeType.equalsIgnoreCase("image/gif")) {
                return MediaObject.MEDIA_TYPE_GIF;
            }
            /* @} */
            /* SPRD: fix bug 387548, WBMP don't support edit @{ */
            if (mimeType.equalsIgnoreCase("image/vnd.wap.wbmp")) {
                return MediaObject.MEDIA_TYPE_IMAGE_WBMP;
            }
            /* @} */
            if (fileFlag == LocalImage.IMG_TYPE_MODE_THUMBNAIL) {
                return MediaObject.MEDIA_TYPE_IMAGE_THUMB;
            }
            if (fileFlag == LocalImage.IMG_TYPE_MODE_BLUR || fileFlag == LocalImage.IMG_TYPE_MODE_BLUR_GALLERY) {
                return MediaObject.MEDIA_TYPE_IMAGE_BLUR;
            }
            if (fileFlag == LocalImage.IMG_TYPE_MODE_BOKEH_HDR_GALLERY) {
                return MediaObject.MEDIA_TYPE_IMAGE_BOKEH_HDR_GALLERY;
            }
            if (fileFlag == LocalImage.IMG_TYPE_MODE_BOKEH_HDR) {
                return MediaObject.MEDIA_TYPE_IMAGE_BOKEH_HDR;
            }
            if (fileFlag == LocalImage.IMG_TYPE_MODE_BOKEH) {
                return MediaObject.MEDIA_TYPE_IMAGE_BOKEH;
            }
            if (fileFlag == LocalImage.IMG_TYPE_MODE_BOKEH_GALLERY) {
                return MediaObject.MEDIA_TYPE_IMAGE_BOKEH_GALLERY;
            }
            if (fileFlag == LocalImage.IMG_TYPE_MODE_BURST_COVER) {
                return MediaObject.MEDIA_TYPE_IMAGE_BURST_COVER;
            }
            if (fileFlag == LocalImage.IMG_TYPE_MODE_BURST) {
                return MediaObject.MEDIA_TYPE_IMAGE_BURST;
            }
            if (fileFlag == LocalImage.IMG_TYPE_MODE_HDR) {
                return MediaObject.MEDIA_TYPE_IMAGE_HDR;
            }
            if (fileFlag == LocalImage.IMG_TYPE_MODE_HDR_AUDIO_CAPTURE) {
                return MediaObject.MEDIA_TYPE_IMAGE_VHDR;
            }
            if (fileFlag == LocalImage.IMG_TYPE_MODE_AUDIO_CAPTURE) {
                return MediaObject.MEDIA_TYPE_IMAGE_PHOTO_VOICE;
            }

            if (fileFlag == LocalImage.IMG_TYPE_MODE_AI_SCENE) {
                return MediaObject.MEDIA_TYPE_IMAGE_AI_SCENE;
            }

            if (fileFlag == LocalImage.IMG_TYPE_MODE_MOTION_PHOTO) {
                return MediaObject.MEDIA_TYPE_IMAGE_MOTION_PHOTO;
            }

            if (fileFlag == LocalImage.IMG_TYPE_MODE_AI_SCENE_HDR) {
                return MediaObject.MEDIA_TYPE_IMAGE_AI_SCENE_HDR;
            }

            if (fileFlag == LocalImage.IMG_TYPE_MODE_MOTION_HDR_PHOTO) {
                return MediaObject.MEDIA_TYPE_IMAGE_MOTION_HDR_PHOTO;
            }

            if (fileFlag == LocalImage.IMG_TYPE_MODE_MOTION_AI_PHOTO) {
                return MediaObject.MEDIA_TYPE_IMAGE_MOTION_AI_PHOTO;
            }

            if (fileFlag == LocalImage.IMG_TYPE_MODE_MOTION_HDR_AI_PHOTO) {
                return MediaObject.MEDIA_TYPE_IMAGE_MOTION_HDR_AI_PHOTO;
            }

            /*supprot FDR pic */
            if (fileFlag == LocalImage.IMG_TYPE_MODE_BOKEH_FDR_GALLERY) {
                return MediaObject.MEDIA_TYPE_IMAGE_BOKEH_FDR_GALLERY;
            }
            if (fileFlag == LocalImage.IMG_TYPE_MODE_BOKEH_FDR) {
                return MediaObject.MEDIA_TYPE_IMAGE_BOKEH_FDR;
            }
            if (fileFlag == LocalImage.IMG_TYPE_MODE_FDR) {
                return MediaObject.MEDIA_TYPE_IMAGE_FDR;
            }
            if (fileFlag == LocalImage.IMG_TYPE_MODE_FDR_AUDIO_CAPTURE) {
                return MediaObject.MEDIA_TYPE_IMAGE_VFDR;
            }
            if (fileFlag == LocalImage.IMG_TYPE_MODE_AI_SCENE_FDR) {
                return MediaObject.MEDIA_TYPE_IMAGE_AI_SCENE_FDR;
            }
            if (fileFlag == LocalImage.IMG_TYPE_MODE_MOTION_FDR_PHOTO) {
                return MediaObject.MEDIA_TYPE_IMAGE_MOTION_FDR_PHOTO;
            }
            if (fileFlag == LocalImage.IMG_TYPE_MODE_MOTION_FDR_AI_PHOTO) {
                return MediaObject.MEDIA_TYPE_IMAGE_MOTION_FDR_AI_PHOTO;
            }
        }
        return MediaObject.MEDIA_TYPE_IMAGE;
    }

    /**
     * 添加JPEG图片exif信息
     * 将exif信息从 原始图片 中拷贝到 目标图片 中
     *
     * @param originFile 原始图片
     * @param targetFile 目标图片
     */
    public static void copyExifInfo(String originFile, String targetFile) {
        try {
            ExifInterface oldExif = new ExifInterface(originFile);
            ExifInterface newExif = new ExifInterface(targetFile);

            Class<ExifInterface> exifCls = ExifInterface.class;
            Field[] fields = exifCls.getFields();

            for (Field f : fields) {
                String name = f.getName();
                if (!TextUtils.isEmpty(name) && name.startsWith("TAG_")) {
                    //过滤掉宽,高信息, 因为保存后图片与原始图片大小信息不一样
                    if ("TAG_IMAGE_LENGTH".equals(name)
                            || "TAG_IMAGE_WIDTH".equals(name)
                            || "TAG_PIXEL_X_DIMENSION".equals(name)
                            || "TAG_PIXEL_Y_DIMENSION".equals(name)
                            || "TAG_THUMBNAIL_IMAGE_LENGTH".equals(name)
                            || "TAG_THUMBNAIL_IMAGE_WIDTH".equals(name)
                            || "TAG_XMP".equals(name)) {
                        continue;
                    }
                    Object attr = f.get(exifCls);
                    if (attr == null) {
                        continue;
                    }
                    String value = oldExif.getAttribute(attr.toString());
                    if (value == null) {
                        continue;
                    }
                    newExif.setAttribute(attr.toString(), value);
                }
            }
            newExif.saveAttributes();
            Log.d(TAG, "write exif success.");
        } catch (Exception e) {
            Log.e(TAG, "addExifInfo error.", e);
        }
    }

    public static boolean isSupportSmartErase() {
        //return StandardFrameworks.getInstances().isSupportAIEngine();
        return false;
    }
}
