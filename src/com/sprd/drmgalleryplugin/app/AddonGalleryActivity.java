
package com.sprd.drmgalleryplugin.app;

import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;

import com.sprd.gallery3d.drm.GalleryActivityUtils;

public class AddonGalleryActivity extends GalleryActivityUtils {
    public static final String KEY_SET_WALLPAPER = "applyForWallpaper";
    private static final String TAG = "AddonGalleryDrm";

    @Override
    public void startGetContentSetAs(Intent intent, Bundle data) {
        if (intent.hasExtra(KEY_SET_WALLPAPER)
                || intent.hasExtra(MediaStore.EXTRA_OUTPUT)) {
            data.putBoolean("key-set-as", true);
        }
    }
}
