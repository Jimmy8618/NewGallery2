/**
 * Created by Spreadst
 */

package com.sprd.gallery3d.app;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.RequiresApi;

import android.util.Log;

import com.android.gallery3d.util.GalleryUtils;

public class GalleryPermissionsActivity extends PermissionsActivity {

    private static final String TAG = "GalleryPermissionsActivity";
    private static Activity sLastActivity;

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate, GalleryActivity start requset permissions");
        super.onCreate(savedInstanceState);
        /* SPRD: bug612471,avoid ANR in monkey test cause by create too many GalleryPermissionsActivity @{ */
        if (GalleryUtils.isMonkey()) {
            if (sLastActivity != null) {
                Log.e(TAG, "GalleryPermissionsActivity in monkey test -> last activity is not finished! ");
                sLastActivity.finish();
                sLastActivity = null;
            }
            sLastActivity = this;
        }
        /* @} */
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "destroy  GalleryPermissionsActivity");
        super.onDestroy();
    }
}
