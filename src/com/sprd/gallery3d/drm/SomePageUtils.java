
package com.sprd.gallery3d.drm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.drm.DrmManagerClient;

import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaSet;
import com.sprd.gallery3d.app.NewVideoActivity;

public class SomePageUtils {

    static SomePageUtils sInstance;

    public static SomePageUtils getInstance() {
        if (sInstance != null) {
            return sInstance;
        }
//        sInstance = (SomePageUtils) AddonManager.getDefault()
//                .getAddon(R.string.feature_drm_somepage, SomePageUtils.class);
        sInstance = new com.sprd.drmgalleryplugin.app.AddonSomePage();
        return sInstance;
    }

    public boolean checkPressedIsDrm(
            Activity activity, MediaItem item,
            AlertDialog.OnClickListener confirmListener,
            AlertDialog.OnClickListener cancelListener, DialogInterface.OnKeyListener onKeyListener, boolean getContent) {
        return false;
    }

    public boolean checkIsDrmFile(MediaSet targetSet) {
        return false;
    }

    public boolean canGetFromDrm(Context context, boolean getContentForSetAs, MediaItem item) {
        return false;
    }

    /* SPRD: Add for new drm feature 568552 @{ */
    public boolean newCheckPressedIsDrm(Context context,
                                        String url, AlertDialog.OnClickListener listener, boolean getContent) {
        return false;
    }

    public boolean checkIsDrmFile(String filePath) {
        return false;
    }

    public boolean checkIsDrmFileValid(String filePath) {
        return false;
    }

    public boolean isDrmSupportTransfer(String filePath) {
        return false;
    }

    public Object newTransferDate(Long time, NewVideoActivity activity) {
        return null;
    }

    public Object newCompareDrmExpirationTime(Object object, byte[] clickTime, NewVideoActivity activity) {
        return null;
    }

    public Object newCompareDrmRemainRight(String filePath, Object object, NewVideoActivity activity) {
        return null;
    }

    public DrmManagerClient getDrmManagerClient() {
        return null;
    }

    // SPRD:Add for bug599941 non-sd drm videos are not supported to share
    public boolean newIsSupportShare(String filePath) {
        return false;
    }
    /* New drm feature end @} */
}
