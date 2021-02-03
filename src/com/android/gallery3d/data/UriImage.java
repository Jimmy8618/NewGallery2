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

package com.android.gallery3d.data;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.drm.DrmStore;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory.Options;
import android.graphics.BitmapRegionDecoder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import androidx.core.content.FileProvider;

import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.app.GalleryStorageUtil;
import com.android.gallery3d.app.PanoramaMetadataSupport;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.BitmapUtils;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.ThreadPool.CancelListener;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;
import com.sprd.drmgalleryplugin.util.DrmUtil;
import com.sprd.frameworks.StandardFrameworks;
import com.sprd.gallery3d.drm.UriImageDrmUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;

public class UriImage extends MediaItem {
    private static final String TAG = "UriImage";

    private static final int STATE_INIT = 0;
    private static final int STATE_DOWNLOADING = 1;
    private static final int STATE_DOWNLOADED = 2;
    private static final int STATE_ERROR = -1;

    // SPRD: bug613322 UriImage support DRM image
    private Uri mUri;
    private final String mContentType;
    private String mFileName;

    private DownloadCache.Entry mCacheEntry;
    private ParcelFileDescriptor mFileDescriptor;
    private int mState = STATE_INIT;
    private int mWidth;
    private int mHeight;
    private int mRotation;
    private int mMediaType = MEDIA_TYPE_IMAGE;
    private PanoramaMetadataSupport mPanoramaMetadata = new PanoramaMetadataSupport(this);

    private GalleryApp mApplication;
    // SPRD: Add for bug620619, do not show "set as" menu if decode failed
    private boolean mIsSupportSetAs = true;
    private static boolean isLowRam = StandardFrameworks.getInstances().isLowRam();
    private static boolean isGmsVersion = false;

    private OnUriImageListener mOnUriImageListener;

    public interface OnUriImageListener {
        void onUriImageInitialized();
    }

    public UriImage(GalleryApp application, Path path, Uri uri, String contentType) {
        super(path, nextVersionNumber());
        mUri = uri;
        mApplication = Utils.checkNotNull(application);
        mContentType = contentType;
        mFileName = getDisplayName();
        isGmsVersion = GalleryUtils.isSprdPhotoEdit();
    }

    public void init(OnUriImageListener l) {
        mOnUriImageListener = l;
        Task task = new Task();
        task.execute();
    }

    public void queryFileFlag() {
        Cursor cursor = null;
        String[] proj = {MediaStore.Images.Media.DATA, "file_flag", "_id", MediaStore.Images.ImageColumns.DISPLAY_NAME};
        Uri baseUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String clause = MediaStore.Images.Media.DATA + " = ?";

        Log.d(TAG, "queryFileFlag before transformUri, mUri = " + mUri + ", authority = " + mUri.getAuthority());
        mUri = transformUri(mUri);
        Log.d(TAG, "queryFileFlag after  transformUri, mUri = " + mUri + ", authority = " + mUri.getAuthority());

        if (mUri != null) {
            if ("file".equals(mUri.getScheme())) {
                filePath = mUri.getPath();
            } else {
                try {
                    cursor = mApplication.getAndroidContext().getContentResolver().query(mUri,
                            new String[]{MediaStore.Images.Media.DATA}, null, null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        filePath = cursor.getString(0);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    Utils.closeSilently(cursor);
                }
            }
        }

        if (filePath != null) {
            try {
                cursor = mApplication.getContentResolver().query(baseUri,
                        proj, clause, new String[]{filePath}, null);
                if (cursor != null && cursor.moveToFirst()) {
                    mFileFlag = cursor.getInt(1);
                    mUri = ContentUris.withAppendedId(baseUri, cursor.getInt(2));
                    mFileName = cursor.getString(3);
                    //@{ 根据文件名判断是否是 Motion Photo
                    if (mFileName != null && GalleryUtils.isMotionPhoto(mFileName, mFileFlag)) {
                        if (mFileFlag == LocalImage.IMG_TYPE_MODE_HDR) {
                            mFileFlag = LocalImage.IMG_TYPE_MODE_MOTION_HDR_PHOTO;
                        } else if (mFileFlag == LocalImage.IMG_TYPE_MODE_AI_SCENE) {
                            mFileFlag = LocalImage.IMG_TYPE_MODE_MOTION_AI_PHOTO;
                        } else if (mFileFlag == LocalImage.IMG_TYPE_MODE_AI_SCENE_HDR) {
                            mFileFlag = LocalImage.IMG_TYPE_MODE_MOTION_HDR_AI_PHOTO;
                        } else if (mFileFlag == LocalImage.IMG_TYPE_MODE_FDR) {
                            mFileFlag = LocalImage.IMG_TYPE_MODE_MOTION_FDR_PHOTO;
                        } else if (mFileFlag == LocalImage.IMG_TYPE_MODE_AI_SCENE_FDR) {
                            mFileFlag = LocalImage.IMG_TYPE_MODE_MOTION_FDR_AI_PHOTO;
                        } else {
                            mFileFlag = LocalImage.IMG_TYPE_MODE_MOTION_PHOTO;
                        }
                    }
                    //@}
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Utils.closeSilently(cursor);
            }
        }
    }

    @Override
    public Job<Bitmap> requestImage(int type) {
        return new BitmapJob(type);
    }

    @Override
    public Job<BitmapRegionDecoder> requestLargeImage() {
        return new RegionDecoderJob();
    }

    private void openFileOrDownloadTempFile(JobContext jc) {
        int state = openOrDownloadInner(jc);
        synchronized (this) {
            mState = state;
            if (mState != STATE_DOWNLOADED) {
                if (mFileDescriptor != null) {
                    Utils.closeSilently(mFileDescriptor);
                    mFileDescriptor = null;
                }
            }
            notifyAll();
        }
    }

    private int openOrDownloadInner(JobContext jc) {
        String scheme = mUri.getScheme();
        if (ContentResolver.SCHEME_CONTENT.equals(scheme)
                || ContentResolver.SCHEME_ANDROID_RESOURCE.equals(scheme)
                || ContentResolver.SCHEME_FILE.equals(scheme)) {
            try {
                if (MIME_TYPE_JPEG.equalsIgnoreCase(mContentType)) {
                    InputStream is = mApplication.getContentResolver()
                            .openInputStream(mUri);
                    mRotation = Exif.getOrientation(is);
                    Utils.closeSilently(is);
                }
                mFileDescriptor = mApplication.getContentResolver()
                        .openFileDescriptor(mUri, "r");
                if (jc.isCancelled()) {
                    return STATE_INIT;
                }
                return STATE_DOWNLOADED;
            } catch (FileNotFoundException e) {
                Log.w(TAG, "fail to open: " + mUri, e);
                return STATE_ERROR;
            }
        } else {
            try {
                URL url = new URI(mUri.toString()).toURL();
                mCacheEntry = mApplication.getDownloadCache().download(jc, url);
                if (jc.isCancelled()) {
                    return STATE_INIT;
                }
                if (mCacheEntry == null) {
                    Log.w(TAG, "download failed " + url);
                    return STATE_ERROR;
                }
                if (MIME_TYPE_JPEG.equalsIgnoreCase(mContentType)) {
                    InputStream is = new FileInputStream(mCacheEntry.cacheFile);
                    mRotation = Exif.getOrientation(is);
                    Utils.closeSilently(is);
                }
                mFileDescriptor = ParcelFileDescriptor.open(
                        mCacheEntry.cacheFile, ParcelFileDescriptor.MODE_READ_ONLY);
                return STATE_DOWNLOADED;
            } catch (Throwable t) {
                Log.w(TAG, "download error", t);
                return STATE_ERROR;
            }
        }
    }

    private boolean prepareInputFile(JobContext jc) {
        jc.setCancelListener(new CancelListener() {
            @Override
            public void onCancel() {
                synchronized (this) {
                    notifyAll();
                }
            }
        });

        while (true) {
            synchronized (this) {
                if (jc.isCancelled()) {
                    return false;
                }
                if (mState == STATE_INIT) {
                    mState = STATE_DOWNLOADING;
                    // Then leave the synchronized block and continue.
                } else if (mState == STATE_ERROR) {
                    return false;
                } else if (mState == STATE_DOWNLOADED) {
                    return true;
                } else /* if (mState == STATE_DOWNLOADING) */ {
                    try {
                        wait();
                    } catch (InterruptedException ex) {
                        // ignored.
                    }
                    continue;
                }
            }
            // This is only reached for STATE_INIT->STATE_DOWNLOADING
            openFileOrDownloadTempFile(jc);
        }
    }

    private class RegionDecoderJob implements Job<BitmapRegionDecoder> {
        @Override
        public BitmapRegionDecoder run(JobContext jc) {
            if (!prepareInputFile(jc)) {
                mIsSupportSetAs = false;
                showToast();
                return null;
            }
            BitmapRegionDecoder decoder = DecodeUtils.createBitmapRegionDecoder(
                    jc, mFileDescriptor.getFileDescriptor(), false);
            mWidth = decoder.getWidth();
            mHeight = decoder.getHeight();
            return decoder;
        }
    }

    private class BitmapJob implements Job<Bitmap> {
        private int mType;

        protected BitmapJob(int type) {
            mType = type;
        }

        @Override
        public Bitmap run(JobContext jc) {
            if (!prepareInputFile(jc)) {
                mIsSupportSetAs = false;
                showToast();
                getPath().recycleObject();
                return null;
            }
            int targetSize = MediaItem.getTargetSize(mType);
            Options options = new Options();
            options.inPreferredConfig = isLowRam ? Config.ARGB_4444 : Config.ARGB_8888;
            /* SPRD: bug613322 UriImage support DRM image @{ */
//            Bitmap bitmap = DecodeUtils.decodeThumbnail(jc,
//                    mFileDescriptor.getFileDescriptor(), options, targetSize, mType);
            Bitmap bitmap = null;
            boolean isDRM = DrmUtil.isDrmFile(mUri, null) && DrmUtil.isDrmValid(mUri);
            Log.d(TAG, "BitmapJob.run: mUri = " + mUri + ", filePath = " + filePath + ", isDRM = " + isDRM + ", mUri.getPath() = " + mUri.getPath());
            if (GalleryStorageUtil.isInInternalStorage(filePath) || isDRM) {
                bitmap = UriImageDrmUtils.getInstance().decodeUriDrmImage(jc,
                        mType, UriImage.this, options, targetSize, mFileDescriptor.getFileDescriptor());
            } else {
                //getFilePath()
                bitmap = DecodeUtils.decodeThumbnail(jc, mUri, options, targetSize, mType);
            }
            /* @} */

            if (jc.isCancelled() || bitmap == null) {
                Log.d(TAG, "Decode thumbnail failed.");
                mIsSupportSetAs = false;
                showToast();
                getPath().recycleObject();
                return null;
            }
            //SPRD:bug 630027,decoder bitmap success, set mIsSupportSetAs true
            mIsSupportSetAs = true;

            if (mType == MediaItem.TYPE_MICROTHUMBNAIL) {
                bitmap = BitmapUtils.resizeAndCropCenter(bitmap, targetSize, true);
            } else {
                bitmap = BitmapUtils.resizeDownBySideLength(bitmap, targetSize, true);
            }
            return bitmap;
        }
    }

    @Override
    public int getSupportedOperations() {
        /* SPRD: bug613322 UriImage support DRM image @{ */
        int supported = 0;
        boolean drmFile = UriImageDrmUtils.getInstance().isDrmFile(mUri, mContentType);
        if (!isGmsVersion) {
            if (drmFile) {
                supported = MediaObject.SUPPORT_DRM_RIGHTS_INFO;
            } else {
                // SPRD: Add for bug620619, do not show "set as" menu if decode failed
                if (mIsSupportSetAs) {
                    supported = SUPPORT_PRINT | SUPPORT_SETAS;
                    if (isSharable()) {
                        supported |= SUPPORT_SHARE;
                    }
                }
            }
            /* @} */
            if (BitmapUtils.isSupportedByRegionDecoder(mContentType)) {
                supported |= SUPPORT_EDIT | SUPPORT_FULL_IMAGE;
            }
        }
        return supported;
    }

    @Override
    public void getPanoramaSupport(PanoramaSupportCallback callback) {
        mPanoramaMetadata.getPanoramaSupport(mApplication, callback);
    }

    @Override
    public void clearCachedPanoramaSupport() {
        mPanoramaMetadata.clearCachedValues();
    }

    private boolean isSharable() {
        // We cannot grant read permission to the receiver since we put
        // the data URI in EXTRA_STREAM instead of the data part of an intent
        // And there are issues in MediaUploader and Bluetooth file sender to
        // share a general image data. So, we only share for local file.
        return ContentResolver.SCHEME_FILE.equals(mUri.getScheme());
    }

    @Override
    public int getMediaType() {
        mMediaType = GalleryUtils.getMediaType(mContentType, mFileFlag);
        return mMediaType;
    }

    @Override
    public Uri getContentUri() {
        return mUri;
    }

    @Override
    public MediaDetails getDetails() {
        MediaDetails details = super.getDetails();
        if (mWidth != 0 && mHeight != 0) {
            details.addDetail(MediaDetails.INDEX_WIDTH, mWidth);
            details.addDetail(MediaDetails.INDEX_HEIGHT, mHeight);
        }
        if (mContentType != null) {
            details.addDetail(MediaDetails.INDEX_MIMETYPE, mContentType);
        }
        if (ContentResolver.SCHEME_FILE.equals(mUri.getScheme())) {
            String filePath = mUri.getPath();
            details.addDetail(MediaDetails.INDEX_PATH, filePath);
            MediaDetails.extractExifInfo(details, mUri);
        }
        /* SPRD: bug613322 UriImage support DRM image @{ */
        details = UriImageDrmUtils.getInstance().getUriDetailsByAction(this, details, DrmStore.Action.DISPLAY);
        /* @} */
        return details;
    }

    @Override
    public String getMimeType() {
        return mContentType;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (mFileDescriptor != null) {
                Utils.closeSilently(mFileDescriptor);
            }
        } finally {
            super.finalize();
        }
    }

    @Override
    public int getWidth() {
        return 0;
    }

    @Override
    public int getHeight() {
        return 0;
    }

    @Override
    public int getRotation() {
        return mRotation;
    }

    /* SPRD: Add for bug620619. We want to remove some menus if decode failed, so show toast to improve user experience @{ */
    private void showToast() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                Toast.makeText(mApplication.getAndroidContext(), R.string.fail_to_load_image, Toast.LENGTH_SHORT).show();
                Looper.loop();
            }
        }).start();
    }
    /* @} */

    @Override
    public String getFilePath() {
        if (isGmsVersion && filePath != null) {
            return filePath;
        } else {
            if (filePath != null) {
                return filePath;
            } else {
                return mUri.getPath();
            }
        }
    }

    // SPRD: bug 641354, Override the getName(),return fileName
    @Override
    public String getName() {
        return mFileName;
    }

    // SPRD: bug 641354, add for get fileName by path
    private String getDisplayName() {
        String fileName = "";
        String path = mUri.getPath();
        if (!TextUtils.isEmpty(path)) {
            int index = path.lastIndexOf("/");
            if (index != -1) {
                fileName = path.substring(index + 1);
            }
        }
        return fileName;
    }

    @Override
    public Uri getPlayUri() {
        return getContentUri();
    }

    @Override
    public String getDate() {
        return "";
    }

    private class Task extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            queryFileFlag();
            queryJpegSize();
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            UriImageDrmUtils.getInstance().loadUriDrmInfo(UriImage.this);
            if (mOnUriImageListener != null) {
                mOnUriImageListener.onUriImageInitialized();
                mOnUriImageListener = null;
            }
        }
    }

    private Uri transformUri(Uri uri) {
        if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
            final String docId = DocumentsContract.getDocumentId(uri);
            final String[] split = docId.split(":");
            uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath(split[1]).build();
            Log.d(TAG, "transformUri 1 uri = " + uri);
        } else if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
            final String docId = DocumentsContract.getDocumentId(uri);
            String[] split = docId.split(":");
            if (split.length >= 2) {
                String type = split[0];
                String filePath;
                String sdPath = "/storage/" + type; //include sd or otg
                if ("primary".equalsIgnoreCase(type)) {
                    filePath = Environment.getExternalStorageDirectory() + "/" + split[1];
                } else {
                    filePath = sdPath + "/" + split[1];
                }
                uri = Uri.fromFile(new File(filePath));
            }
            Log.d(TAG, "transformUri 2 uri = " + uri);
        } else if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {
            final String docId = DocumentsContract.getDocumentId(uri);
            Uri contentUri;
            try {
                if (Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.Q) {
                    if (docId.contains(":")) {
                        final String[] split = docId.split(":");
                        contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath(split[1]).build();
                    } else {
                        contentUri = ContentUris.withAppendedId(
                                Uri.parse("content://downloads/all_downloads"), Long.valueOf(docId));
                    }
                } else {
                    contentUri = ContentUris.withAppendedId(
                            Uri.parse("content://downloads/all_downloads"), Long.valueOf(docId));
                }
            } catch (Exception e) {
                e.printStackTrace();
                return uri;
            }
            String filePath = GalleryUtils.getDataColumn(mApplication.getAndroidContext(),
                    contentUri, null, null);
            if (filePath != null) {
                uri = Uri.fromFile(new File(filePath));
            }
            Log.d(TAG, "transformUri 3 uri = " + uri);
        } else {
            uri = fromFileProvider(uri);
            Log.d(TAG, "transformUri 4 uri = " + uri);
        }
        return uri;
    }

    private Uri fromFileProvider(Uri uri) {
        Class<FileProvider> providerClass = FileProvider.class;
        try {
            Method method = providerClass.getDeclaredMethod("getPathStrategy", Context.class, String.class);
            method.setAccessible(true);
            Object obj = null;
            try {
                obj = method.invoke(null, mApplication.getAndroidContext(), uri.getAuthority());
            } catch (IllegalArgumentException | InvocationTargetException e) {
                Log.e(TAG, "fromFileProvider error, " + e.toString());
            }
            if (obj != null) {
                Class<?> pathStrategyClass = Class.forName(FileProvider.class.getName() + "$PathStrategy");
                Method getFileMethod = pathStrategyClass.getDeclaredMethod("getFileForUri", Uri.class);
                getFileMethod.setAccessible(true);
                Object path = getFileMethod.invoke(obj, uri);
                if (path instanceof File) {
                    uri = Uri.fromFile((File) path);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "fromFileProvider error, ", e);
        }
        Log.d(TAG, "fromFileProvider returned uri is " + uri);
        return uri;
    }
}
