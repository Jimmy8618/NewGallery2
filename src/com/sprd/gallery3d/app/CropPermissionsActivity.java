/**
 * Created by Spreadst
 */

package com.sprd.gallery3d.app;

import android.os.Build;
import android.os.Bundle;

import androidx.annotation.RequiresApi;

import android.util.Log;

public class CropPermissionsActivity extends PermissionsActivity {

    private static final String TAG = "CropPermissionsActivity";

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate, CropActivity start requset permissions");
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "destroy  CropPermissionsActivity");
        super.onDestroy();
    }
}
