
package com.sprd.gallery3d.drm;

import com.android.gallery3d.app.GalleryAppImpl;

public class GalleryAppImplUtils {

    static GalleryAppImplUtils sInstance;

    public static GalleryAppImplUtils getInstance() {
        if (sInstance != null) {
            return sInstance;
        }
//        sInstance = (GalleryAppImplUtils) AddonManager.getDefault()
//                .getAddon(R.string.feature_drm_galleryappimpl, GalleryAppImplUtils.class);
        sInstance = new com.sprd.drmgalleryplugin.app.AddonGalleryAppImpl();
        return sInstance;
    }

    public void createGalleryAppImpl(GalleryAppImpl impl) {
    }
}
