package com.sprd.drmgalleryplugin.app;


import android.drm.DrmManagerClient;

import com.android.gallery3d.app.GalleryAppImpl;
import com.sprd.frameworks.StandardFrameworks;
import com.sprd.gallery3d.drm.GalleryAppImplUtils;


public class AddonGalleryAppImpl extends GalleryAppImplUtils {
    private static GalleryAppImpl sGalleryAppImpl = null;
    private static DrmManagerClient mDrmManagerClient;


    @Override
    public void createGalleryAppImpl(GalleryAppImpl impl) {
        sGalleryAppImpl = impl;
    }

    synchronized public static DrmManagerClient getDrmManagerClient() {
        if (mDrmManagerClient == null) {
            mDrmManagerClient = StandardFrameworks.getInstances().getDrmManagerClientEx(sGalleryAppImpl);
        }
        return mDrmManagerClient;
    }

}
