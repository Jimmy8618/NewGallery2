
package com.sprd.gallery3d.drm;

import android.content.Context;
import android.os.Handler;
import android.os.Message;

import com.android.gallery3d.data.MediaItem;

//import android.app.AddonManager;


public class PhotoPageUtils {

    static PhotoPageUtils sInstance;

    public static PhotoPageUtils getInstance() {
        if (sInstance != null) {
            return sInstance;
        }
//        sInstance = (PhotoPageUtils) AddonManager.getDefault()
//                .getAddon(R.string.feature_drm_photopage, PhotoPageUtils.class);
        sInstance = new com.sprd.drmgalleryplugin.app.AddonPhotoPage();
        return sInstance;
    }

    public void getFirstPickIsDrmPhoto() {

    }

    public boolean cosumeDrmRights(Message message, Context context) {
        return true;
    }

    public void updateDrmCurrentPhoto(MediaItem photo, Handler handler) {

    }

    public void onDrmDestroy() {

    }
}
