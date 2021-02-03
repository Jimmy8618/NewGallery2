/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.app;

import android.content.Intent;
import android.os.Bundle;

import com.android.gallery3d.util.GalleryUtils;
import com.sprd.gallery3d.app.GalleryPermissionsActivity;
import com.sprd.gallery3d.app.PermissionsActivity;
import com.sprd.gallery3d.app.PickPhotosPermissionsActivity;

public class DialogPicker extends PickerActivity {
    private boolean mHasCriticalPermissions;
    private static final String TAG = "DialogPicker";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (GalleryUtils.isAlnormalIntent(getIntent())) {
            finish();
            return;
        }
        /* SPRD: bug 620318 ,check permissions for DialogPicker @{ */
        checkPermissions();
        if (!mHasCriticalPermissions) {
            android.util.Log.v(TAG, "onCreate: Missing critical permissions.");
            finish();
            return;
        }
        /* @} */

        int typeBits = GalleryUtils.determineTypeBits(this, getIntent());
        setTitle(GalleryUtils.getSelectionModePrompt(typeBits));
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        Bundle data = extras == null ? new Bundle() : new Bundle(extras);

        data.putBoolean(GalleryActivity.KEY_GET_CONTENT, true);
        data.putString(SprdAlbumSetPage.KEY_MEDIA_PATH,
                getDataManager().getTopSetPath(typeBits));
        data.putBoolean("key-set-as", true);
        getStateManager().startState(SprdAlbumSetPage.class, data);
    }

    /* SPRD: bug 620318 ,check permissions for DialogPicker @{ */
    private void checkPermissions() {
        boolean isPickIntent = Intent.ACTION_GET_CONTENT.equalsIgnoreCase(getIntent().getAction())
                || Intent.ACTION_PICK.equalsIgnoreCase(getIntent().getAction());

        if (isPickIntent && GalleryUtils.checkStoragePermissions(this)) {
            mHasCriticalPermissions = true;
        } else {
            mHasCriticalPermissions = GalleryUtils.checkStoragePermissions(this) && GalleryUtils.checkLocationPermissions(this);
        }

        if (!mHasCriticalPermissions) {
            Class<?> target;
            if (isPickIntent) {
                target = PickPhotosPermissionsActivity.class;
            } else {
                target = GalleryPermissionsActivity.class;
            }

            if (GalleryUtils.requestPermission(this, target, PermissionsActivity.START_FROM_GALLERY)) {
                finish();
            }
        }

    }
    /* @} */
}
