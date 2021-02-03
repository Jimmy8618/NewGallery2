
package com.sprd.gallery3d.drm;

import android.content.Context;
import android.view.Menu;

import com.android.gallery3d.app.PhotoPage;
import com.android.gallery3d.app.SprdAlbumPage;
import com.android.gallery3d.app.SprdAlbumSetPage;
import com.android.gallery3d.data.MediaDetails;
import com.android.gallery3d.v2.page.PhotoViewPageFragment;

import java.util.ArrayList;

public class MenuExecutorUtils {

    static MenuExecutorUtils sInstance;

    public static MenuExecutorUtils getInstance() {
        if (sInstance != null) {
            return sInstance;
        }
//        sInstance = (MenuExecutorUtils) AddonManager.getDefault()
//                .getAddon(R.string.feature_drm_menuexecutor, MenuExecutorUtils.class);
        sInstance = new com.sprd.drmgalleryplugin.ui.AddonMenuExecutor();
        return sInstance;
    }

    public void createDrmMenuItem(Menu menu, Context context) {
    }

    public void updateDrmMenuOperation(Menu menu, int supported) {
    }

    public boolean showHideDrmDetails(SprdAlbumSetPage page, int itemId) {
        return false;
    }

    public boolean showHideDrmDetails(SprdAlbumPage page, int itemId) {
        return false;
    }

    public boolean showHideDrmDetails(PhotoPage page, int itemId, int index) {
        return false;
    }

    public boolean setDrmDetails(Context context, MediaDetails details
            , ArrayList<String> items, boolean isDrmDetails) {
        return false;
    }

    public String getDetailsNameForDrm(Context context, int key) {
        return "Unknown key" + key;
    }

    public boolean keyMatchDrm(int key) {
        return false;
    }

    public boolean showHideDrmDetails(PhotoViewPageFragment page, int itemId, int index) {
        return false;
    }
}
