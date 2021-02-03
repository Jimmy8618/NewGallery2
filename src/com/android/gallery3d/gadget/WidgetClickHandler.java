/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.gadget;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryActivity;
import com.android.gallery3d.app.PhotoPage;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.v2.app.GalleryActivity2;

import java.io.FileNotFoundException;

public class WidgetClickHandler extends Activity {
    private static final String TAG = "WidgetClickHandler";

    private boolean isValidDataUri(Uri dataUri) {
        if (dataUri == null) {
            return false;
        }
        try {
            AssetFileDescriptor f = getContentResolver()
                    .openAssetFileDescriptor(dataUri, "r");
            f.close();
            return true;
        } catch (FileNotFoundException e) {
            Log.e(TAG, "file not found! uri: " + dataUri, e);
            Toast.makeText(getApplicationContext(), R.string.no_such_item, Toast.LENGTH_SHORT).show();
            return false;
        } catch (Throwable e) {
            Log.w(TAG, "cannot open uri: " + dataUri, e);
            return false;
        }
    }

    @Override
    @TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        // The behavior is changed in JB, refer to b/6384492 for more details
        boolean tediousBack = Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.JELLY_BEAN;
        Uri uri = getIntent().getData();
        Intent intent;
        if (isValidDataUri(uri)) {
            intent = new Intent(Intent.ACTION_VIEW, uri);
            if (tediousBack) {
                intent.putExtra(PhotoPage.KEY_TREAT_BACK_AS_UP, true);
            }
        } else {
            if (GalleryUtils.isSupportV2UI()) {
                intent = new Intent(this, GalleryActivity2.class);
            } else {
                intent = new Intent(this, GalleryActivity.class);
            }
        }
        intent.putExtra(PhotoPage.KEY_READONLY, false);
        intent.putExtra(PhotoPage.KEY_START_FROM_WIDGET, true);
        if (tediousBack) {
            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_CLEAR_TASK |
                            Intent.FLAG_ACTIVITY_TASK_ON_HOME);
        }
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            Intent newIntent = new Intent(this, GalleryActivity.class);
            newIntent.setDataAndType(uri, "image/*");
            newIntent.setAction(Intent.ACTION_VIEW);
            newIntent.putExtra(PhotoPage.KEY_READONLY, false);
            newIntent.putExtra(PhotoPage.KEY_START_FROM_WIDGET, true);
            if (tediousBack) {
                newIntent.setFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK |
                                Intent.FLAG_ACTIVITY_CLEAR_TASK |
                                Intent.FLAG_ACTIVITY_TASK_ON_HOME);
            }
            try {
                startActivity(newIntent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.no_such_item, Toast.LENGTH_LONG).show();
            }
        }
        finish();
    }
}
