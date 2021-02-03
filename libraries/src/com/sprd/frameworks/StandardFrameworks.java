package com.sprd.frameworks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.drm.DrmManagerClient;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.hardware.camera2.CaptureResult;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSessionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.VideoView;

import com.android.fw.MediaPlayerMetaData;
import com.android.fw.OnMediaKeyListener;
import com.android.fw.SprdFramewoks;
import com.android.fw.StorageEventListener;
import com.android.fw.YuvCodec;

import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;

public class StandardFrameworks {
    private static final String TAG = StandardFrameworks.class.getSimpleName();

    public static final int STATE_UNMOUNTED = 0;

    public static final int STATE_MOUNTED = 2;

    private static StandardFrameworks mInstance;

    private static boolean useStandard() {
        try {
            Class.forName("com.android.gallery3d.os.UseStandard");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static StandardFrameworks getInstances() {
        if (mInstance == null) {
            synchronized (StandardFrameworks.class) {
                if (mInstance == null) {
                    if (useStandard()) {
                        mInstance = new StandardFrameworks();
                    } else {
                        try {
                            mInstance = new SprdFramewoks();
                        } catch (Exception | NoClassDefFoundError e) {
                            Log.e(TAG, "error in new Instance, " + e.toString());
                            mInstance = new StandardFrameworks();
                        }
                    }
                }
                Log.e(TAG, "use " + mInstance);
            }
        }
        return mInstance;
    }

    public CaptureResult.Key<Integer> getSprdCaptureResult() {
        return null;
    }

    public int getSprdFaceUpDownSensorType() {
        return -1;
    }

    public int getSprdHubFlipSensorType() {
        return -1;
    }

    public int getSettingPlayControl(Context context) {
        return 0;
    }

    public MediaPlayer getMediaPlayerEx() {
        return new MediaPlayer();
    }

    public void setOnMediaKeyListener(MediaSessionManager mediaSessionManager, OnMediaKeyListener onMediaKeyListener) {
    }

    public void setVideoViewRendererAndSubtitleAnchor(Context context, MediaPlayer mediaPlayer, VideoView videoView) {
    }

    public MediaPlayerMetaData getPlayerMetaData(MediaPlayer mediaPlayer) {
        return new MediaPlayerMetaData();
    }

    public int getCallState(TelephonyManager telephonyManager, int i) {
        return telephonyManager.getCallState();
    }

    public int getCallingUserId() {
        return 0;
    }

    public int getSimState(TelephonyManager telephonyManager, int i) {
        return TelephonyManager.SIM_STATE_UNKNOWN;
    }

    public Intent setFlags(Intent intent) {
        return intent;
    }

    public void addWinParams(WindowManager.LayoutParams layoutParams) {
    }

    public int getSystemUIHideSurfaceFlag() {
        return View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
    }

    public void setDisablePreviewScreenshots(Activity activity, boolean b) {
    }

    public int[] getActiveSubscriptionIdList(SubscriptionManager subscriptionManager) {
        return new int[0];
    }

    public boolean getIntentisAccessUriMode(Activity activity) {
        return false;
    }

    public void runTimeSetTargetHeapUtilization(float v) {
    }

    public boolean getBooleanFromSystemProperties(String key, boolean def) {
        return def;
    }

    public int getIntFromSystemProperties(String key, int def) {
        return def;
    }

    public String getStringSystemProperties(String key, String def) {
        return def;
    }

    public Object registerStorageManagerListener(StorageManager storageManager, StorageEventListener storageEventListener) {
        return null;
    }

    public void unregisterStorageManagerListener(StorageManager storageManager, Object o) {
    }

    public File getExternalStoragePath(Context context) {
        StorageManager sm = context.getSystemService(StorageManager.class);
        List<StorageVolume> volumes = sm.getStorageVolumes();
        StorageVolume sdCard = null;
        for (StorageVolume v : volumes) {
            if (v.isRemovable() && "mounted".equals(v.getState())) {
                sdCard = v;
                break;
            }
        }
        if (sdCard != null) {
            try {
                Class<StorageVolume> c = StorageVolume.class;
                Method m = c.getMethod("getPathFile");
                m.setAccessible(true);
                return (File) m.invoke(sdCard);
            } catch (Exception ignored) {
            }
        }
        return new File("/storage/sdcard0");
    }

    public String getInternalFilesDir(Context context) {
        File dir = context.getExternalFilesDir(null);
        return dir != null ? dir.getAbsolutePath() : null;
    }

    public String getExternalFilesDir(Context context, String srcPath) {
        File[] files = context.getExternalFilesDirs(null);
        if (files == null) {
            return null;
        }
        String sdRoot;
        if (srcPath == null) {
            sdRoot = getExternalStoragePath(context).getAbsolutePath();
        } else {
            sdRoot = getStorageDirectory(srcPath);
        }

        for (File f : files) {
            if (f != null && f.getAbsolutePath().startsWith(sdRoot)) {
                return f.getAbsolutePath();
            }
        }
        return null;
    }

    public String getStorageDirectory(String filePath) {
        String[] sp = filePath.split("/", 6);
        int index = -1;
        for (int i = 0; i < sp.length; i++) {
            if ("storage".equalsIgnoreCase(sp[i])) {
                index = i;
                break;
            }
        }
        if (index == -1) {
            return null;
        }
        String out = "/" + sp[index] + "/" + sp[index + 1];
        if ("emulated".equals(sp[index + 1])) {
            out += "/" + sp[index + 2];
        }
        return out;
    }

    public File getInternalStoragePath() {
        return Environment.getExternalStorageDirectory();
    }

    public File[] getUsbdiskVolumePaths() {
        return new File[0];
    }

    public int getUsbdiskVolumesCount() {
        return 0;
    }

    public String getExternalStorageState(Context context) {
        return Environment.getExternalStorageState(getExternalStoragePath(context));
    }

    public HashMap<String, String> getOtgVolumesInfo(StorageManager storageManager) {
        return new HashMap<>();
    }

    public File getVolumePathFile(StorageVolume volume) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Class<StorageVolume> c = StorageVolume.class;
            try {
                Method method = c.getMethod("getPathFile");
                method.setAccessible(true);
                Object obj = method.invoke(volume);
                return (File) obj;
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                Log.e(TAG, "getVolumePathFile error: " + e);
                return null;
            }
        }
        return null;
    }

    public Uri getMtpObjectsUri(String s) {
        return null;
    }

    public YuvCodec decodeJpegToYuv(byte[] bytes) {
        return null;
    }

    public boolean encodeYuvToJpeg(byte[] bytes, int i, int i1, int i2, int[] ints, Rect rect, int i3, int i4, OutputStream outputStream) {
        return false;
    }

    public DrmManagerClient getDrmManagerClientEx(Context context) {
        return new DrmManagerClient(context);
    }

    public Object openDecryptSession(DrmManagerClient drmManagerClient, String s) {
        return null;
    }

    public void closeDecryptSession(DrmManagerClient drmManagerClient, Object o) {
    }

    public byte[] preadDrmData(DrmManagerClient drmManagerClient, Object o, int i, int i1) {
        return null;
    }

    public void setPlaybackStatus(DrmManagerClient drmManagerClient, Object o, int i) {
    }

    public Bitmap decodeDRMBitmapWithBitmapOptions(DrmManagerClient drmManagerClient, String s, BitmapFactory.Options options) {
        return null;
    }

    public Bitmap decodeDRMBitmapWithBitmapOptions(DrmManagerClient drmManagerClient, Uri uri, BitmapFactory.Options options) {
        return null;
    }

    public void consumeDrmRights(MediaPlayer mediaPlayer) {
    }

    public boolean getIsAudioFocusExclusive(AudioManager audioManager) {
        return false;
    }

    public boolean getSafeMediaVolumeEnabled(AudioManager audioManager) {
        return false;
    }

    public boolean getIsTrimvideoEnable() {
        return "enable".equals(System.getProperty("ro.config.trimvideo", "disable"));
    }

    public boolean isLowRam() {
        return false;
    }

    public boolean isLowRamDeviceStatic() {
        return false;
    }

    public int getRamConfig() {
        return 0;
    }

    public boolean getIsDrmSupported() {
        return false;
    }

    public boolean isSupportHwCodec() {
        return false;
    }

    public boolean isSupportMultiCameraSource() {
        return true;
    }

    public boolean isSupportBurstImage() {
        return false;
    }

    public boolean isSupportFileFlag() {
        return false;
    }

    public boolean isSupportIsDrm() {
        return false;
    }

    public boolean isSupportCover() {
        return false;
    }

    public boolean isSupportShareAsVideo() {
        return false;
    }

    public boolean isSupportAIEngine() {
        return true;
    }

    public boolean isSupportLocation() {
        return false;
    }

    public boolean isSupportStory() {
        return false;
    }
}
