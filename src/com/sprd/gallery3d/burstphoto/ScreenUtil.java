package com.sprd.gallery3d.burstphoto;

import android.content.Context;
import android.content.res.Configuration;
import android.util.Log;

/**
 * Created by rui.li on 4/30/17.
 */

public class ScreenUtil {
    private static final String TAG = "ScreenUtil";

    public static int dip2px(Context context, float dipValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dipValue * scale + 0.5f);
    }

    public static int px2dip(Context context, float pxValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (pxValue / scale + 0.5f);
    }

    public static boolean isPortrait(Context context) {
        if (context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            Log.d(TAG, "screen orientation : portrait");
            return true;
        } else {
            Log.d(TAG, "screen orientation : landscape");
            return false;
        }
    }
}
