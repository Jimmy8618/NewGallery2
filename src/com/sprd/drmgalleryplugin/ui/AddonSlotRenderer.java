package com.sprd.drmgalleryplugin.ui;

import android.content.Context;

import com.android.gallery3d.R;
import com.android.gallery3d.glrenderer.ResourceTexture;
import com.sprd.gallery3d.drm.SlotRendererUtils;

public class AddonSlotRenderer extends SlotRendererUtils {

    @Override
    public ResourceTexture createDrmStatusOverlay(boolean loacked, Context context) {
        if (loacked) {
            return new ResourceTexture(context, R.drawable.ic_drm_lock);
        } else {
            return new ResourceTexture(context, R.drawable.ic_drm_unlock);
        }
    }

//    @Override
//    public void drawDrmStatusOverlay(AlbumSlotRenderer renderer, MediaItem item, GLCanvas canvas, int width, int height) {
//        if(item == null || !item.mIsDrmFile) return;
//        int s = Math.min(width, height) / 4;
//        if (DrmUtil.isDrmValid(item.getFilePath())) {
//            renderer.mDRMUnlockedIcon.draw(canvas, (width - s), (height - s), s, s);
//        } else {
//            renderer.mDRMLockedIcon.draw(canvas, (width - s), (height - s), s, s);
//        }
//    }
}
