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

public class NewVideoPermissionsActivity extends PermissionsActivity {

    private static final String TAG = "NewVideoPermissionsActivity";
    private static Activity sLastActivity;

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate, NewVideoActivity start requset permissions");
        super.onCreate(savedInstanceState);
        /* SPRD: bug625227,avoid ANR in monkey test cause by create too many NewVideoPermissionsActivity @{ */
        if (GalleryUtils.isMonkey()) {
            if (sLastActivity != null) {
                Log.e(TAG, "NewVideoPermissionsActivity in monkey test -> last activity is not finished! ");
                sLastActivity.finish();
                sLastActivity = null;
            }
            sLastActivity = this;
        }
        /* @} */
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "destroy  NewVideoPermissionsActivity");
        super.onDestroy();
    }
}
