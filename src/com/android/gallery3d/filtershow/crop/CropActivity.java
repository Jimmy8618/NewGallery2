/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.gallery3d.filtershow.crop;

import android.app.ActionBar;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryStorageUtil;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.BitmapUtils;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.filtershow.tools.SaveImage;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.v2.interact.SdCardPermissionAccessor;
import com.android.gallery3d.v2.interact.SdCardPermissionListener;
import com.android.gallery3d.v2.util.SdCardPermission;
import com.sprd.frameworks.StandardFrameworks;
import com.sprd.gallery3d.app.CropPermissionsActivity;
import com.sprd.gallery3d.app.PermissionsActivity;
import com.sprd.gallery3d.tools.LargeImageProcessingUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;


/**
 * Activity for cropping an image.
 */
public class CropActivity extends Activity implements SdCardPermissionAccessor {
    private static final String LOGTAG = "CropActivity";
    public static final String CROP_ACTION = "com.android.camera.action.CROP";
    private CropExtras mCropExtras = null;
    private LoadBitmapTask mLoadBitmapTask = null;

    private int mOutputX = 0;
    private int mOutputY = 0;
    private Bitmap mOriginalBitmap = null;
    private RectF mOriginalBounds = null;
    private int mOriginalRotation = 0;
    private Uri mSourceUri = null;
    private CropView mCropView = null;
    private View mSaveButton = null;
    private boolean finalIOGuard = false;
    private boolean mHasCriticalPermissions;
    // SPRD: bug 533817 crop 9000*9000 wbmp image oom
    private boolean mLoadBitmapFailed = false;

    private static final int SELECT_PICTURE = 1; // request code for picker

    private static final int DEFAULT_COMPRESS_QUALITY = 90;
    /**
     * The maximum bitmap size we allow to be returned through the intent.
     * Intents have a maximum of 1MB in total size. However, the Bitmap seems to
     * have some overhead to hit so that we go way below the limit here to make
     * sure the intent stays below 1MB.We should consider just returning a byte
     * array instead of a Bitmap instance to avoid overhead.
     */
    //SPRD: Modify 20150108 of bug 391470,cant add gallery widget
    public static final int MAX_BMAP_IN_INTENT = 500000;

    // Flags
    private static final int DO_SET_WALLPAPER = 1;
    private static final int DO_RETURN_DATA = 1 << 1;
    private static final int DO_EXTRA_OUTPUT = 1 << 2;

    private static final int FLAG_CHECK = DO_SET_WALLPAPER | DO_RETURN_DATA | DO_EXTRA_OUTPUT;

    private boolean mCropInGallery = false;

    private SdCardPermissionListener mSdCardPermissionListener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (GalleryUtils.isAlnormalIntent(getIntent())) {
            finish();
            return;
        }
        setResult(RESULT_CANCELED, new Intent());
        mCropExtras = getExtrasFromIntent(intent);
        if (mCropExtras != null && mCropExtras.getShowWhenLocked()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
        mCropInGallery = intent.getBooleanExtra("crop_in_gallery", false);
        setContentView(R.layout.crop_activity);
        mCropView = findViewById(R.id.cropView);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
            actionBar.setCustomView(R.layout.filtershow_actionbar);

            /*
             * SPRD Bug:507994,change mSaveButton into a global variable@{
             * Original Android code:
            View mSaveButton = actionBar.getCustomView();
             */
            mSaveButton = actionBar.getCustomView();
            /* @} */
            mSaveButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    requestSaveImage();
                }
            });
        }

        checkPermissions();

        if (mHasCriticalPermissions) {
            if (intent.getData() != null) {
                mSourceUri = intent.getData();
                /* SPRD: bug 517500, picture display is not completely @{ */
                // startLoadBitmap(mSourceUri);
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startLoadBitmap(mSourceUri);
                    }
                }, 10);
                /* @} */
            } else {
                pickImage();
            }
        }
        // SPRD: Add to avoid holding on to a lot of memory that isn't needed.
        StandardFrameworks.getInstances().runTimeSetTargetHeapUtilization(0.75f);
    }

    private void enableSave(boolean enable) {
        if (mSaveButton != null) {
            mSaveButton.setEnabled(enable);
        }
    }

    @Override
    protected void onDestroy() {
        if (mLoadBitmapTask != null) {
            mLoadBitmapTask.cancel(false);
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mCropView.configChanged();
    }

    /**
     * Opens a selector in Gallery to chose an image for use when none was given
     * in the CROP intent.
     */
    private void pickImage() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_image)),
                SELECT_PICTURE);
    }

    /**
     * Callback for pickImage().
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case SELECT_PICTURE:
                if (resultCode == RESULT_OK) {
                    mSourceUri = data.getData();
                    startLoadBitmap(mSourceUri);
                }
                break;
            case SdCardPermission.SDCARD_PERMISSION_REQUEST_CODE:
                if (data == null || data.getData() == null) {
                    if (mSdCardPermissionListener != null) {
                        mSdCardPermissionListener.onSdCardPermissionDenied();
                        mSdCardPermissionListener = null;
                    }
                } else {
                    Uri uri = data.getData();
                    //
                    String documentId = DocumentsContract.getTreeDocumentId(uri);
                    if (!documentId.endsWith(":") || "primary:".equals(documentId)) {
                        if (mSdCardPermissionListener != null) {
                            mSdCardPermissionListener.onSdCardPermissionDenied();
                            mSdCardPermissionListener = null;
                        }
                        return;
                    }
                    //
                    final int takeFlags = data.getFlags()
                            & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    getContentResolver().takePersistableUriPermission(uri, takeFlags);

                    if (SdCardPermission.getInvalidatePermissionStorageCount() > 0) {
                        String path = SdCardPermission.getInvalidatePermissionStoragePath(0);
                        SdCardPermission.saveStorageUriPermission(path, uri.toString());
                        SdCardPermission.removeInvalidatePermissionStoragePath(0);
                        Log.d(LOGTAG, "onActivityResult uri = " + uri + ", storage = " + path);
                    }

                    if (SdCardPermission.getInvalidatePermissionStorageCount() > 0) {
                        Intent accessIntent = SdCardPermission.getAccessStorageIntent(
                                SdCardPermission.getInvalidatePermissionStoragePath(0)
                        );
                        if (accessIntent == null) {
                            if (mSdCardPermissionListener != null) {
                                mSdCardPermissionListener.onSdCardPermissionDenied();
                                mSdCardPermissionListener = null;
                            }
                        } else {
                            startActivityForResult(accessIntent, SdCardPermission.SDCARD_PERMISSION_REQUEST_CODE);
                        }
                    } else {
                        if (mSdCardPermissionListener != null) {
                            mSdCardPermissionListener.onSdCardPermissionAllowed();
                            mSdCardPermissionListener = null;
                        }
                    }
                }
                break;
            default:
                break;
        }
    }

    /**
     * Gets screen size metric.
     */
    private int getScreenImageSize() {
        DisplayMetrics outMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(outMetrics);
        return Math.max(outMetrics.heightPixels, outMetrics.widthPixels);
    }

    /**
     * Method that loads a bitmap in an async task.
     */
    private void startLoadBitmap(Uri uri) {
        if (uri != null) {
            enableSave(false);
            final View loading = findViewById(R.id.loading);
            loading.setVisibility(View.VISIBLE);
            mLoadBitmapTask = new LoadBitmapTask();
            mLoadBitmapTask.execute(uri);
        } else {
            cannotLoadImage();
            done();
        }
    }

    /**
     * Method called on UI thread with loaded bitmap.
     */
    private void doneLoadBitmap(Bitmap bitmap, RectF bounds, int orientation) {
        final View loading = findViewById(R.id.loading);
        loading.setVisibility(View.GONE);
        mOriginalBitmap = bitmap;
        mOriginalBounds = bounds;
        mOriginalRotation = orientation;
        if (bitmap != null && bitmap.getWidth() != 0 && bitmap.getHeight() != 0) {
            RectF imgBounds = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
            mCropView.initialize(bitmap, imgBounds, imgBounds, orientation);
            if (mCropExtras != null) {
                int aspectX = mCropExtras.getAspectX();
                int aspectY = mCropExtras.getAspectY();
                mOutputX = mCropExtras.getOutputX();
                mOutputY = mCropExtras.getOutputY();
                if (mOutputX > 0 && mOutputY > 0) {
                    mCropView.applyAspect(mOutputX, mOutputY);

                }
                float spotX = mCropExtras.getSpotlightX();
                float spotY = mCropExtras.getSpotlightY();
                if (spotX > 0 && spotY > 0) {
                    mCropView.setWallpaperSpotlight(spotX, spotY);
                }
                if (aspectX > 0 && aspectY > 0) {
                    mCropView.applyAspect(aspectX, aspectY);
                }
            }
            enableSave(true);
        } else {
            Log.w(LOGTAG, "could not load image for cropping");
            cannotLoadImage();
            setResult(RESULT_CANCELED, new Intent());
            done();
        }
    }

    /**
     * Display toast for image loading failure.
     */
    private void cannotLoadImage() {
        CharSequence text = getString(R.string.cannot_load_image);
        Toast toast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        toast.show();
    }

    /* SPRD: bug 533817, crop 9000*9000 wbmp image oom  @{ */
    private void cannotLoadImageCausedByLowMemory() {
        CharSequence text = getString(R.string.cannot_load_crop_image);
        Toast toast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        toast.show();
    }
    /* @} */

    /**
     * AsyncTask for loading a bitmap into memory.
     *
     * @see #startLoadBitmap(Uri)
     */
    private class LoadBitmapTask extends AsyncTask<Uri, Void, Bitmap> {
        int mBitmapSize;
        Context mContext;
        Rect mOriginalBounds;
        int mOrientation;

        public LoadBitmapTask() {
            mBitmapSize = getScreenImageSize();
            mContext = getApplicationContext();
            mOriginalBounds = new Rect();
            mOrientation = 0;
        }

        @Override
        protected Bitmap doInBackground(Uri... params) {
            Uri uri = params[0];
            Bitmap bmap = null;
            try {
                bmap = ImageLoader.loadConstrainedBitmap(uri, mContext, mBitmapSize,
                        mOriginalBounds, false);
            } catch (OutOfMemoryError e) {
                Log.w(LOGTAG, "<LoadBitmapTask> load constrained bitmap failed." + e);
                mLoadBitmapFailed = true;
                bmap = null;
                System.gc();
            }

            /* SPRD: bug 533817, crop 9000*9000 wbmp image oom  @{ */
            // SPRD: Modify 20151219 for bug515432, bmap may be null here. @{
            if (bmap != null) {
                // SPRD: Modify 20151214 for bug507565, get max texture size from mali before bitmap is going to draw,
                // otherwise, it is may be black when crop a image if size ofimage is too large @{
                int maxTextureSize = LargeImageProcessingUtils.computeEglMaxTextureSize();
                while (bmap != null && (bmap.getWidth() > maxTextureSize || bmap.getHeight() > maxTextureSize)) {
                    // As long as width or height of bitmap is larger than max
                    // texture size, it need to be resize
                    Log.d(LOGTAG, "<LoadBitmapTask> bitmap is still too large, need resize.");
                    bmap = LargeImageProcessingUtils.resizeBitmapByScale(bmap, (float) (2 / 3), true);
                    if (bmap == null) {
                        mLoadBitmapFailed = true;
                    }
                }
            }
            // SPRD: Modify 20151219 for bug515432 @}
            /* @} */
            mOrientation = ImageLoader.getMetadataRotation(mContext, uri);
            return bmap;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            /* SPRD: bug 533817 crop 9000*9000 wbmp image oom @{ */
            if (result == null && mLoadBitmapFailed) {
                cannotLoadImageCausedByLowMemory();
                done();
                return;
            }
            /* @} */
            doneLoadBitmap(result, new RectF(mOriginalBounds), mOrientation);
        }
    }

    protected void startFinishOutput() {
        if (finalIOGuard) {
            return;
        } else {
            finalIOGuard = true;
        }
        enableSave(false);
        Uri destinationUri = null;
        int flags = 0;
        if (mOriginalBitmap != null && mCropExtras != null) {
            if (mCropExtras.getExtraOutput() != null) {
                destinationUri = mCropExtras.getExtraOutput();
                if (destinationUri != null) {
                    flags |= DO_EXTRA_OUTPUT;
                }
            }
            if (mCropExtras.getSetAsWallpaper()) {
                flags |= DO_SET_WALLPAPER;
            }
            if (mCropExtras.getReturnData()) {
                flags |= DO_RETURN_DATA;
            }
        }
        if (flags == 0) {
            //if (Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.Q) {
            //    destinationUri = SaveImage.makeUri(this, mSourceUri);
            //} else {
            destinationUri = SaveImage.makeAndInsertUri(this, mSourceUri);
            //}
            if (destinationUri != null) {
                flags |= DO_EXTRA_OUTPUT;
            }
        }
        if ((flags & FLAG_CHECK) != 0 && mOriginalBitmap != null) {
            RectF photo = new RectF(0, 0, mOriginalBitmap.getWidth(), mOriginalBitmap.getHeight());
            RectF crop = getBitmapCrop(photo);
            startBitmapIO(flags, mOriginalBitmap, mSourceUri, destinationUri, crop,
                    photo, mOriginalBounds,
                    (mCropExtras == null) ? null : mCropExtras.getOutputFormat(), mOriginalRotation);
            return;
        }
        setResult(RESULT_CANCELED, new Intent());
        done();
        return;
    }

    private void startBitmapIO(int flags, Bitmap currentBitmap, Uri sourceUri, Uri destUri,
                               RectF cropBounds, RectF photoBounds, RectF currentBitmapBounds, String format,
                               int rotation) {
        if (cropBounds == null || photoBounds == null || currentBitmap == null
                || currentBitmap.getWidth() == 0 || currentBitmap.getHeight() == 0
                || cropBounds.width() == 0 || cropBounds.height() == 0 || photoBounds.width() == 0
                || photoBounds.height() == 0) {
            return; // fail fast
        }
        if ((flags & FLAG_CHECK) == 0) {
            return; // no output options
        }
        if ((flags & DO_SET_WALLPAPER) != 0) {
            Toast.makeText(this, R.string.setting_wallpaper, Toast.LENGTH_LONG).show();
        }

        final View loading = findViewById(R.id.loading);
        loading.setVisibility(View.VISIBLE);
        BitmapIOTask ioTask = new BitmapIOTask(sourceUri, destUri, format, flags, cropBounds,
                photoBounds, currentBitmapBounds, rotation, mOutputX, mOutputY);
        ioTask.execute(currentBitmap);
    }

    private void doneBitmapIO(boolean success, Intent intent) {
        final View loading = findViewById(R.id.loading);
        loading.setVisibility(View.GONE);
        if (success) {
            setResult(RESULT_OK, intent);
        } else {
            setResult(RESULT_CANCELED, intent);
        }
        done();
    }

    private class BitmapIOTask extends AsyncTask<Bitmap, Void, Boolean> {

        private final WallpaperManager mWPManager;
        InputStream mInStream = null;
        OutputStream mOutStream = null;
        String mOutputFormat = null;
        Uri mOutUri = null;
        Uri mInUri = null;
        int mFlags = 0;
        RectF mCrop = null;
        RectF mPhoto = null;
        RectF mOrig = null;
        Intent mResultIntent = null;
        int mRotation = 0;

        // Helper to setup input stream
        private void regenerateInputStream() {
            if (mInUri == null) {
                Log.w(LOGTAG, "cannot read original file, no input URI given");
            } else {
                Utils.closeSilently(mInStream);
                try {
                    mInStream = getContentResolver().openInputStream(mInUri);
                } catch (FileNotFoundException e) {
                    Log.w(LOGTAG, "cannot read file: " + mInUri.toString(), e);
                } catch (Exception e) {//SPRD: for bug 496882,crop not existent image,crash
                    Log.w(LOGTAG, "cannot read file by use this URI : " + mInUri.toString(), e);
                }
            }
        }

        public BitmapIOTask(Uri sourceUri, Uri destUri, String outputFormat, int flags,
                            RectF cropBounds, RectF photoBounds, RectF originalBitmapBounds, int rotation,
                            int outputX, int outputY) {
            mOutputFormat = outputFormat;
            mOutStream = null;
            mOutUri = destUri;
            mInUri = sourceUri;
            mFlags = flags;
            mCrop = cropBounds;
            mPhoto = photoBounds;
            mOrig = originalBitmapBounds;
            mWPManager = WallpaperManager.getInstance(getApplicationContext());
            mResultIntent = new Intent();
            mRotation = (rotation < 0) ? -rotation : rotation;
            mRotation %= 360;
            mRotation = 90 * mRotation / 90;  // now mRotation is a multiple of 90
            mOutputX = outputX;
            mOutputY = outputY;

            /* SPRD: Add 20151228 by Spreadst for bug518495, destinationUri should not be the same as
             * mSourceUri, openOutputStream after mInStream has been used. @{
            if ((flags & DO_EXTRA_OUTPUT) != 0) {
                if (mOutUri == null) {
                    Log.w(LOGTAG, "cannot write file, no output URI given");
                } else {
                    try {
                        mOutStream = getContentResolver().openOutputStream(mOutUri);
                    } catch (FileNotFoundException e) {
                        Log.w(LOGTAG, "cannot write file: " + mOutUri.toString(), e);
                    }
                }
            } */
            /* @} */

            if ((flags & (DO_EXTRA_OUTPUT | DO_SET_WALLPAPER)) != 0) {
                regenerateInputStream();
            }
        }

        @Override
        protected Boolean doInBackground(Bitmap... params) {
            boolean failure = false;
            Bitmap img = params[0];
            if (mCropInGallery) {
                if (mRotation == 270 || mRotation == 90) {
                    float w = mPhoto.right - mPhoto.left;
                    float h = mPhoto.bottom - mPhoto.top;
                    RectF newPhoto = new RectF(0, 0, h, w);
                    Log.d(LOGTAG,
                            "BitmapIOTask.doInBackground mPhoto : " + mPhoto + " --> " + newPhoto);
                    mPhoto = newPhoto;
                }

            }

            // Set extra for crop bounds
            if (mCrop != null && mPhoto != null && mOrig != null) {
                RectF trueCrop = CropMath.getScaledCropBounds(mCrop, mPhoto, mOrig);
                Matrix m = new Matrix();
                m.setRotate(mRotation);
                m.mapRect(trueCrop);
                if (trueCrop != null) {
                    Rect rounded = new Rect();
                    trueCrop.roundOut(rounded);
                    mResultIntent.putExtra(CropExtras.KEY_CROPPED_RECT, rounded);
                }
            }

            // Find the small cropped bitmap that is returned in the intent
            if ((mFlags & DO_RETURN_DATA) != 0) {
                assert (img != null);
                Bitmap ret = getCroppedImage(img, mCrop, mPhoto);
                if (ret != null) {
                    ret = getDownsampledBitmap(ret, MAX_BMAP_IN_INTENT);
                }
                if (ret == null) {
                    Log.w(LOGTAG, "could not downsample bitmap to return in data");
                    failure = true;
                } else {
                    if (mRotation > 0) {
                        Matrix m = new Matrix();
                        m.setRotate(mRotation);
                        Bitmap tmp = Bitmap.createBitmap(ret, 0, 0, ret.getWidth(),
                                ret.getHeight(), m, true);
                        if (tmp != null) {
                            ret = tmp;
                        }
                    }
                    mResultIntent.putExtra(CropExtras.KEY_DATA, ret);
                }
            }

            // Do the large cropped bitmap and/or set the wallpaper
            if ((mFlags & (DO_EXTRA_OUTPUT | DO_SET_WALLPAPER)) != 0 && mInStream != null) {
                // Find crop bounds (scaled to original image size)
                RectF trueCrop = CropMath.getScaledCropBounds(mCrop, mPhoto, mOrig);
                if (trueCrop == null) {
                    Log.w(LOGTAG, "cannot find crop for full size image");
                    failure = true;
                    deleteOutUri();
                    return false;
                }
                Rect roundedTrueCrop = new Rect();
                trueCrop.roundOut(roundedTrueCrop);

                if (roundedTrueCrop.width() <= 0 || roundedTrueCrop.height() <= 0) {
                    Log.w(LOGTAG, "crop has bad values for full size image");
                    failure = true;
                    deleteOutUri();
                    return false;
                }

                // Attempt to open a region decoder
                BitmapRegionDecoder decoder = null;
                Bitmap crop = null;
                // If image format is not supported by region decoder, do not do anything here
                if (BitmapUtils.isSupportedByRegionDecoder(LargeImageProcessingUtils.getType(
                        mInUri, CropActivity.this))) {
                    try {
                        decoder = BitmapRegionDecoder.newInstance(mInStream, true);
                    } catch (IOException e) {
                        Log.w(LOGTAG, "cannot open region decoder for file: " + mInUri.toString(),
                                e);
                    }
                    /*
                     * SPRD: Modify for bug537533 start. Add some exception catch here to avoid crash
                     * and try to resize bitmap to avoid OOM @{
                     */
                    if (decoder != null) {
                        /* SPRD: bug632435, decoder bitmap, java.lang.IllegalArgumentException @{ */
                        if (roundedTrueCrop.right <= 0 || roundedTrueCrop.bottom <= 0
                                || roundedTrueCrop.left >= decoder.getWidth()
                                || roundedTrueCrop.top >= decoder.getHeight()) {
                            Log.d(LOGTAG, "roundedTrueCrop is outside the image ");
                            RectF newTrueCrop = CropMath.getScaledCropBounds(mCrop, mPhoto,
                                    new RectF(new Rect(0, 0, decoder.getWidth(), decoder.getHeight())));
                            //roundedTrueCrop outside the image, use decoder to get new roundedTrueCrop
                            newTrueCrop.roundOut(roundedTrueCrop);
                        }
                        /* @} */
                        // Do region decoding to get crop bitmap
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inMutable = true;
                        /* SPRD: fix bug 505771 OOM when crop picture @{ */
                        // crop = decoder.decodeRegion(roundedTrueCrop, options);
                        // options.inSampleSize = LargeImageProcessingUtils.getProperSampleSize(
                        // roundedTrueCrop.width(), roundedTrueCrop.height());
                        try {
                            // crop = decoder.decodeRegion(roundedTrueCrop, options);
                            crop = LargeImageProcessingUtils.decodeRegion(decoder, roundedTrueCrop,
                                    options);
                        } catch (OutOfMemoryError e) {
                            Log.w(LOGTAG, "<BitmapIOTask> decodeRegion failed." + e);
                            crop = null;
                            System.gc();
                        }
                        /* @} */
                        decoder.recycle();
                    }
                }

                if (crop == null) {
                    // BitmapRegionDecoder has failed, try to crop in-memory
                    regenerateInputStream();
                    Bitmap fullSize = null;
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    if (mInStream != null) {
                        // fullSize = BitmapFactory.decodeStream(mInStream);
                        options.inJustDecodeBounds = false;
                        options.inSampleSize = LargeImageProcessingUtils.getProperSampleSize(
                                roundedTrueCrop.width(), roundedTrueCrop.height());
                        try {
                            fullSize = BitmapFactory.decodeStream(mInStream, null, options);
                        } catch (OutOfMemoryError e) {
                            Log.w(LOGTAG, "<BitmapIOTask> decodeStream failed." + e);
                            fullSize = null;
                            System.gc();
                        }
                    }
                    if (fullSize != null) {
                        // SPRD: Modify 20150818 Spreadst of bug505771, use createBitmapWithFixedSize instead
                        // crop = Bitmap.createBitmap(fullSize, roundedTrueCrop.left, roundedTrueCrop.top, roundedTrueCrop.width(), roundedTrueCrop.height());
                        // SPRD: bug 533817 crop 9000*9000 wbmp image oom
                        if (options.inSampleSize > 1) {
                            if (mCrop != null && mPhoto != null) {
                                // fullSize bitmap is resized, need adjust roundedTrueCrop
                                RectF fullSizeRect = new RectF(0, 0, fullSize.getWidth(), fullSize.getHeight());
                                RectF fullSizeTrueCrop = CropMath.getScaledCropBounds(mCrop,
                                        mPhoto, fullSizeRect);
                                fullSizeTrueCrop.roundOut(roundedTrueCrop);
                            }
                        }
                        crop = LargeImageProcessingUtils.createBitmapWithFixedSize(fullSize,
                                roundedTrueCrop.left,
                                roundedTrueCrop.top, roundedTrueCrop.width(),
                                roundedTrueCrop.height());
                    }
                }
                /* Modify for bug537533 end @} */
                if (crop == null) {
                    Log.w(LOGTAG, "cannot decode file: " + mInUri.toString());
                    failure = true;
                    deleteOutUri();
                    return false;
                }
                /* SPRD: Add 20151228 by Spreadst for bug518495, destinationUri should not be the same as
                 * mSourceUri. In fact, it is a bug of third-party applications. @{
                 */
                if ((mFlags & DO_EXTRA_OUTPUT) != 0) {
                    if (mOutUri == null) {
                        Log.w(LOGTAG, "cannot write file, no output URI given");
                    } else {
                        try {
                            File dest = SaveImage.getLocalFileFromUri(CropActivity.this, mOutUri);
                            Log.d(LOGTAG, "BitmapIOTask.doInBackground mOutUri = " + mOutUri + ", dest = " + dest);
                            if (Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.Q) {
                                mOutStream = getContentResolver().openOutputStream(mOutUri);
                            } else {
                                if (dest != null && !GalleryStorageUtil.isInInternalStorage(dest.getAbsolutePath())) {
                                    //先创建文件
                                    SdCardPermission.mkFile(dest);
                                    mOutStream = SdCardPermission.createExternalOutputStream(dest.getAbsolutePath());
                                } else {
                                    mOutStream = getContentResolver().openOutputStream(mOutUri);
                                }
                            }
                        } catch (FileNotFoundException e) {
                            Log.w(LOGTAG, "cannot write file: " + mOutUri.toString(), e);
                        } catch (SecurityException e) {
                            Log.w(LOGTAG, "current process has no permission", e);
                        }
                    }
                }
                /* @} */
                if (mOutputX > 0 && mOutputY > 0) {
                    Matrix m = new Matrix();
                    RectF cropRect = new RectF(0, 0, crop.getWidth(), crop.getHeight());
                    if (mRotation > 0) {
                        m.setRotate(mRotation);
                        m.mapRect(cropRect);
                    }
                    RectF returnRect = new RectF(0, 0, mOutputX, mOutputY);
                    m.setRectToRect(cropRect, returnRect, Matrix.ScaleToFit.FILL);
                    m.preRotate(mRotation);
                    Bitmap tmp = Bitmap.createBitmap((int) returnRect.width(),
                            (int) returnRect.height(), Bitmap.Config.ARGB_8888);
                    if (tmp != null) {
                        Canvas c = new Canvas(tmp);
                        c.drawBitmap(crop, m, new Paint());
                        crop = tmp;
                    }
                } else if (mRotation > 0) {
                    Matrix m = new Matrix();
                    m.setRotate(mRotation);
                    Bitmap tmp = Bitmap.createBitmap(crop, 0, 0, crop.getWidth(),
                            crop.getHeight(), m, true);
                    if (tmp != null) {
                        crop = tmp;
                    }
                }
                // Get output compression format
                CompressFormat cf =
                        convertExtensionToCompressFormat(getFileExtension(mOutputFormat));

                // If we only need to output to a URI, compress straight to file
                if (mFlags == DO_EXTRA_OUTPUT) {
                    if (mOutStream == null
                            || !crop.compress(cf, DEFAULT_COMPRESS_QUALITY, mOutStream)) {
                        Log.w(LOGTAG, "failed to compress bitmap to file: " + mOutUri.toString());
                        failure = true;
                    } else {
                        //SPRD: fix bug379487,545287 update the cropped picture's size,width,height
                        //if (Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.Q) {
                        //    SaveImage.updateFilePending(getApplicationContext(), mOutUri,
                        //            crop.getWidth(), crop.getHeight());
                        //} else {
                        SaveImage.updateFileByUri(getApplicationContext(), mOutUri,
                                crop.getWidth(), crop.getHeight());
                        //}
                        mResultIntent.setData(mOutUri);
                    }
                } else {
                    // Compress to byte array
                    ByteArrayOutputStream tmpOut = new ByteArrayOutputStream(2048);
                    if (crop.compress(cf, DEFAULT_COMPRESS_QUALITY, tmpOut)) {

                        // If we need to output to a Uri, write compressed
                        // bitmap out
                        if ((mFlags & DO_EXTRA_OUTPUT) != 0) {
                            if (mOutStream == null) {
                                Log.w(LOGTAG,
                                        "failed to compress bitmap to file: " + mOutUri.toString());
                                failure = true;
                            } else {
                                try {
                                    mOutStream.write(tmpOut.toByteArray());
                                    mResultIntent.setData(mOutUri);
                                } catch (IOException e) {
                                    Log.w(LOGTAG,
                                            "failed to compress bitmap to file: "
                                                    + mOutUri.toString(), e);
                                    failure = true;
                                }
                            }
                        }

                        // If we need to set to the wallpaper, set it
                        if ((mFlags & DO_SET_WALLPAPER) != 0 && mWPManager != null) {
                            if (mWPManager == null) {
                                Log.w(LOGTAG, "no wallpaper manager");
                                failure = true;
                            } else {
                                try {
                                    mWPManager.setStream(new ByteArrayInputStream(tmpOut
                                            .toByteArray()));
                                } catch (IOException e) {
                                    Log.w(LOGTAG, "cannot write stream to wallpaper", e);
                                    failure = true;
                                }
                            }
                        }
                    } else {
                        Log.w(LOGTAG, "cannot compress bitmap");
                        failure = true;
                    }
                }
            }
            Log.d(LOGTAG, "Save mInStream = " + mInStream + ", mOutUri = " + mOutUri + ", failure = " + failure);
            if (failure || (mInStream == null && mOutUri != null)) {
                failure = true;
                deleteOutUri();
            }
            return !failure; // True if any of the operations failed
        }

        private void deleteOutUri() {
            Log.e(LOGTAG, "crop image failed, delete mOutUri = " + mOutUri);
            if (mOutUri != null) {
                getContentResolver().delete(mOutUri, null, null);
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (!result) {
                Toast.makeText(CropActivity.this, R.string.decode_image_faild, Toast.LENGTH_LONG).show();
            }
            Utils.closeSilently(mOutStream);
            Utils.closeSilently(mInStream);
            doneBitmapIO(result.booleanValue(), mResultIntent);
        }

    }

    private void done() {
        finish();
    }

    protected static Bitmap getCroppedImage(Bitmap image, RectF cropBounds, RectF photoBounds) {
        RectF imageBounds = new RectF(0, 0, image.getWidth(), image.getHeight());
        RectF crop = CropMath.getScaledCropBounds(cropBounds, photoBounds, imageBounds);
        if (crop == null) {
            return null;
        }
        Rect intCrop = new Rect();
        crop.roundOut(intCrop);
        // SPRD: Modify 20150818 Spreadst of bug505771, use createBitmapWithFixedSize instead
        // return Bitmap.createBitmap(image, intCrop.left, intCrop.top, intCrop.width(), intCrop.height());
        return LargeImageProcessingUtils.createBitmapWithFixedSize(image, intCrop.left,
                intCrop.top, intCrop.width(),
                intCrop.height());
    }

    protected static Bitmap getDownsampledBitmap(Bitmap image, int max_size) {
        if (image == null || image.getWidth() == 0 || image.getHeight() == 0 || max_size < 16) {
            throw new IllegalArgumentException("Bad argument to getDownsampledBitmap()");
        }
        int shifts = 0;
        int size = CropMath.getBitmapSize(image);
        while (size > max_size) {
            shifts++;
            size /= 4;
        }
        Bitmap ret = Bitmap.createScaledBitmap(image, image.getWidth() >> shifts,
                image.getHeight() >> shifts, true);
        if (ret == null) {
            return null;
        }
        // Handle edge case for rounding.
        if (CropMath.getBitmapSize(ret) > max_size) {
            return Bitmap.createScaledBitmap(ret, ret.getWidth() >> 1, ret.getHeight() >> 1, true);
        }
        return ret;
    }

    /**
     * Gets the crop extras from the intent, or null if none exist.
     */
    protected static CropExtras getExtrasFromIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras != null) {
            return new CropExtras(extras.getInt(CropExtras.KEY_OUTPUT_X, 0),
                    extras.getInt(CropExtras.KEY_OUTPUT_Y, 0),
                    extras.getBoolean(CropExtras.KEY_SCALE, true) &&
                            extras.getBoolean(CropExtras.KEY_SCALE_UP_IF_NEEDED, false),
                    extras.getInt(CropExtras.KEY_ASPECT_X, 0),
                    extras.getInt(CropExtras.KEY_ASPECT_Y, 0),
                    extras.getBoolean(CropExtras.KEY_SET_AS_WALLPAPER, false),
                    extras.getBoolean(CropExtras.KEY_RETURN_DATA, false),
                    (Uri) extras.getParcelable(MediaStore.EXTRA_OUTPUT),
                    extras.getString(CropExtras.KEY_OUTPUT_FORMAT),
                    extras.getBoolean(CropExtras.KEY_SHOW_WHEN_LOCKED, false),
                    extras.getFloat(CropExtras.KEY_SPOTLIGHT_X),
                    extras.getFloat(CropExtras.KEY_SPOTLIGHT_Y));
        }
        return null;
    }

    protected static CompressFormat convertExtensionToCompressFormat(String extension) {
        return extension.equals("png") ? CompressFormat.PNG : CompressFormat.JPEG;
    }

    protected static String getFileExtension(String requestFormat) {
        String outputFormat = (requestFormat == null)
                ? "jpg"
                : requestFormat;
        outputFormat = outputFormat.toLowerCase();
        return (outputFormat.equals("png") || outputFormat.equals("gif"))
                ? "png" // We don't support gif compression.
                : "jpg";
    }

    private RectF getBitmapCrop(RectF imageBounds) {
        RectF crop = mCropView.getCrop();
        RectF photo = mCropView.getPhoto();
        if (crop == null || photo == null) {
            Log.w(LOGTAG, "could not get crop");
            return null;
        }
        RectF scaledCrop = CropMath.getScaledCropBounds(crop, photo, imageBounds);
        return scaledCrop;
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        if (isInMultiWindowMode) {
            android.util.Log.d(LOGTAG, "onMultiWindowModeChanged: " + isInMultiWindowMode);
            Toast.makeText(this, R.string.exit_multiwindow_tips, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        GalleryUtils.killActivityInMultiWindow(this, GalleryUtils.DONT_SUPPORT_VIEW_PHOTOS);
    }

    /*SPRD:add for bug598607 Lock screen switch screen, open the picture is abnormal@{*/
    @Override
    protected void onRestart() {
        super.onRestart();
        mCropView.configChanged();
        mCropView.invalidate();
    }

    /* @} */
    /* SPRD: add check gallery permissions @{ */
    private void checkPermissions() {
        mHasCriticalPermissions = true;
        if (getIntent().getBooleanExtra("from_photo_page", false)) {
            mHasCriticalPermissions = GalleryUtils.checkStoragePermissions(this);
            if (!mHasCriticalPermissions) {
                Toast.makeText(CropActivity.this, R.string.gallery_premission_error,
                        Toast.LENGTH_LONG).show();
                finish();
            }
        } else {
            mHasCriticalPermissions = GalleryUtils.checkStoragePermissions(this);
            if (!mHasCriticalPermissions) {
                if (GalleryUtils.requestPermission(this, CropPermissionsActivity.class, PermissionsActivity
                        .START_FROM_CROP)) {
                    finish();
                }
            }

        }

    }
    /* @} */

    private void requestSaveImage() {
        File dest = SaveImage.getFinalSaveDirectory(this, mSourceUri);
        String filePath = dest.getAbsolutePath();
        if (!GalleryStorageUtil.isInInternalStorage(filePath)
                && !SdCardPermission.hasStoragePermission(filePath)) {
            SdCardPermissionListener sdCardPermissionListener = new SdCardPermissionListener() {
                @Override
                public void onSdCardPermissionAllowed() {
                    startFinishOutput();
                }

                @Override
                public void onSdCardPermissionDenied() {
                    SdCardPermission.showSdcardPermissionErrorDialog(CropActivity.this, null);
                }
            };
            ArrayList<String> storagePaths = new ArrayList<>();
            storagePaths.add(filePath);
            SdCardPermission.requestSdcardPermission(this, storagePaths, this, sdCardPermissionListener);
        } else {
            startFinishOutput();
        }
    }

    @Override
    public void setSdCardPermissionListener(SdCardPermissionListener sdCardPermissionListener) {
        mSdCardPermissionListener = sdCardPermissionListener;
    }
}
