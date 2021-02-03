package com.android.fw;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.drm.DrmManagerClient;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.YuvImageEx;
import android.hardware.camera2.CaptureResult;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSessionManager;
import android.net.Uri;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.VideoView;

import java.io.File;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;

import android.hardware.SprdSensor;
import android.media.SubtitleController;
import android.media.WebVttRenderer;
import android.media.Metadata;
import android.os.SystemProperties;
import android.os.storage.VolumeInfo;
import android.os.EnvironmentEx;
import android.drm.DrmManagerClientEx;
import android.drm.DecryptHandle;
import android.graphics.BitmapFactoryEx;

import com.sprd.frameworks.StandardFrameworks;

import dalvik.system.VMRuntime;

public class SprdFramewoks extends StandardFrameworks {

    @Override
    public CaptureResult.Key<Integer> getSprdCaptureResult() {
        return null;
    }

    @Override
    public int getSprdFaceUpDownSensorType() {
        try {
            return SprdSensor.TYPE_SPRDHUB_FACE_UP_DOWN;
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.getSprdFaceUpDownSensorType();
        }
    }

    @Override
    public int getSprdHubFlipSensorType() {
        try {
            return SprdSensor.TYPE_SPRDHUB_FLIP;
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.getSprdHubFlipSensorType();
        }
    }

    @Override
    public int getSettingPlayControl(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), Settings.Global.PLAY_CONTROL, 0);
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.getSettingPlayControl(context);
        }
    }

    @Override
    public MediaPlayer getMediaPlayerEx() {
        return new MediaPlayer();
    }

    @Override
    public void setOnMediaKeyListener(MediaSessionManager manager, final OnMediaKeyListener listener) {
        try {
            if (manager == null) {
                return;
            }
            if (listener == null) {
                manager.setOnMediaKeyListener(null, null);
            } else {
                manager.setOnMediaKeyListener(new MediaSessionManager.OnMediaKeyListener() {
                    @Override
                    public boolean onMediaKey(KeyEvent event) {
                        return listener.onMediaKey(event);
                    }
                }, null);
            }
        } catch (Exception | Error e) {
            e.printStackTrace();
            super.setOnMediaKeyListener(manager, listener);
        }
    }

    @Override
    public void setVideoViewRendererAndSubtitleAnchor(Context context, MediaPlayer mediaplayer, VideoView view) {
        try {
            final SubtitleController controller = new SubtitleController(
                    context, mediaplayer.getMediaTimeProvider(), mediaplayer);
            controller.registerRenderer(new WebVttRenderer(context));
            mediaplayer.setSubtitleAnchor(controller, view);
        } catch (Exception | Error e) {
            e.printStackTrace();
            super.setVideoViewRendererAndSubtitleAnchor(context, mediaplayer, view);
        }
    }

    @Override
    public MediaPlayerMetaData getPlayerMetaData(MediaPlayer player) {
        try {
            MediaPlayerMetaData returndata = new MediaPlayerMetaData();
            Metadata data = player.getMetadata(MediaPlayer.METADATA_ALL,
                    MediaPlayer.BYPASS_METADATA_FILTER);
            if (data != null) {
                returndata.mCanPause = !data.has(Metadata.PAUSE_AVAILABLE)
                        || data.getBoolean(Metadata.PAUSE_AVAILABLE);
                returndata.mCanSeekBack = !data.has(Metadata.SEEK_BACKWARD_AVAILABLE)
                        || data.getBoolean(Metadata.SEEK_BACKWARD_AVAILABLE);
                returndata.mCanSeekForward = !data.has(Metadata.SEEK_FORWARD_AVAILABLE)
                        || data.getBoolean(Metadata.SEEK_FORWARD_AVAILABLE);
            }
            return returndata;
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.getPlayerMetaData(player);
        }
    }

    @Override
    public int getCallState(TelephonyManager tm, int subId) {
        try {
            return tm.getCallState(subId);
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.getCallState(tm, subId);
        }
    }

    @Override
    public int getCallingUserId() {
        try {
            return UserHandle.getCallingUserId();
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.getCallingUserId();
        }
    }

    @Override
    public int getSimState(TelephonyManager tm, int slotIdx) {
        try {
            return tm.getSimState(slotIdx);
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.getSimState(tm, slotIdx);
        }
    }

    @Override
    public Intent setFlags(Intent intent) {
        try {
            return intent.setFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.setFlags(intent);
        }
    }

    @Override
    public void addWinParams(WindowManager.LayoutParams params) {
        try {
            if (params != null) {
                //params.privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_SNAPSHOT_NOT_RUN;
            }
        } catch (Exception | Error e) {
            e.printStackTrace();
            super.addWinParams(params);
        }
    }

    @Override
    public int getSystemUIHideSurfaceFlag() {
        return View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
    }

    @Override
    public void setDisablePreviewScreenshots(Activity activity, boolean disable) {
        try {
            activity.setDisablePreviewScreenshots(disable);
        } catch (Exception | Error e) {
            e.printStackTrace();
            super.setDisablePreviewScreenshots(activity, disable);
        }
    }

    @Override
    public int[] getActiveSubscriptionIdList(SubscriptionManager sm) {
        try {
            return sm.getActiveSubscriptionIdList();
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.getActiveSubscriptionIdList(sm);
        }
    }

    @Override
    public boolean getIntentisAccessUriMode(Activity activity) {
        return false;
    }

    @Override
    public void runTimeSetTargetHeapUtilization(float value) {
        try {
            VMRuntime.getRuntime().setTargetHeapUtilization(value);
        } catch (Exception | Error e) {
            e.printStackTrace();
            super.runTimeSetTargetHeapUtilization(value);
        }
    }

    @Override
    public boolean getBooleanFromSystemProperties(String key, boolean def) {
        try {
            return SystemProperties.getBoolean(key, def);
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.getBooleanFromSystemProperties(key, def);
        }
    }

    @Override
    public int getIntFromSystemProperties(String key, int def) {
        try {
            return SystemProperties.getInt(key, def);
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.getIntFromSystemProperties(key, def);
        }
    }

    @Override
    public String getStringSystemProperties(String key, String def) {
        try {
            return SystemProperties.get(key, def);
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.getStringSystemProperties(key, def);
        }
    }

    @Override
    public Object registerStorageManagerListener(StorageManager sm, StorageEventListener listener) {
        try {
            MyStorageEventListener myStorageEventListener = new MyStorageEventListener(sm, listener);
            sm.registerListener(myStorageEventListener);
            return myStorageEventListener;
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.registerStorageManagerListener(sm, listener);
        }
    }

    @Override
    public void unregisterStorageManagerListener(StorageManager sm, Object listener) {
        try {
            if (sm != null) {
                sm.unregisterListener((android.os.storage.StorageEventListener) listener);
            }
        } catch (Exception | Error e) {
            e.printStackTrace();
            super.unregisterStorageManagerListener(sm, listener);
        }
    }

    private static class MyStorageEventListener extends android.os.storage.StorageEventListener {
        private StorageEventListener mlistener;
        private StorageManager mStorageManager;

        public MyStorageEventListener(StorageManager sm, StorageEventListener listener) {
            mlistener = listener;
            mStorageManager = sm;
        }

        @Override
        public void onVolumeStateChanged(VolumeInfo vol, int oldState, int newState) {
            if (isInteresting(vol)) {
                if (vol.getDisk() != null) {
                    String volumeName = mStorageManager.getBestVolumeDescription(vol);
                    String volumePath = vol.getPath() == null ? null : vol.getPath().toString();
                    mlistener.onVolumeStateChanged(volumeName, volumePath,
                            vol.getDisk().isSd(), vol.getState());
                }
            }
        }

        private boolean isInteresting(VolumeInfo vol) {
            if (vol != null) {
                switch (vol.getType()) {
                    case VolumeInfo.TYPE_PRIVATE:
                    case VolumeInfo.TYPE_PUBLIC:
                        return true;
                    default:
                        return false;
                }
            }
            return false;
        }
    }

    @Override
    public File getExternalStoragePath(Context context) {
        try {
            return EnvironmentEx.getExternalStoragePath();
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.getExternalStoragePath(context);
        }
    }

    @Override
    public File getInternalStoragePath() {
        try {
            return EnvironmentEx.getInternalStoragePath();
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.getInternalStoragePath();
        }
    }

    @Override
    public File[] getUsbdiskVolumePaths() {
        try {
            return EnvironmentEx.getUsbdiskVolumePaths();
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.getUsbdiskVolumePaths();
        }
    }

    @Override
    public int getUsbdiskVolumesCount() {
        try {
            return EnvironmentEx.getUsbdiskVolumes().size();
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.getUsbdiskVolumesCount();
        }
    }

    @Override
    public String getExternalStorageState(Context context) {
        try {
            return EnvironmentEx.getExternalStoragePathState();
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.getExternalStorageState(context);
        }
    }

    @Override
    public HashMap<String, String> getOtgVolumesInfo(StorageManager sm) {
        try {
            HashMap<String, String> volumnInfo = new HashMap<>();
            final List<VolumeInfo> volumes = sm.getVolumes();
            if (volumes != null) {
                String volumeName = null;
                String volumePath = null;
                for (VolumeInfo info : volumes) {
                    if (info != null && info.getDisk() != null && info.getDisk().isUsb()
                            && !info.getDisk().isSd()) {
                        volumeName = sm.getBestVolumeDescription(info);
                        if (info.getPath() != null) {
                            volumePath = info.getPath().toString();
                        }
                        if (volumeName != null && volumePath != null) {
                            volumnInfo.put(volumeName, volumePath);
                        }
                    }
                }
            }
            return volumnInfo;
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.getOtgVolumesInfo(sm);
        }
    }

    @Override
    public File getVolumePathFile(StorageVolume volume) {
        try {
            return volume.getPathFile();
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.getVolumePathFile(volume);
        }
    }

    @Override
    public Uri getMtpObjectsUri(String volumeName) {
        try {
            return MediaStore.Files.getMtpObjectsUri(volumeName);
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.getMtpObjectsUri(volumeName);
        }
    }

    @Override
    public YuvCodec decodeJpegToYuv(byte[] oriJpeg) {
        try {
            return new YuvCodec(YuvImageEx.decodeJpegToYuv(oriJpeg, YuvImageEx.YUV_FORMAT_NV21));
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.decodeJpegToYuv(oriJpeg);
        }
    }

    @Override
    public boolean encodeYuvToJpeg(byte[] yuv, int format, int width, int height, int[] strides, Rect rectangle, int quality, int rotation, OutputStream stream) {
        try {
            YuvImageEx.EncoderParameter encoderPara =
                    new YuvImageEx.EncoderParameter(yuv, YuvImageEx.YUV_FORMAT_NV21, width, height)
                            .setQuality(quality)
                            .setRotation(rotation)
                            .setRectangle(rectangle)
                            .setStrides(strides);

            return YuvImageEx.encodeYuvToJpeg(encoderPara, stream);
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.encodeYuvToJpeg(yuv, format, width, height, strides, rectangle, quality, rotation, stream);
        }
    }

    @Override
    public DrmManagerClient getDrmManagerClientEx(Context context) {
        try {
            return new DrmManagerClientEx(context);
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.getDrmManagerClientEx(context);
        }
    }

    @Override
    public Object openDecryptSession(DrmManagerClient client, String path) {
        try {
            DrmManagerClientEx clientEx = (DrmManagerClientEx) client;
            return clientEx.openDecryptSession(path);
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.openDecryptSession(client, path);
        }
    }

    @Override
    public void closeDecryptSession(DrmManagerClient client, Object handle) {
        try {
            DrmManagerClientEx clientEx = (DrmManagerClientEx) client;
            clientEx.closeDecryptSession((DecryptHandle) handle);
        } catch (Exception | Error e) {
            e.printStackTrace();
            super.closeDecryptSession(client, handle);
        }
    }

    @Override
    public byte[] preadDrmData(DrmManagerClient client, Object handle, int offset, int size) {
        try {
            DrmManagerClientEx clientEx = (DrmManagerClientEx) client;
            return clientEx.pread((DecryptHandle) handle, offset, size);
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.preadDrmData(client, handle, offset, size);
        }
    }

    @Override
    public void setPlaybackStatus(DrmManagerClient client, Object handle, int playbackStatus) {
        try {
            DrmManagerClientEx clientEx = (DrmManagerClientEx) client;
            clientEx.setPlaybackStatus((DecryptHandle) handle, playbackStatus);
        } catch (Exception | Error e) {
            e.printStackTrace();
            super.setPlaybackStatus(client, handle, playbackStatus);
        }
    }

    @Override
    public Bitmap decodeDRMBitmapWithBitmapOptions(DrmManagerClient client, String path, BitmapFactory.Options options) {
        try {
            Bitmap result;
            DrmManagerClientEx clientEx = (DrmManagerClientEx) client;
            DecryptHandle handle = clientEx.openDecryptSession(path);
            if (handle != null) {
                result = BitmapFactoryEx.decodeDrmStream(clientEx, handle, options);
                clientEx.closeDecryptSession(handle);
            } else {
                result = BitmapFactory.decodeFile(path, options);

            }
            return result;
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.decodeDRMBitmapWithBitmapOptions(client, path, options);
        }
    }

    @Override
    public Bitmap decodeDRMBitmapWithBitmapOptions(DrmManagerClient client, Uri uri, BitmapFactory.Options options) {
        return null;
    }

    @Override
    public void consumeDrmRights(MediaPlayer player) {
        try {
            if (player != null) {
                player.consumeDrmRights();
            }
        } catch (Exception | Error e) {
            e.printStackTrace();
            super.consumeDrmRights(player);
        }
    }

    @Override
    public boolean getIsAudioFocusExclusive(AudioManager am) {
        return false;
    }

    @Override
    public boolean getSafeMediaVolumeEnabled(AudioManager am) {
        try {
            return am.getSafeMediaVolumeEnabled();
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.getSafeMediaVolumeEnabled(am);
        }
    }

    @Override
    public boolean getIsTrimvideoEnable() {
        return "enable".equals(System.getProperty("ro.config.trimvideo", "disable"));
    }

    @Override
    public boolean isLowRam() {
        try {
            return SystemProperties.getBoolean("ro.config.low_ram", false);
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.isLowRam();
        }
    }

    @Override
    public boolean isLowRamDeviceStatic() {
        try {
            return ActivityManager.isLowRamDeviceStatic();
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.isLowRamDeviceStatic();
        }
    }

    @Override
    public int getRamConfig() {
        try {
            return SystemProperties.getInt("ro.run.ramconfig", 2);
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.getRamConfig();
        }
    }

    @Override
    public boolean getIsDrmSupported() {
        try {
            return SystemProperties.get("drm.service.enabled", "false").equals("true");
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.getIsDrmSupported();
        }
    }

    @Override
    public boolean isSupportHwCodec() {
        try {
            return YuvImageEx.isJpegHwCodecAvailable();
        } catch (Exception | Error e) {
            return false;
        }
    }

    @Override
    public boolean isSupportMultiCameraSource() {
        return false;
    }

    @Override
    public boolean isSupportBurstImage() {
        return true;
    }

    @Override
    public boolean isSupportFileFlag() {
        return true;
    }

    @Override
    public boolean isSupportIsDrm() {
        return true;
    }

    @Override
    public boolean isSupportCover() {
        return true;
    }

    @Override
    public boolean isSupportShareAsVideo() {
        return true;
    }

    @Override
    public boolean isSupportAIEngine() {
        try {
            return SystemProperties.getInt("persist.sys.gallery.discover.module", 0) > 0;
        } catch (Exception | Error e) {
            e.printStackTrace();
            return super.isSupportAIEngine();
        }
    }

    @Override
    public boolean isSupportLocation() {
        return false;
    }

    @Override
    public boolean isSupportStory() {
        return false;
    }
}
