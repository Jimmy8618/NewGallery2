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
import android.content.ContentValues;
import android.database.Cursor;
import android.drm.DrmStore;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.MediaColumns;
import android.text.TextUtils;
import android.util.Log;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.app.GalleryStorageUtil;
import com.android.gallery3d.app.PanoramaMetadataSupport;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.BitmapUtils;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.exif.ExifInterface;
import com.android.gallery3d.exif.ExifTag;
import com.android.gallery3d.filtershow.tools.SaveImage;
import com.android.gallery3d.msg.MimeTypeMsg;
import com.android.gallery3d.util.DateUtils;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;
import com.android.gallery3d.util.UpdateHelper;
import com.android.gallery3d.v2.discover.db.DiscoverStore;
import com.android.gallery3d.v2.util.SdCardPermission;
import com.sprd.drmgalleryplugin.util.DrmUtil;
import com.sprd.frameworks.StandardFrameworks;
import com.sprd.gallery3d.drm.LocalMediaItemUtils;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

// LocalImage represents an image in the local storage.
public class LocalImage extends LocalMediaItem {
    private static final String TAG = "LocalImage";

    public static final Path ITEM_PATH = Path.fromString("/local/image/item");

    // file_flag in media databases
    public static final int IMG_TYPE_MODE_NORMAL = 0;
    public static final int IMG_TYPE_MODE_3D_CAPTURE = 7;
    public static final int IMG_TYPE_MODE_SOFY_OPTICAL_ZOOM = 11;
    public static final int IMG_TYPE_MODE_BLUR = 12;//0X000C blur has bokeh
    public static final int IMG_TYPE_MODE_BLUR_GALLERY = 268;//0X010C blur not bokeh (no use)
    public static final int IMG_TYPE_MODE_BOKEH = 16;//0X0010 real-bokeh has bokeh
    public static final int IMG_TYPE_MODE_BOKEH_HDR = 17;//0X0011 real-bokeh with hdr has bokeh
    public static final int IMG_TYPE_MODE_BOKEH_GALLERY = 272;//0X0110 real-bokeh not bokeh
    public static final int IMG_TYPE_MODE_BOKEH_HDR_GALLERY = 273;//0X0111 real-bokeh with hdr not bokeh
    public static final int IMG_TYPE_MODE_AI_SCENE = 36;
    public static final int IMG_TYPE_MODE_AI_SCENE_HDR = 37;
    public static final int IMG_TYPE_MODE_BURST = 51;
    public static final int IMG_TYPE_MODE_HDR = 52;
    public static final int IMG_TYPE_MODE_AUDIO_CAPTURE = 53;
    public static final int IMG_TYPE_MODE_HDR_AUDIO_CAPTURE = 54;
    public static final int IMG_TYPE_MODE_BURST_COVER = 55;
    public static final int IMG_TYPE_MODE_THUMBNAIL = 56;
    public static final int IMG_TYPE_MODE_MOTION_PHOTO = 1024; //motion photo flag
    public static final int IMG_TYPE_MODE_MOTION_HDR_PHOTO = 1025; //motion hdr flag
    public static final int IMG_TYPE_MODE_MOTION_AI_PHOTO = 1026; //motion AI flag
    public static final int IMG_TYPE_MODE_MOTION_HDR_AI_PHOTO = 1027; //motion hdr AI flag

    /*FDR pic type */
    public static final int IMG_TYPE_MODE_BOKEH_FDR = 18;
    public static final int IMG_TYPE_MODE_BOKEH_FDR_GALLERY = 274;
    public static final int IMG_TYPE_MODE_AI_SCENE_FDR = 38;
    public static final int IMG_TYPE_MODE_FDR = 57;
    public static final int IMG_TYPE_MODE_FDR_AUDIO_CAPTURE = 58;
    public static final int IMG_TYPE_MODE_MOTION_FDR_PHOTO = 1028;
    public static final int IMG_TYPE_MODE_MOTION_FDR_AI_PHOTO = 1029;

    // Must preserve order between these indices and the order of the terms in
    // the following PROJECTION array.
    private static final int INDEX_ID = 0;
    private static final int INDEX_CAPTION = 1;
    private static final int INDEX_MIME_TYPE = 2;
    private static final int INDEX_LATITUDE = 3;
    private static final int INDEX_LONGITUDE = 4;
    private static final int INDEX_DATE_TAKEN = 5;
    private static final int INDEX_DATE_ADDED = 6;
    private static final int INDEX_DATE_MODIFIED = 7;
    private static final int INDEX_DATA = 8;
    private static final int INDEX_ORIENTATION = 9;
    private static final int INDEX_BUCKET_ID = 10;
    private static final int INDEX_SIZE = 11;
    private static final int INDEX_WIDTH = 12;
    private static final int INDEX_HEIGHT = 13;
    private static final int INDEX_DISPLAY_NAME = 14;

    private int mBurstCount;

    private static boolean isLowRam = StandardFrameworks.getInstances().isLowRam();
    private static boolean isGmsVersion = GalleryUtils.isSprdPhotoEdit();

    private static final int MAX_BMP_CROP_PIXEL = 9000 * 9000;

    private static final ArrayList<String> PROJECTION_ARRAY = new ArrayList<>();
    public static String[] PROJECTION;

    static {
        PROJECTION_ARRAY.add(ImageColumns._ID);            //0
        PROJECTION_ARRAY.add(ImageColumns.TITLE);          //1
        PROJECTION_ARRAY.add(ImageColumns.MIME_TYPE);      //2
        PROJECTION_ARRAY.add(ImageColumns.LATITUDE);       //3
        PROJECTION_ARRAY.add(ImageColumns.LONGITUDE);      //4
        PROJECTION_ARRAY.add(ImageColumns.DATE_TAKEN);     //5
        PROJECTION_ARRAY.add(ImageColumns.DATE_ADDED);     //6
        PROJECTION_ARRAY.add(ImageColumns.DATE_MODIFIED);  //7
        PROJECTION_ARRAY.add(ImageColumns.DATA);           //8
        PROJECTION_ARRAY.add(ImageColumns.ORIENTATION);    //9
        PROJECTION_ARRAY.add(ImageColumns.BUCKET_ID);      //10
        PROJECTION_ARRAY.add(ImageColumns.SIZE);           //11
        PROJECTION_ARRAY.add(MediaColumns.WIDTH);          //12
        PROJECTION_ARRAY.add(MediaColumns.HEIGHT);         //13
        PROJECTION_ARRAY.add(ImageColumns.DISPLAY_NAME);   //14
        if (StandardFrameworks.getInstances().isSupportIsDrm()) {
            PROJECTION_ARRAY.add(COLUMN_IS_DRM);           //15
        }
        if (StandardFrameworks.getInstances().isSupportFileFlag()) {
            PROJECTION_ARRAY.add(COLUMN_FILE_FLAG);        //16
        }

        PROJECTION = PROJECTION_ARRAY.toArray(new String[0]);
    }

    protected final GalleryApp mApplication;

    public int rotation;

    private PanoramaMetadataSupport mPanoramaMetadata = new PanoramaMetadataSupport(this);

    public LocalImage(Path path, GalleryApp application, Cursor cursor) {
        super(path, nextVersionNumber());
        mApplication = application;
        loadFromCursor(cursor);
        /* SPRD: Drm feature start ,bug 598636,DRM optimization @{ */
        if (displayName != null && displayName.endsWith(".dcf")) {
            LocalMediaItemUtils.getInstance().loadDrmInfor(this);
        }
        /* SPRD: Drm feature end @} */
    }

    public LocalImage(Path path, GalleryApp application, int id) {
        super(path, nextVersionNumber());
        mApplication = application;
        ContentResolver resolver = mApplication.getContentResolver();
        Uri uri = Images.Media.EXTERNAL_CONTENT_URI;
        Cursor cursor = null;
        try {
            cursor = LocalAlbum.getItemCursor(resolver, uri, PROJECTION, id);
        } catch (SecurityException e) {
            Log.d(TAG, "SecurityException " + e.toString());
        }
        if (cursor == null) {
            // SPRD: Modify bug496296, shouldn't add cache when no value in cursor
            path.recycleObject();
            throw new RuntimeException("cannot get cursor for: " + path);
        }
        try {
            if (cursor.moveToNext()) {
                loadFromCursor(cursor);
            } else {
                // SPRD: Modify bug496296, shouldn't add cache when no value in cursor
                path.recycleObject();
                throw new RuntimeException("cannot find data for: " + path);
            }
        } finally {
            cursor.close();
        }
        /* SPRD: Drm feature start ,bug 598636,DRM optimization @{ */
        if (displayName != null && displayName.endsWith(".dcf")) {
            LocalMediaItemUtils.getInstance().loadDrmInfor(this);
        }
        /* SPRD: Drm feature end @} */
    }

    private void loadFromCursor(Cursor cursor) {
        id = cursor.getInt(INDEX_ID);
        caption = cursor.getString(INDEX_CAPTION);
        mimeType = cursor.getString(INDEX_MIME_TYPE);
        //after Q, not support lat, long from database
        if (Build.VERSION.SDK_INT < ApiHelper.VERSION_CODES.Q) {
            latitude = cursor.getDouble(INDEX_LATITUDE);
            longitude = cursor.getDouble(INDEX_LONGITUDE);
        }
        dateTakenInMs = cursor.getLong(INDEX_DATE_TAKEN);
        dateAddedInSec = cursor.getLong(INDEX_DATE_ADDED);
        dateModifiedInSec = cursor.getLong(INDEX_DATE_MODIFIED);
        filePath = cursor.getString(INDEX_DATA);
        rotation = cursor.getInt(INDEX_ORIENTATION);
        bucketId = cursor.getInt(INDEX_BUCKET_ID);
        fileSize = cursor.getLong(INDEX_SIZE);
        width = cursor.getInt(INDEX_WIDTH);
        height = cursor.getInt(INDEX_HEIGHT);
        displayName = cursor.getString(INDEX_DISPLAY_NAME);
        if (StandardFrameworks.getInstances().isSupportFileFlag()) {
            mFileFlag = cursor.getInt(cursor.getColumnIndex(COLUMN_FILE_FLAG));
        }
        //@{ 根据文件名判断是否是 Motion Photo
        if (displayName != null && GalleryUtils.isMotionPhoto(displayName, mFileFlag)) {
            // UNISOC added for bug 1205145, Make sure motion photo flag is correct.
            switch (mFileFlag) {
                case IMG_TYPE_MODE_HDR:
                    mFileFlag = IMG_TYPE_MODE_MOTION_HDR_PHOTO;
                    break;
                case IMG_TYPE_MODE_AI_SCENE:
                    mFileFlag = IMG_TYPE_MODE_MOTION_AI_PHOTO;
                    break;
                case IMG_TYPE_MODE_AI_SCENE_HDR:
                    mFileFlag = IMG_TYPE_MODE_MOTION_HDR_AI_PHOTO;
                    break;
                case IMG_TYPE_MODE_FDR:
                    mFileFlag = IMG_TYPE_MODE_MOTION_FDR_PHOTO;
                    break;
                case IMG_TYPE_MODE_AI_SCENE_FDR:
                    mFileFlag = IMG_TYPE_MODE_MOTION_FDR_AI_PHOTO;
                    break;
                case IMG_TYPE_MODE_MOTION_HDR_PHOTO:
                case IMG_TYPE_MODE_MOTION_FDR_PHOTO:
                case IMG_TYPE_MODE_MOTION_AI_PHOTO:
                case IMG_TYPE_MODE_MOTION_HDR_AI_PHOTO:
                case IMG_TYPE_MODE_MOTION_FDR_AI_PHOTO:
                    break;
                default:
                    mFileFlag = IMG_TYPE_MODE_MOTION_PHOTO;
            }
        }
        //@}
        if (StandardFrameworks.getInstances().isSupportIsDrm()) {
            isDrm = cursor.getInt(cursor.getColumnIndex(COLUMN_IS_DRM));
        }
        //Log.i(TAG, " loadFromCursor flag = " + mFileFlag);
        queryJpegSize();
        /* Add for bug535110 new feature,  support play audio picture @{ */
        /* @} */
        if (getMediaType() == MEDIA_TYPE_IMAGE_BURST_COVER) {
            mBurstCount = queryBurstImageCount(mApplication.getContentResolver(), bucketId, dateTakenInMs);
        }
    }

    @Override
    protected boolean updateFromCursor(Cursor cursor) {
        UpdateHelper uh = new UpdateHelper();
        id = uh.update(id, cursor.getInt(INDEX_ID));
        caption = uh.update(caption, cursor.getString(INDEX_CAPTION));
        String oriMimeType = mimeType;
        mimeType = uh.update(mimeType, cursor.getString(INDEX_MIME_TYPE));
        displayName = uh.update(displayName, cursor.getString(INDEX_DISPLAY_NAME));
        int oriFileFlag = mFileFlag;
        if (StandardFrameworks.getInstances().isSupportFileFlag()) {
            mFileFlag = uh.update(mFileFlag, cursor.getInt(cursor.getColumnIndex(COLUMN_FILE_FLAG)));
        }
        //@{ 根据文件名判断是否是 Motion Photo
        if (displayName != null && GalleryUtils.isMotionPhoto(displayName, mFileFlag)) {
            switch (mFileFlag) {
                case IMG_TYPE_MODE_HDR:
                    mFileFlag = IMG_TYPE_MODE_MOTION_HDR_PHOTO;
                    break;
                case IMG_TYPE_MODE_AI_SCENE:
                    mFileFlag = IMG_TYPE_MODE_MOTION_AI_PHOTO;
                    break;
                case IMG_TYPE_MODE_AI_SCENE_HDR:
                    mFileFlag = IMG_TYPE_MODE_MOTION_HDR_AI_PHOTO;
                    break;
                case IMG_TYPE_MODE_FDR:
                    mFileFlag = IMG_TYPE_MODE_MOTION_FDR_PHOTO;
                    break;
                case IMG_TYPE_MODE_AI_SCENE_FDR:
                    mFileFlag = IMG_TYPE_MODE_MOTION_FDR_AI_PHOTO;
                    break;
                case IMG_TYPE_MODE_MOTION_HDR_PHOTO:
                case IMG_TYPE_MODE_MOTION_FDR_PHOTO:
                case IMG_TYPE_MODE_MOTION_AI_PHOTO:
                case IMG_TYPE_MODE_MOTION_HDR_AI_PHOTO:
                case IMG_TYPE_MODE_MOTION_FDR_AI_PHOTO:
                    break;
                default:
                    mFileFlag = IMG_TYPE_MODE_MOTION_PHOTO;
            }
        }
        //@}
        if ((oriMimeType != null && !oriMimeType.equals(mimeType))
                || (mimeType != null && !mimeType.equals(oriMimeType))
                || (oriFileFlag != mFileFlag)) {
            onMimeTypeChanged(oriMimeType, mimeType);
        }

        //after Q, not support lat, long from database
        if (Build.VERSION.SDK_INT < ApiHelper.VERSION_CODES.Q) {
            latitude = uh.update(latitude, cursor.getDouble(INDEX_LATITUDE));
            longitude = uh.update(longitude, cursor.getDouble(INDEX_LONGITUDE));
        }
        dateTakenInMs = uh.update(
                dateTakenInMs, cursor.getLong(INDEX_DATE_TAKEN));
        dateAddedInSec = uh.update(
                dateAddedInSec, cursor.getLong(INDEX_DATE_ADDED));
        dateModifiedInSec = uh.update(
                dateModifiedInSec, cursor.getLong(INDEX_DATE_MODIFIED));
        filePath = uh.update(filePath, cursor.getString(INDEX_DATA));
        rotation = uh.update(rotation, cursor.getInt(INDEX_ORIENTATION));
        bucketId = uh.update(bucketId, cursor.getInt(INDEX_BUCKET_ID));
        fileSize = uh.update(fileSize, cursor.getLong(INDEX_SIZE));
        width = uh.update(width, cursor.getInt(INDEX_WIDTH));
        height = uh.update(height, cursor.getInt(INDEX_HEIGHT));
        if (StandardFrameworks.getInstances().isSupportIsDrm()) {
            isDrm = uh.update(isDrm, cursor.getInt(cursor.getColumnIndex(COLUMN_IS_DRM)));
        }
        if (getMediaType() == MEDIA_TYPE_IMAGE_BURST_COVER) {
            mBurstCount = uh.update(mBurstCount, queryBurstImageCount(mApplication.getContentResolver(), bucketId, dateTakenInMs));
        }
        return uh.isUpdated();
    }

    @Override
    public Job<Bitmap> requestImage(int type) {
        //Log.d(TAG, "requestImage " + filePath);
        if (Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.Q) {
            return new LocalImageRequest(mApplication, mPath, dateModifiedInSec,
                    type, getContentUri(), filePath);
        } else {
            return new LocalImageRequest(mApplication, mPath, dateModifiedInSec,
                    type, filePath);
        }
    }

    public static class LocalImageRequest extends ImageCacheRequest {
        /* SPRD: Drm feature start @{
        private String mLocalFilePath;
        */
        public String mLocalFilePath;
        /* SPRD: Drm feature end @} */
        public Uri mUri;
        private String mFilePath;

        public LocalImageRequest(GalleryApp application, Path path, long timeModified,
                                 int type, String localFilePath) {
            super(application, path, timeModified, type,
                    MediaItem.getTargetSize(type));
            mLocalFilePath = localFilePath;
        }

        public LocalImageRequest(GalleryApp application, Path path, long timeModified,
                                 int type, Uri uri, String filePath) {
            super(application, path, timeModified, type,
                    MediaItem.getTargetSize(type));
            mUri = uri;
            mFilePath = filePath;
        }

        @Override
        public Bitmap onDecodeOriginal(JobContext jc, final int type) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            if (isLowRam) {
                options.inPreferredConfig = Bitmap.Config.ARGB_4444;
            } else {
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            }
            int targetSize = MediaItem.getTargetSize(type);

            // try to decode from JPEG EXIF
            if (type == MediaItem.TYPE_MICROTHUMBNAIL) {
                ExifInterface exif = new ExifInterface();
                byte[] thumbData = null;
                InputStream is = null;
                try {
                    if (mUri != null) {
                        is = mApplication.getContentResolver().openInputStream(mUri);
                        exif.readExif(is);
                    } else {
                        exif.readExif(mLocalFilePath);
                    }
                    thumbData = exif.getThumbnail();
                } catch (FileNotFoundException e) {
                    Log.w(TAG, "failed to find file to read thumbnail: " + mLocalFilePath + ", mUri = " + mUri);
                } catch (IOException e) {
                    Log.w(TAG, "failed to get thumbnail from: " + mLocalFilePath + ", mUri = " + mUri);
                } finally {
                    Utils.closeSilently(is);
                }
                if (thumbData != null) {
                    Bitmap bitmap = DecodeUtils.decodeIfBigEnough(
                            jc, thumbData, options, targetSize);
                    if (bitmap != null) {
                        return bitmap;
                    }
                }
            }

            /* SPRD: Drm feature start @{
            return DecodeUtils.decodeThumbnail(jc, mLocalFilePath, options, targetSize, type);
            */
            if (mUri != null) {
                if (DrmUtil.isDrmFile(mUri, null) && DrmUtil.isDrmValid(mUri)) {
                    return LocalMediaItemUtils.getInstance().decodeThumbnailWithDrm(jc, type, mFilePath, options, targetSize);
                } else {
                    return LocalMediaItemUtils.getInstance().decodeThumbnailWithDrm(jc, type, mUri, options, targetSize);
                }
            } else {
                return LocalMediaItemUtils.getInstance().decodeThumbnailWithDrm(jc, type, mLocalFilePath, options, targetSize);
            }
            /* SPRD: Drm feature end @} */
        }
    }

    @Override
    public Job<BitmapRegionDecoder> requestLargeImage() {
        if (Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.Q) {
            return new LocalLargeImageRequest(getContentUri());
        } else {
            return new LocalLargeImageRequest(filePath);
        }
    }

    public static class LocalLargeImageRequest
            implements Job<BitmapRegionDecoder> {
        String mLocalFilePath;
        Uri mUri;

        public LocalLargeImageRequest(String localFilePath) {
            mLocalFilePath = localFilePath;
        }

        public LocalLargeImageRequest(Uri uri) {
            mUri = uri;
        }

        @Override
        public BitmapRegionDecoder run(JobContext jc) {
            if (mUri != null) {
                InputStream is = null;
                try {
                    is = GalleryAppImpl.getApplication().getContentResolver().openInputStream(mUri);
                    return BitmapRegionDecoder.newInstance(is, false);
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                } finally {
                    Utils.closeSilently(is);
                }
            } else {
                return DecodeUtils.createBitmapRegionDecoder(jc, mLocalFilePath, false);
            }
        }
    }

    @Override
    public int getSupportedOperations() {
        if (isGmsVersion) {
            return 0;
        }
        int operation = SUPPORT_DELETE | SUPPORT_SHARE
                | SUPPORT_SETAS | SUPPORT_PRINT | SUPPORT_INFO;

        if (isSupportCrop()) {
            operation |= SUPPORT_CROP;
        }

        if (BitmapUtils.isSupportedByRegionDecoder(mimeType)) {
            operation |= SUPPORT_FULL_IMAGE | SUPPORT_EDIT;
        }

        if (BitmapUtils.isRotationSupported(mimeType) && isSupportRotateByMediaType(getMediaType())) {
            operation |= SUPPORT_ROTATE;
        }

        if (GalleryUtils.isValidLocation(latitude, longitude)) {
            operation |= SUPPORT_SHOW_ON_MAP;
        }

        /* SPRD : Drm feature start @{ */
        operation = LocalMediaItemUtils.getInstance().getImageSupportedOperations(this, operation);
        /* SPRD : Drm feature end  @} */
        return operation;
    }

    @Override
    public void getPanoramaSupport(PanoramaSupportCallback callback) {
        mPanoramaMetadata.getPanoramaSupport(mApplication, callback);
    }

    @Override
    public void clearCachedPanoramaSupport() {
        mPanoramaMetadata.clearCachedValues();
    }

    @Override
    public void delete() {
        Log.d(TAG, "delete " + filePath);
        GalleryUtils.assertNotInRenderThread();
        if (GalleryStorageUtil.isInInternalStorage(filePath) || Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.Q) {
            Uri baseUri = Images.Media.EXTERNAL_CONTENT_URI;
            ContentResolver contentResolver = mApplication.getContentResolver();
            if (getMediaType() != MediaObject.MEDIA_TYPE_IMAGE_BURST_COVER) {
                SaveImage.deleteAuxFiles(contentResolver, getContentUri());
                contentResolver.delete(baseUri, "_id=?",
                        new String[]{String.valueOf(id)});
            } else {
                long dateTaken = getDateInMs();
                Log.d(TAG, "delete burst image : " +
                        String.format("(file_flag=%d or file_flag=%d) and datetaken=%d", IMG_TYPE_MODE_BURST_COVER, IMG_TYPE_MODE_BURST, dateTaken));
                contentResolver.delete(baseUri, "(file_flag=?" + " or file_flag=?) and " + ImageColumns.DATE_TAKEN + "=?",
                        new String[]{String.valueOf(IMG_TYPE_MODE_BURST_COVER), String.valueOf(IMG_TYPE_MODE_BURST), String.valueOf(dateTaken)});
            }
        } else {
            Uri baseUri = Images.Media.EXTERNAL_CONTENT_URI;
            ContentResolver contentResolver = mApplication.getContentResolver();
            if (getMediaType() != MediaObject.MEDIA_TYPE_IMAGE_BURST_COVER) {
                //SaveImage.deleteAuxFiles(contentResolver, getContentUri());
                SdCardPermission.deleteFile(filePath);
            } else {
                long dateTaken = getDateInMs();
                Log.d(TAG, "delete burst image : " +
                        String.format("(file_flag=%d or file_flag=%d) and datetaken=%d", IMG_TYPE_MODE_BURST_COVER, IMG_TYPE_MODE_BURST, dateTaken));
                String[] projection = new String[]{
                        MediaStore.Files.FileColumns.DATA,
                };
                String selection = "(file_flag=?" + " or file_flag=?) and " + ImageColumns.DATE_TAKEN + "=?";
                String[] args = new String[]{String.valueOf(IMG_TYPE_MODE_BURST_COVER), String.valueOf(IMG_TYPE_MODE_BURST), String.valueOf(dateTaken)};
                Cursor cursor = null;
                try {
                    cursor = contentResolver.query(baseUri, projection, selection, args, null);
                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            String path = cursor.getString(0);
                            SdCardPermission.deleteFile(path);
                        }
                    }
                } catch (Exception e) {
                    Log.d(TAG, "delete fail : " + e);
                } finally {
                    Utils.closeSilently(cursor);
                }
            }
        }
    }

    @Override
    public void rotate(int degrees) {
        Log.d(TAG, "rotate, degrees = " + degrees);
        GalleryUtils.assertNotInRenderThread();
        Uri baseUri = Images.Media.EXTERNAL_CONTENT_URI;
        ContentValues values = new ContentValues();
        int rotation = (this.rotation + degrees) % 360;
        if (rotation < 0) {
            rotation += 360;
        }
        long lastModifiedSeconds = 0;

        if (mimeType.equalsIgnoreCase("image/jpeg")) {
            ExifInterface exifInterface = new ExifInterface();
            ExifTag tag = exifInterface.buildTag(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.getOrientationValueForRotation(rotation));
            if (tag != null) {
                exifInterface.setTag(tag);
                ParcelFileDescriptor fd1 = null;
                ParcelFileDescriptor fd2 = null;
                try {
                    if (!GalleryStorageUtil.isInInternalStorage(filePath)) {
                        fd1 = SdCardPermission.createExternalFileDescriptor(filePath, "rw");
                    }
                    if (Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.Q) {
                        exifInterface.reWriteExif(mApplication.getContentResolver(), getContentUri());
                    } else {
                        exifInterface.forceRewriteExif(filePath, fd1);
                    }
                    File file = new File(filePath);
                    fileSize = file.length();
                    lastModifiedSeconds = file.lastModified() / 1000;
                    values.put(Images.Media.SIZE, fileSize);
                    values.put(Images.Media.DATE_MODIFIED, lastModifiedSeconds);
                } catch (FileNotFoundException e) {
                    Log.w(TAG, "cannot find file to set exif: " + filePath, e);
                } catch (IOException e) {
                    Log.w(TAG, "cannot set exif data: " + filePath, e);
                    if (!GalleryStorageUtil.isInInternalStorage(filePath)) {
                        fd2 = SdCardPermission.createExternalFileDescriptor(filePath, "rw");
                    }
                    setExifRotation(filePath, fd2, rotation);

                    File file = new File(filePath);
                    fileSize = file.length();
                    lastModifiedSeconds = file.lastModified() / 1000;
                    values.put(Images.Media.SIZE, fileSize);
                    values.put(Images.Media.DATE_MODIFIED, lastModifiedSeconds);
                } finally {
                    Utils.closeSilently(fd1);
                    Utils.closeSilently(fd2);
                }
            } else {
                Log.w(TAG, "Could not build tag: " + ExifInterface.TAG_ORIENTATION);
            }
        }

        this.rotation = rotation;
        values.put(Images.Media.ORIENTATION, rotation);
        mApplication.getContentResolver().update(baseUri, values, "_id=?",
                new String[]{String.valueOf(id)});
    }

    /*SPRD:add for bug598541 Cut the image rotation is set to wallpaper, wallpaper is still displayed as the rotation before the picture@{*/
    private void setExifRotation(String filePath, ParcelFileDescriptor fd, int rotation) {
        android.media.ExifInterface exifInterface;
        try {
            if (fd != null) {
                exifInterface = new android.media.ExifInterface(fd.getFileDescriptor());
            } else {
                exifInterface = new android.media.ExifInterface(filePath);
            }
            switch (rotation) {
                case 0:
                    rotation = android.media.ExifInterface.ORIENTATION_NORMAL;
                    break;
                case 90:
                    rotation = android.media.ExifInterface.ORIENTATION_ROTATE_90;
                    break;
                case 180:
                    rotation = android.media.ExifInterface.ORIENTATION_ROTATE_180;
                    break;
                case 270:
                    rotation = android.media.ExifInterface.ORIENTATION_ROTATE_270;
                    break;
                default:
                    rotation = android.media.ExifInterface.ORIENTATION_NORMAL;
                    break;
            }
            exifInterface.setAttribute(android.media.ExifInterface.TAG_ORIENTATION,
                    rotation + "");
            exifInterface.saveAttributes();
        } catch (IOException e) {
            Log.d(TAG, "exception when setExifRotation", e);
        }
    }

    /*@} */
    @Override
    public Uri getContentUri() {
        Uri baseUri = Images.Media.EXTERNAL_CONTENT_URI;
        return baseUri.buildUpon().appendPath(String.valueOf(id)).build();
    }

    @Override
    public int getMediaType() {
        return GalleryUtils.getMediaType(mimeType, mFileFlag);
    }

    @Override
    public MediaDetails getDetails() {
        MediaDetails details = super.getDetails();
        details.addDetail(MediaDetails.INDEX_ORIENTATION, Integer.valueOf(rotation));
        if (MIME_TYPE_JPEG.equals(mimeType)) {
            // ExifInterface returns incorrect values for photos in other format.
            // For example, the width and height of an webp images is always '0'.
            MediaDetails.extractExifInfo(details, getContentUri());
        }

        /* SPRD: Drm feature start @{ */
        details = LocalMediaItemUtils.getInstance().getDetailsByAction(this, details, DrmStore.Action.DISPLAY, mApplication.getAndroidContext());
        /* @} SPRD: Drm feature end */
        return details;
    }

    @Override
    public int getRotation() {
        return rotation;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public String getFilePath() {
        return filePath;
    }

    public interface BurstCountUpdateListener {
        void onBurstCountUpdate(int burstCount);
    }

    private BurstCountUpdateListener mBurstCountUpdateListener;

    public void setBurstCountUpdateListener(BurstCountUpdateListener listener) {
        mBurstCountUpdateListener = listener;
    }

    private int queryBurstImageCount(ContentResolver resolver, long bucketId, long dateTaken) {
        Cursor cursor = null;
        int burstCount = 0;
        try {
            Log.d(TAG, "queryBurstImageCount bucketId = " + bucketId + ", dateTaken = " + dateTaken);
            cursor = resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new String[]{"_id"},
                    MediaStore.Images.ImageColumns.BUCKET_ID + " = ?" + ") AND ((file_flag = ? OR file_flag = ?) AND datetaken = ?",
                    new String[]{String.valueOf(bucketId), String.valueOf(IMG_TYPE_MODE_BURST), String.valueOf
                            (IMG_TYPE_MODE_BURST_COVER), String.valueOf(dateTaken)}, null);
            if (cursor != null) {
                //cursor.moveToNext();
                burstCount = cursor.getCount();
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        Log.d(TAG, "queryBurstImageCount burstCount= " + burstCount);

        if (mBurstCount != burstCount && mBurstCountUpdateListener != null) {
            mBurstCountUpdateListener.onBurstCountUpdate(burstCount);
        }

        return burstCount;
    }

    @Override
    public int getBurstCount() {
        return mBurstCount;
    }

    @Override
    public Uri getPlayUri() {
        return getContentUri();
    }

    private void onMimeTypeChanged(String oriMimeType, String curMimeType) {
        EventBus.getDefault().post(new MimeTypeMsg(oriMimeType, curMimeType));
    }

    @Override
    public void bokehDonechangeFileFlag(int flag) {
        mFileFlag = flag;
    }

    private boolean isSupportRotateByMediaType(int mediaType) {
        return (mediaType == MEDIA_TYPE_IMAGE ||
                mediaType == MEDIA_TYPE_IMAGE_HDR ||
                mediaType == MEDIA_TYPE_IMAGE_FDR ||
                mediaType == MEDIA_TYPE_IMAGE_PHOTO_VOICE ||
                mediaType == MEDIA_TYPE_IMAGE_VHDR ||
                mediaType == MEDIA_TYPE_IMAGE_VFDR ||
                mediaType == MEDIA_TYPE_IMAGE_MOTION_PHOTO ||
                mediaType == MEDIA_TYPE_IMAGE_MOTION_HDR_PHOTO ||
                mediaType == MEDIA_TYPE_IMAGE_MOTION_FDR_PHOTO ||
                mediaType == MEDIA_TYPE_IMAGE_MOTION_AI_PHOTO ||
                mediaType == MEDIA_TYPE_IMAGE_MOTION_HDR_AI_PHOTO ||
                mediaType == MEDIA_TYPE_IMAGE_MOTION_FDR_AI_PHOTO);
    }

    private boolean isSupportCrop() {
        if (TextUtils.isEmpty(mimeType)) {
            return false;
        }
        String type = mimeType.toLowerCase();
        return !isLowRam || !type.endsWith("bmp") || width * height < MAX_BMP_CROP_PIXEL;
    }

    @Override
    public String getDate() {
        return DateUtils.timeStringWithDateInMs(GalleryAppImpl.getApplication(),
                dateModifiedInSec == 0 ? dateTakenInMs : dateModifiedInSec * 1000);
    }

    @Override
    public void moveOutThings() {
        Log.d(TAG, "moveOutThings");
        DiscoverStore.moveOutThings(id);
    }
}
