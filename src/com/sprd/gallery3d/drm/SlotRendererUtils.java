
package com.sprd.gallery3d.drm;

import android.content.Context;

import com.android.gallery3d.glrenderer.ResourceTexture;

//import android.app.AddonManager;

public class SlotRendererUtils {

    static SlotRendererUtils sInstance;

    public static SlotRendererUtils getInstance() {
        if (sInstance != null) {
            return sInstance;
        }
//        sInstance = (SlotRendererUtils) AddonManager.getDefault()
//                .getAddon(R.string.feature_drm_slotrenderer, SlotRendererUtils.class);
        sInstance = new com.sprd.drmgalleryplugin.ui.AddonSlotRenderer();
        return sInstance;
    }

    public ResourceTexture createDrmStatusOverlay(boolean loacked, Context context) {
        return null;
    }

//    public void drawDrmStatusOverlay(AlbumSlotRenderer renderer, MediaItem item, GLCanvas canvas, int width, int height) {
//    }
}
