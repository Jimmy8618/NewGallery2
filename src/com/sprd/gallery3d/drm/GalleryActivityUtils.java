
package com.sprd.gallery3d.drm;

import android.content.Intent;
import android.os.Bundle;

//import android.app.AddonManager;

public class GalleryActivityUtils {

    static GalleryActivityUtils sInstance;

    public static GalleryActivityUtils getInstance() {
        if (sInstance != null) {
            return sInstance;
        }
//        sInstance = (GalleryActivityUtils) AddonManager.getDefault()
//                .getAddon(R.string.feature_drm_galleryactivity, GalleryActivityUtils.class);
        sInstance = new com.sprd.drmgalleryplugin.app.AddonGalleryActivity();
        return sInstance;
    }

    public void startGetContentSetAs(Intent intent, Bundle data) {

    }
}
