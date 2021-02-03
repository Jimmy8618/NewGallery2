
package com.sprd.gallery3d.drm;

import android.content.Context;
import android.net.Uri;

import java.io.InputStream;

//import android.app.AddonManager;

public class GifDecoderUtils {

    static GifDecoderUtils sInstance;

    public static GifDecoderUtils getInstance() {
        if (sInstance != null) {
            return sInstance;
        }
//        sInstance = (GifDecoderUtils) AddonManager.getDefault().getAddon(
//                R.string.feature_drm_gifDecoder, GifDecoderUtils.class);
        sInstance = new com.sprd.drmgalleryplugin.gif.AddonGifDecoder();
        return sInstance;
    }

    public void initDrm(Uri uri, Context context) {
    }

    public boolean isReadDrmUri() {
        return false;
    }

    public InputStream readDrmUri() {
        return null;
    }
}
