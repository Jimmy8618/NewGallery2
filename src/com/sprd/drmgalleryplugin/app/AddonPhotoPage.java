package com.sprd.drmgalleryplugin.app;

import android.content.Context;
import android.drm.DrmManagerClient;
import android.drm.DrmStore;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.app.PhotoPage;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.data.MediaItem;
import com.sprd.drmgalleryplugin.util.DrmUtil;
import com.sprd.frameworks.StandardFrameworks;
import com.sprd.gallery3d.drm.PhotoPageUtils;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.Queue;

public class AddonPhotoPage extends PhotoPageUtils {
    private static final String TAG = "AddonPhotoPage";

    private static final boolean PLAN_2 = true;

    private static final int MSG_CONSUME_DRM_RIGHTS = 1;

    private static final int CONSUME_DRM_RIGHTS_DELAY = 1000;

    public static final String KEY_IS_DISPLAY_DRM = "is-play-drm";

    private Object decryptHandle;

    private Queue<MediaItem> mQueueDrmWaitingConsumed;

    private Handler mMainHandler;

    public AddonPhotoPage() {
        if (PLAN_2) {
            mMainHandler = new MainHandler(this);
        }
    }

    @Override
    public void getFirstPickIsDrmPhoto() {
        mQueueDrmWaitingConsumed = new LinkedList<MediaItem>();
        decryptHandle = null;
    }

    @Override
    public void updateDrmCurrentPhoto(MediaItem mCurrentPhoto, Handler mHandler) {
        Log.d(TAG, "updateDrmCurrentPhoto filePath = " + mCurrentPhoto.getFilePath());
        String filePath = mCurrentPhoto.getFilePath();
        if (DrmUtil.isDrmFile(filePath, null)
                && mCurrentPhoto.getMediaType() != LocalImage.MEDIA_TYPE_VIDEO
                && !DrmUtil.getDrmFileType(filePath).equals(DrmUtil.FL_DRM_FILE)) {
            if (DrmUtil.isDrmValid(filePath)) {
                if (PLAN_2) {
                    consumeStart(filePath);
                } else {
                    setDrmPlayStatus(filePath, DrmStore.Playback.START);
                    if (mQueueDrmWaitingConsumed == null) {
                        mQueueDrmWaitingConsumed = new LinkedList<MediaItem>();
                    }
                    mQueueDrmWaitingConsumed.offer(mCurrentPhoto);
                    mHandler.removeMessages(PhotoPage.MSG_CONSUME_DRM_RIGHTS);
                    mHandler.sendEmptyMessageDelayed(PhotoPage.MSG_CONSUME_DRM_RIGHTS,
                            CONSUME_DRM_RIGHTS_DELAY);
                }
            }
        }
    }

    @Override
    public boolean cosumeDrmRights(Message message, Context context) {
        if (PhotoPage.MSG_CONSUME_DRM_RIGHTS == message.what) {
            MediaItem drmImage;
            while ((drmImage = mQueueDrmWaitingConsumed.poll()) != null) {
                setDrmPlayStatus(drmImage.getFilePath(), DrmStore.Playback.STOP);
                Toast.makeText(context, context.getString(R.string.drm_consumed),
                        Toast.LENGTH_SHORT).show();
            }
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onDrmDestroy() {
        if (mQueueDrmWaitingConsumed != null) {
            mQueueDrmWaitingConsumed.clear();
            mQueueDrmWaitingConsumed = null;
        }
        if (decryptHandle != null) {
            decryptHandle = null;
        }
    }

    private void setDrmPlayStatus(String filePath, int playbackStatus) {
        Log.d(TAG, "setDrmPlayStatus playbackStatus is " + playbackStatus + ", filePath is " + filePath);
        DrmManagerClient mDrmManagerClient = DrmUtil.getDrmManagerClient();
        if (decryptHandle == null) {
            decryptHandle = StandardFrameworks.getInstances().
                    openDecryptSession(mDrmManagerClient, filePath);
        }

        if (decryptHandle != null) {
            StandardFrameworks.getInstances().setPlaybackStatus(mDrmManagerClient,
                    decryptHandle, playbackStatus);
            if (playbackStatus == DrmStore.Playback.STOP) {
                StandardFrameworks.getInstances().closeDecryptSession(mDrmManagerClient,
                        decryptHandle);
                decryptHandle = null;
            }
        } else {
            Log.e(TAG, "setDrmPlayStatus fail, decryptHandle is null for " + filePath);
        }
    }

    private void consumeStart(String filePath) {
        Log.d(TAG, "consumeStart - " + filePath);
        DrmManagerClient mDrmManagerClient = DrmUtil.getDrmManagerClient();
        DrmConsumeItem item = new DrmConsumeItem();
        item.filePath = filePath;
        item.decryptHandle = StandardFrameworks.getInstances().
                openDecryptSession(mDrmManagerClient, filePath);
        if (item.decryptHandle != null) {
            StandardFrameworks.getInstances().setPlaybackStatus(mDrmManagerClient,
                    item.decryptHandle, DrmStore.Playback.START);
            Message msg = mMainHandler.obtainMessage(MSG_CONSUME_DRM_RIGHTS, item);
            mMainHandler.sendMessageDelayed(msg, CONSUME_DRM_RIGHTS_DELAY);
        }
    }

    private void consumeStop(DrmConsumeItem item) {
        Log.d(TAG, "consumeStop - " + item.filePath);
        DrmManagerClient mDrmManagerClient = DrmUtil.getDrmManagerClient();
        if (item.decryptHandle != null) {
            StandardFrameworks.getInstances().setPlaybackStatus(mDrmManagerClient,
                    item.decryptHandle, DrmStore.Playback.STOP);
            StandardFrameworks.getInstances().closeDecryptSession(mDrmManagerClient,
                    item.decryptHandle);
            item.decryptHandle = null;
            Context context = GalleryAppImpl.getApplication();
            Toast.makeText(context, context.getString(R.string.drm_consumed),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_CONSUME_DRM_RIGHTS:
                consumeStop((DrmConsumeItem) msg.obj);
                break;
            default:
                break;
        }
    }

    private static class MainHandler extends Handler {
        private WeakReference<AddonPhotoPage> ref;

        MainHandler(AddonPhotoPage page) {
            ref = new WeakReference<>(page);
        }

        @Override
        public void handleMessage(Message msg) {
            AddonPhotoPage page = ref.get();
            if (page != null) {
                page.handleMessage(msg);
            }
        }
    }

    private static class DrmConsumeItem {
        Object decryptHandle;
        String filePath;
    }
}
