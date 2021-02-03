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
package com.android.gallery3d.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;
import android.view.View.MeasureSpec;

import com.android.gallery3d.R;
import com.android.gallery3d.data.MediaDetails;
import com.android.gallery3d.ui.DetailsAddressResolver.AddressResolvingListener;
import com.android.gallery3d.v2.util.DetailsLocationResolver;
import com.sprd.gallery3d.drm.MenuExecutorUtils;

public class DetailsHelper {
    public static final boolean SHOW_IN_ACTIVITY = true;
    private static DetailsAddressResolver sAddressResolver;
    private static DetailsLocationResolver sLocationResolver;
    private DetailsViewContainer mContainer;
    private static final String TAG = "DetailsHelper";

    public interface DetailsSource {
        int size();

        int setIndex();

        MediaDetails getDetails();
    }

    public interface CloseListener {
        void onClose();
    }

    public interface DetailsViewContainer {
        void reloadDetails();

        void setCloseListener(CloseListener listener);

        void show();

        void hide();

        /* SPRD: Drm feature start @{ */
        void reloadDrmDetails(boolean isDrmDetails);
        /* SPRD: Drm feature end @} */
    }

    public interface ResolutionResolvingListener {
        void onResolutionAvailable(int width, int height);
    }

    public interface ResolutionResolvingListener2 {
        void onResolutionAvailable(int width, int height, String size);
    }

    public DetailsHelper(Activity activity, GLView rootPane, DetailsSource source) {
        mContainer = new DialogDetailsView(activity, source);
    }

    public void layout(int left, int top, int right, int bottom) {
        if (mContainer instanceof GLView) {
            GLView view = (GLView) mContainer;
            view.measure(MeasureSpec.UNSPECIFIED,
                    MeasureSpec.makeMeasureSpec(bottom - top, MeasureSpec.AT_MOST));
            view.layout(0, top, view.getMeasuredWidth(), top + view.getMeasuredHeight());
        }
    }

    public void reloadDetails() {
        mContainer.reloadDetails();
    }

    public void setCloseListener(CloseListener listener) {
        mContainer.setCloseListener(listener);
    }

    public static String resolveAddress(/*AbstractGallery*/Activity activity, double[] latlng,
                                                           AddressResolvingListener listener) {
        if (sAddressResolver == null) {
            sAddressResolver = new DetailsAddressResolver(activity);
        } else {
            sAddressResolver.cancel();
        }
        return sAddressResolver.resolveAddress(latlng, listener);
    }

    public static void resolveLocation(Activity activity, Uri uri, DetailsLocationResolver.LocationResolvingListener listener, boolean isImage) {
        if (sLocationResolver == null) {
            sLocationResolver = new DetailsLocationResolver(activity);
        } else {
            sLocationResolver.cancel();
        }
        sLocationResolver.resolveLocation(uri, listener, isImage);
    }

    public static void resolveResolution(String path, ResolutionResolvingListener listener) {
        /* SPRD: bug 545287 decoder bitmap by path oom,when obtain bitmap details @}*/
        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeFile(path);
        } catch (OutOfMemoryError e) {
            Log.w(TAG, "obtain bitmap details oom :" + e);
        }
        /* @} */
        if (bitmap == null) {
            return;
        }
        listener.onResolutionAvailable(bitmap.getWidth(), bitmap.getHeight());
    }

    public static void resolveResolution2(String path, ResolutionResolvingListener2 listener, String size) {
        /* SPRD: bug 545287 decoder bitmap by path oom,when obtain bitmap details @}*/
        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeFile(path);
        } catch (OutOfMemoryError e) {
            Log.w(TAG, "obtain bitmap details oom :" + e);
        }
        /* @} */
        if (bitmap == null) {
            return;
        }
        listener.onResolutionAvailable(bitmap.getWidth(), bitmap.getHeight(), size);
    }

    public static void pause() {
        if (sAddressResolver != null) {
            sAddressResolver.cancel();
        }
        if (sLocationResolver != null) {
            sLocationResolver.cancel();
        }
    }

    public void show() {
        mContainer.show();
    }

    public void hide() {
        mContainer.hide();
    }

    public static String getDetailsName(Context context, int key) {
        switch (key) {
            case MediaDetails.INDEX_TITLE:
                return context.getString(R.string.title);
            case MediaDetails.INDEX_DESCRIPTION:
                return context.getString(R.string.description);
            case MediaDetails.INDEX_DATETIME:
                return context.getString(R.string.time);
            case MediaDetails.INDEX_LOCATION:
                return context.getString(R.string.location);
            case MediaDetails.INDEX_PATH:
                return context.getString(R.string.path);
            case MediaDetails.INDEX_WIDTH:
                return context.getString(R.string.width);
            case MediaDetails.INDEX_HEIGHT:
                return context.getString(R.string.height);
            case MediaDetails.INDEX_ORIENTATION:
                return context.getString(R.string.orientation);
            case MediaDetails.INDEX_DURATION:
                return context.getString(R.string.duration);
            case MediaDetails.INDEX_MIMETYPE:
                return context.getString(R.string.mimetype);
            case MediaDetails.INDEX_SIZE:
                return context.getString(R.string.file_size);
            case MediaDetails.INDEX_MAKE:
                return context.getString(R.string.maker);
            case MediaDetails.INDEX_MODEL:
                return context.getString(R.string.model);
            case MediaDetails.INDEX_FLASH:
                return context.getString(R.string.flash);
            case MediaDetails.INDEX_APERTURE:
                return context.getString(R.string.aperture);
            case MediaDetails.INDEX_FOCAL_LENGTH:
                return context.getString(R.string.focal_length);
            case MediaDetails.INDEX_WHITE_BALANCE:
                return context.getString(R.string.white_balance);
            case MediaDetails.INDEX_EXPOSURE_TIME:
                return context.getString(R.string.exposure_time);
            case MediaDetails.INDEX_ISO:
                return context.getString(R.string.iso);
            default:
                /* SPRD: Drm feature start @{
                return "Unknown key" + key;
                */
                return MenuExecutorUtils.getInstance().getDetailsNameForDrm(context, key);
            /* SPRD: Drm feature end @} */
        }
    }

    /* SPRD: Drm feature start @{ */
    public void reloadDrmDetails(boolean isDrmDetails) {
        mContainer.reloadDrmDetails(isDrmDetails);
    }
    /* SPRD: Drm feature end @} */
}


