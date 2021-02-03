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
import android.database.Cursor;
import android.drm.DrmStore;
import android.graphics.Bitmap;
import android.graphics.BitmapRegionDecoder;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore.Video;
import android.provider.MediaStore.Video.VideoColumns;
import android.util.Log;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.app.GalleryStorageUtil;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.BitmapUtils;
import com.android.gallery3d.util.DateUtils;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;
import com.android.gallery3d.util.UpdateHelper;
import com.android.gallery3d.v2.util.SdCardPermission;
import com.sprd.frameworks.StandardFrameworks;
import com.sprd.gallery3d.drm.LocalMediaItemUtils;

import java.util.ArrayList;

// LocalVideo represents a video in the local storage.
public class LocalVideo extends LocalMediaItem {
    private static final String TAG = "LocalVideo";
    public static final Path ITEM_PATH = Path.fromString("/local/video/item");

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
    private static final int INDEX_DURATION = 9;
    private static final int INDEX_BUCKET_ID = 10;
    private static final int INDEX_SIZE = 11;
    private static final int INDEX_RESOLUTION = 12;
    private static final int INDEX_DISPLAY_NAME = 13;
    private static final ArrayList<String> PROJECTION_ARRAY = new ArrayList<>();

    static String[] PROJECTION;

    static {
        PROJECTION_ARRAY.add(VideoColumns._ID);           //0
        PROJECTION_ARRAY.add(VideoColumns.TITLE);         //1
        PROJECTION_ARRAY.add(VideoColumns.MIME_TYPE);     //2
        PROJECTION_ARRAY.add(VideoColumns.LATITUDE);      //3
        PROJECTION_ARRAY.add(VideoColumns.LONGITUDE);     //4
        PROJECTION_ARRAY.add(VideoColumns.DATE_TAKEN);    //5
        PROJECTION_ARRAY.add(VideoColumns.DATE_ADDED);    //6
        PROJECTION_ARRAY.add(VideoColumns.DATE_MODIFIED); //7
        PROJECTION_ARRAY.add(VideoColumns.DATA);          //8
        PROJECTION_ARRAY.add(VideoColumns.DURATION);      //9
        PROJECTION_ARRAY.add(VideoColumns.BUCKET_ID);     //10
        PROJECTION_ARRAY.add(VideoColumns.SIZE);          //11
        PROJECTION_ARRAY.add(VideoColumns.RESOLUTION);    //12
        PROJECTION_ARRAY.add(VideoColumns.DISPLAY_NAME);  //13
        if (StandardFrameworks.getInstances().isSupportIsDrm()) {
            PROJECTION_ARRAY.add(COLUMN_IS_DRM);          //14
        }

        PROJECTION = PROJECTION_ARRAY.toArray(new String[0]);
    }

    private final GalleryApp mApplication;

    public int durationInSec;

    public LocalVideo(Path path, GalleryApp application, Cursor cursor) {
        super(path, nextVersionNumber());
        mApplication = application;
        loadFromCursor(cursor);
        /* SPRD: Drm feature start ,bug 598636,DRM optimization @{ */
        if (displayName != null && displayName.endsWith(".dcf")) {
            LocalMediaItemUtils.getInstance().loadDrmInfor(this);
        }
        /* SPRD: Drm feature end @} */
    }

    public LocalVideo(Path path, GalleryApp context, int id) {
        super(path, nextVersionNumber());
        mApplication = context;
        ContentResolver resolver = mApplication.getContentResolver();
        Uri uri = Video.Media.EXTERNAL_CONTENT_URI;
        Cursor cursor = null;
        try {
            cursor = LocalAlbum.getItemCursor(resolver, uri, PROJECTION, id);
        } catch (SecurityException e) {
            Log.d(TAG, "SecurityException " + e.toString());
        }
        if (cursor == null) {
            path.recycleObject();
            throw new RuntimeException("cannot get cursor for: " + path);
        }
        try {
            if (cursor.moveToNext()) {
                loadFromCursor(cursor);
            } else {
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
        durationInMs = cursor.getInt(INDEX_DURATION);
        durationInSec = (int) (durationInMs / 1000);
        bucketId = cursor.getInt(INDEX_BUCKET_ID);
        fileSize = cursor.getLong(INDEX_SIZE);
        parseResolution(cursor.getString(INDEX_RESOLUTION));
        displayName = cursor.getString(INDEX_DISPLAY_NAME);
        if (StandardFrameworks.getInstances().isSupportIsDrm()) {
            isDrm = cursor.getInt(cursor.getColumnIndex(COLUMN_IS_DRM));
        }
    }

    private void parseResolution(String resolution) {
        if (resolution == null) {
            return;
        }
        int m = resolution.indexOf('x');
        if (m == -1) {
            m = resolution.indexOf('×');
            if (m == -1) {
                return;
            }
        }
        try {
            int w = Integer.parseInt(resolution.substring(0, m));
            int h = Integer.parseInt(resolution.substring(m + 1));
            width = w;
            height = h;
        } catch (Throwable t) {
            Log.w(TAG, t);
        }
    }

    @Override
    protected boolean updateFromCursor(Cursor cursor) {
        UpdateHelper uh = new UpdateHelper();
        id = uh.update(id, cursor.getInt(INDEX_ID));
        caption = uh.update(caption, cursor.getString(INDEX_CAPTION));
        mimeType = uh.update(mimeType, cursor.getString(INDEX_MIME_TYPE));
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
        durationInMs = uh.update(durationInMs, cursor.getInt(INDEX_DURATION));
        durationInSec = (int) (durationInMs / 1000);
        bucketId = uh.update(bucketId, cursor.getInt(INDEX_BUCKET_ID));
        fileSize = uh.update(fileSize, cursor.getLong(INDEX_SIZE));
        displayName = uh.update(displayName, cursor.getString(INDEX_DISPLAY_NAME));
        if (StandardFrameworks.getInstances().isSupportIsDrm()) {
            isDrm = uh.update(isDrm, cursor.getInt(cursor.getColumnIndex(COLUMN_IS_DRM)));
        }
        return uh.isUpdated();
    }

    @Override
    public Job<Bitmap> requestImage(int type) {
        if (Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.Q) {
            return new LocalVideoRequest(mApplication, getPath(), dateModifiedInSec,
                    type, getContentUri());
        } else {
            return new LocalVideoRequest(mApplication, getPath(), dateModifiedInSec,
                    type, filePath);
        }
    }

    public static class LocalVideoRequest extends ImageCacheRequest {
        /* SPRD: Drm feature start @{
        private String mLocalFilePath;
        */
        public String mLocalFilePath;
        public Uri mUri;
        /* SPRD: Drm feature end @} */

        public LocalVideoRequest(GalleryApp application, Path path, long timeModified,
                                 int type, String localFilePath) {
            super(application, path, timeModified, type,
                    MediaItem.getTargetSize(type));
            mLocalFilePath = localFilePath;
        }

        public LocalVideoRequest(GalleryApp application, Path path, long timeModified,
                                 int type, Uri uri) {
            super(application, path, timeModified, type,
                    MediaItem.getTargetSize(type));
            mUri = uri;
        }

        @Override
        public Bitmap onDecodeOriginal(JobContext jc, int type) {
            Bitmap bitmap;
            if (mUri != null) {
                bitmap = BitmapUtils.createVideoThumbnail(mApplication.getAndroidContext(), mUri);
            } else {
                bitmap = BitmapUtils.createVideoThumbnail(mLocalFilePath);
            }

            if (bitmap == null || jc.isCancelled()) {
                return null;
            }
            return bitmap;
        }
    }

    @Override
    public Job<BitmapRegionDecoder> requestLargeImage() {
        throw new UnsupportedOperationException("Cannot regquest a large image"
                + " to a local video!");
    }

    @Override
    public int getSupportedOperations() {
        /* SPRD : Drm feature start @{
        return SUPPORT_DELETE | SUPPORT_SHARE | SUPPORT_PLAY | SUPPORT_INFO | SUPPORT_TRIM | SUPPORT_MUTE;
        */
        int operation = SUPPORT_DELETE | SUPPORT_SHARE | SUPPORT_PLAY | SUPPORT_INFO
                | SUPPORT_TRIM | SUPPORT_MUTE;
        operation = LocalMediaItemUtils.getInstance().getVideoSupportedOperations(this, operation);
        return operation;
        /* SPRD : Drm feature end  @} */
    }

    @Override
    public void delete() {
        Log.d(TAG, "delete " + filePath);
        GalleryUtils.assertNotInRenderThread();
        if (GalleryStorageUtil.isInInternalStorage(filePath) || Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.Q) {
            Uri baseUri = Video.Media.EXTERNAL_CONTENT_URI;
            mApplication.getContentResolver().delete(baseUri, "_id=?",
                    new String[]{String.valueOf(id)});
        } else {
            SdCardPermission.deleteFile(filePath);
        }
    }

    @Override
    public void rotate(int degrees) {
        // TODO
    }

    @Override
    public Uri getContentUri() {
        Uri baseUri = Video.Media.EXTERNAL_CONTENT_URI;
        return baseUri.buildUpon().appendPath(String.valueOf(id)).build();
    }

    @Override
    public Uri getPlayUri() {
        return getContentUri();
    }

    @Override
    public int getMediaType() {
        return MEDIA_TYPE_VIDEO;
    }

    @Override
    public MediaDetails getDetails() {
        MediaDetails details = super.getDetails();
        int s = durationInSec;
        if (s > 0) {
            details.addDetail(MediaDetails.INDEX_DURATION, GalleryUtils.formatDuration(
                    mApplication.getAndroidContext(), durationInSec));
        }

        /* SPRD: Drm feature start @{ */
        details = LocalMediaItemUtils.getInstance().getDetailsByAction(this, details, DrmStore.Action.PLAY, mApplication.getAndroidContext());
        /* SPRD: Drm feature end @} */
        return details;
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

    public int getDuration() {
        return durationInSec;
    }

    @Override
    public String getDurationString() {
        return GalleryUtils.formatDuration(mApplication.getAndroidContext(), durationInSec);
    }

    @Override
    public String getDate() {
        return DateUtils.timeStringWithDateInMs(GalleryAppImpl.getApplication(),
                dateModifiedInSec == 0 ? dateTakenInMs : dateModifiedInSec * 1000);
    }
}
