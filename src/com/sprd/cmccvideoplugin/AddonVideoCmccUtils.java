
package com.sprd.cmccvideoplugin;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.android.gallery3d.app.MoviePlayer;
import com.sprd.gallery3d.cmcc.VideoCmccUtils;

//import android.app.AddonManager;

public class AddonVideoCmccUtils extends VideoCmccUtils {

    private static final String TAG = "AddonVideoCmccUtils";
    private Context mPluginContext;

    public AddonVideoCmccUtils() {
    }

    /**
     * SPRD: This method is used to register message broadcast
     */
    @Override
    public void initMessagingUtils(final Activity activity) {
        Log.d(TAG, "AddonVideoCmccUtils initMessagingUtils");
        CmccMessagingUtils.getInstance().initMessagingUtils(activity, activity);
    }

    /**
     * SPRD: This method is used to unregister message broadcast
     */
    @Override
    public void releaseMessagingUtils() {
        Log.d(TAG, "AddonVideoCmccUtils releaseMessagingUtils");
        CmccMessagingUtils.getInstance().releaseMessagingUtils();
    }

    /**
     * SPRD: This method is used to cancel messaging remind dialog
     */
    @Override
    public void destoryMessagingDialog() {
        Log.d(TAG, "AddonVideoCmccUtils destoryMessagingDialog");
        CmccMessagingUtils.getInstance().destoryMessagingDialog();
    }

    /**
     * SPRD: This method is used to set player use to pause video
     */
    @Override
    public void initPlayer(MoviePlayer player) {
        Log.d(TAG, "AddonVideoCmccUtils initPlayer");
        CmccMessagingUtils.getInstance().initPlayer(player);
    }

    /**
     * SPRD: This method is used to judge whether it is timeout or not
     */
    @Override
    public boolean ifIsPhoneTimeout(long current) {
        return CmccPhoneUtils.getInstance().ifIsPhoneTimeout(current);
    }
}
