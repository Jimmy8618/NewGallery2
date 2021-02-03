
package com.sprd.gallery3d.drm;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.android.gallery3d.data.DecodeUtils;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.data.LocalMediaItem;
import com.android.gallery3d.data.LocalVideo;
import com.android.gallery3d.data.MediaDetails;
import com.android.gallery3d.util.ThreadPool.JobContext;

public class LocalMediaItemUtils {

    static LocalMediaItemUtils sInstance;

    public static LocalMediaItemUtils getInstance() {
        if (sInstance != null) {
            return sInstance;
        }
//        sInstance = (LocalMediaItemUtils) AddonManager.getDefault()
//                .getAddon(R.string.feature_drm_localMedia, LocalMediaItemUtils.class);
        sInstance = new com.sprd.drmgalleryplugin.data.AddonLocalMediaItem();
        return sInstance;
    }

    public void loadDrmInfor(LocalMediaItem item) {

    }

    public int getImageSupportedOperations(LocalImage item, int operation) {
        return operation;
    }

    public int getVideoSupportedOperations(LocalVideo item, int operation) {
        return operation;
    }

    public Bitmap decodeThumbnailWithDrm(JobContext jc, final int type, String path,
                                         BitmapFactory.Options options, int targetSize) {
        return DecodeUtils.decodeThumbnail(jc, path, options, targetSize, type);
    }

    public Bitmap decodeThumbnailWithDrm(JobContext jc, final int type, Uri uri,
                                         BitmapFactory.Options options, int targetSize) {
        return DecodeUtils.decodeThumbnail(jc, uri, options, targetSize, type);
    }

    public MediaDetails getDetailsByAction(LocalMediaItem item, MediaDetails details, int action, Context context) {
        return details;
    }

    public boolean isCanFound(boolean found, String filePath, int mediaType) {
        return found;
    }

    public boolean isCanFound(boolean found, Uri uri, int mediaType) {
        return found;
    }
}
