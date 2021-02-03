
package com.sprd.gallery3d.drm;

import android.content.Context;
import android.graphics.Rect;

import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.ui.PhotoView;
import com.android.gallery3d.ui.PhotoView.Model;

//import android.app.AddonManager;


public class PhotoViewUtils {

    static PhotoViewUtils sInstance;

    public static PhotoViewUtils getInstance() {
        if (sInstance != null) {
            return sInstance;
        }
//        sInstance = (PhotoViewUtils) AddonManager.getDefault()
//                .getAddon(R.string.feature_drm_photoview, PhotoViewUtils.class);
        sInstance = new com.sprd.drmgalleryplugin.ui.AddonPhotoView();
        return sInstance;
    }

    public void initPictureDrmIcon(Context context) {

    }

    public boolean isDrmUnLocked(Model mModel, int offset) {
        return false;
    }

    public boolean isDrmLocked(Model mModel, int offset) {
        return false;
    }

    public void setDrmIcon(GLCanvas canvas, Rect r, PhotoView mPhotoView, Boolean mIsVideo, Boolean mIsDrmLocked, Boolean mIsDrmUnLocked) {

    }
}
