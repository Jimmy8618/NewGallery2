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
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Video;
import android.provider.MediaStore.Video.VideoColumns;
import android.util.Log;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.util.BucketNames;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.MediaSetUtils;
import com.android.gallery3d.v2.data.CameraSource;
import com.sprd.frameworks.StandardFrameworks;

import java.io.File;
import java.util.ArrayList;

// LocalAlbumSet lists all media items in one bucket on local storage.
// The media items need to be all images or all videos, but not both.
public class LocalAlbum extends MediaSet {
    private static final String TAG = "LocalAlbum";
    private static final String[] COUNT_PROJECTION = {"count(*)"};

    private static final int INVALID_COUNT = -1;
    private String mWhereClause;
    private final String mOrderClause;
    private final Uri mBaseUri;
    private final String[] mProjection;

    private final GalleryApp mApplication;
    private final ContentResolver mResolver;
    private final Integer mBucketId;
    private final String mName;
    private final boolean mIsImage;
    private final ChangeNotifier mNotifier;
    private final Path mItemPath;
    private int mCachedCount = INVALID_COUNT;

    private String mHidedAlbumsClause;
    private boolean mForceDirty;

    public LocalAlbum(Path path, GalleryApp application, Integer bucketId,
                      boolean isImage, String name) {
        super(path, nextVersionNumber());
        mApplication = application;
        mResolver = application.getContentResolver();
        mBucketId = bucketId;
        mName = name;
        mIsImage = isImage;

        if (isImage) {
            if (mBucketId != null) {
                mWhereClause = ImageColumns.BUCKET_ID + " = ?";
                if (StandardFrameworks.getInstances().isSupportBurstImage()) {
                    mWhereClause += ") and (file_flag != " + LocalImage.IMG_TYPE_MODE_BURST + " or file_flag is null";
                }
            } else {
                mWhereClause = null;
                if (StandardFrameworks.getInstances().isSupportBurstImage()) {
                    mWhereClause = "file_flag != " + LocalImage.IMG_TYPE_MODE_BURST + " or file_flag is null";
                }
            }
            mOrderClause = ImageColumns.DATE_MODIFIED + " DESC, "
                    + ImageColumns._ID + " DESC";
            mBaseUri = Images.Media.EXTERNAL_CONTENT_URI;
            mProjection = LocalImage.PROJECTION;
            mItemPath = LocalImage.ITEM_PATH;
        } else {
            if (mBucketId != null) {
                mWhereClause = VideoColumns.BUCKET_ID + " = ?";
            } else {
                mWhereClause = null;
            }
            mOrderClause = VideoColumns.DATE_MODIFIED + " DESC, "
                    + VideoColumns._ID + " DESC";
            mBaseUri = Video.Media.EXTERNAL_CONTENT_URI;
            mProjection = LocalVideo.PROJECTION;
            mItemPath = LocalVideo.ITEM_PATH;
        }

        mNotifier = new ChangeNotifier(this, new Uri[]{mBaseUri}, application);

        if (GalleryUtils.isSupportV2UI()) {
            if (mBucketId == null) {//"所有图片" 相册 会用到, 隐藏的文件夹图片不用显示
                mHidedAlbumsClause = hidedAlbumsClause();
            }
        }
    }

    public LocalAlbum(Path path, GalleryApp application, Integer bucketId,
                      boolean isImage) {
        this(path, application, bucketId, isImage,
                BucketHelper.getBucketName(
                        application.getContentResolver(), bucketId));
    }

    /* SPRD: launch camera when sliding pictures in camera folder
    @Override
    public boolean isCameraRoll() {
        return mBucketId == MediaSetUtils.CAMERA_BUCKET_ID;
    }
    */
    @Override
    public Uri getContentUri() {
        if (mBucketId != null) {
            if (mIsImage) {
                return MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon()
                        .appendQueryParameter(LocalSource.KEY_BUCKET_ID,
                                String.valueOf(mBucketId)).build();
            } else {
                return MediaStore.Video.Media.EXTERNAL_CONTENT_URI.buildUpon()
                        .appendQueryParameter(LocalSource.KEY_BUCKET_ID,
                                String.valueOf(mBucketId)).build();
            }
        } else {
            return mBaseUri;
        }
    }

    @Override
    public ArrayList<MediaItem> getMediaItem(int start, int count) {
        //Log.d(TAG, "getMediaItem start=" + start + ", count=" + count);
        DataManager dataManager = mApplication.getDataManager();
        Uri uri = mBaseUri.buildUpon()
                .appendQueryParameter("limit", start + "," + count)
                .appendQueryParameter("nonotify", "1")
                .build();
        ArrayList<MediaItem> list = new ArrayList<MediaItem>();
        GalleryUtils.assertNotInRenderThread();
        Cursor cursor = null;
        try {
            String[] args = mBucketId == null ? null : new String[]{String.valueOf(mBucketId)};
            cursor = mResolver.query(uri, mProjection, whereClause(), args, mOrderClause);
        } catch (Exception e) {
            Log.w(TAG, "Gallery permissions is error,can't get data.", e);
            if (cursor != null) {
                cursor.close();
            }
            return list;
        }
        if (cursor == null) {
            Log.w(TAG, "query fail: " + uri);
            return list;
        }

        try {
            while (cursor.moveToNext()) {
                if (mQuitQuery) {
                    return list;
                }
                int id = cursor.getInt(0);  // _id must be in the first column
                Path childPath = mItemPath.getChild(id);
                MediaItem item = loadOrUpdateItem(childPath, cursor,
                        dataManager, mApplication, mIsImage);
                // Log.d(TAG, "LocalAlbum.getMediaItem add " + item.getFilePath() + ", type=" + item.getMimeType());
                list.add(item);
            }
        } finally {
            cursor.close();
        }
        return list;
    }

    private static MediaItem loadOrUpdateItem(Path path, Cursor cursor,
                                              DataManager dataManager, GalleryApp app, boolean isImage) {
        synchronized (DataManager.LOCK) {
            LocalMediaItem item = (LocalMediaItem) dataManager.peekMediaObject(path);
            if (item == null) {
                if (isImage) {
                    item = new LocalImage(path, app, cursor);
                } else {
                    item = new LocalVideo(path, app, cursor);
                }
            } else {
                item.updateContent(cursor);
            }
            return item;
        }
    }

    // The pids array are sorted by the (path) id.
    public static MediaItem[] getMediaItemById(
            GalleryApp application, boolean isImage, ArrayList<Integer> ids) {
        // get the lower and upper bound of (path) id
        MediaItem[] result = new MediaItem[ids.size()];
        if (ids.isEmpty()) {
            return result;
        }
        int idLow = ids.get(0);
        int idHigh = ids.get(ids.size() - 1);

        // prepare the query parameters
        Uri baseUri;
        String[] projection;
        Path itemPath;
        if (isImage) {
            baseUri = Images.Media.EXTERNAL_CONTENT_URI;
            projection = LocalImage.PROJECTION;
            itemPath = LocalImage.ITEM_PATH;
        } else {
            baseUri = Video.Media.EXTERNAL_CONTENT_URI;
            projection = LocalVideo.PROJECTION;
            itemPath = LocalVideo.ITEM_PATH;
        }

        ContentResolver resolver = application.getContentResolver();
        DataManager dataManager = application.getDataManager();
        Cursor cursor = null;
        try {
            cursor = resolver.query(baseUri, projection, "_id BETWEEN ? AND ?",
                    new String[]{String.valueOf(idLow), String.valueOf(idHigh)},
                    "_id");
        } catch (Exception e) {
            Log.w(TAG, "Gallery permissions is error,can't get data.", e);
            if (cursor != null) {
                cursor.close();
            }
            return result;
        }
        if (cursor == null) {
            Log.w(TAG, "query fail" + baseUri);
            return result;
        }
        try {
            int n = ids.size();
            int i = 0;

            while (i < n && cursor.moveToNext()) {
                int id = cursor.getInt(0);  // _id must be in the first column

                // Match id with the one on the ids list.
                if (ids.get(i) > id) {
                    continue;
                }

                while (ids.get(i) < id) {
                    if (++i >= n) {
                        return result;
                    }
                }

                Path childPath = itemPath.getChild(id);
                MediaItem item = loadOrUpdateItem(childPath, cursor, dataManager,
                        application, isImage);
                result[i] = item;
                ++i;
            }
            return result;
        } finally {
            cursor.close();
        }
    }

    public static Cursor getItemCursor(ContentResolver resolver, Uri uri,
                                       String[] projection, int id) {
        return resolver.query(uri, projection, "_id=?",
                new String[]{String.valueOf(id)}, null);
    }

    @Override
    public int getMediaItemCount() {
        if (mCachedCount == INVALID_COUNT) {
            Cursor cursor = null;
            try {
                Uri uri = mBaseUri.buildUpon().appendQueryParameter("nonotify", "1").build();
                String[] args = mBucketId == null ? null : new String[]{String.valueOf(mBucketId)};
                cursor = mResolver.query(
                        uri, mProjection/*COUNT_PROJECTION*/, whereClause(),
                        args, null);
            } catch (Exception e) {
                Log.w(TAG, "Gallery permissions is error,can't get data.", e);
                if (cursor != null) {
                    cursor.close();
                }
                return 0;
            }

            if (cursor == null) {
                Log.w(TAG, "query fail");
                return 0;
            }
            try {
                //Utils.assertTrue(cursor.moveToNext());
                //mCachedCount = cursor.getInt(0);
                mCachedCount = cursor.getCount();
            } finally {
                cursor.close();
            }
        }
        return mCachedCount;
    }

    @Override
    public String getName() {
        return getLocalizedName(mApplication.getResources(), mBucketId, mName);
    }

    @Override
    public long reload() {
        if (mNotifier.isDirty() || mForceDirty) {

            if (mForceDirty) {
                mForceDirty = false;
                if (GalleryUtils.isSupportV2UI()) {
                    if (mBucketId == null) {//"所有图片" 相册 会用到, 隐藏的文件夹图片不用显示
                        mHidedAlbumsClause = hidedAlbumsClause();
                    }
                }
            }

            mDataVersion = nextVersionNumber();
            mCachedCount = INVALID_COUNT;
        }
        return mDataVersion;
    }

    @Override
    public int getSupportedOperations() {
        return SUPPORT_DELETE | SUPPORT_SHARE | SUPPORT_INFO;
    }

    @Override
    public void delete() {
        if (mBucketId != null) {
            GalleryUtils.assertNotInRenderThread();
            mResolver.delete(mBaseUri, whereClause(),
                    new String[]{String.valueOf(mBucketId)});
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public boolean isLeafAlbum() {
        return true;
    }

    public static String getLocalizedName(Resources res, Integer bucket,
                                          String name) {
        ArrayList<Integer> cameraSource = CameraSource.getCameraSources();
        ArrayList<Integer> otgCameraSource = CameraSource.getOtgCameraSources();
        int bucketId = bucket == null ? 0 : bucket;
        if (otgCameraSource.contains(bucketId)) {
            return "Camera";
        } else if (cameraSource.contains(bucketId)) {
            return res.getString(R.string.folder_camera);
        } else if (bucketId == MediaSetUtils.DOWNLOAD_BUCKET_ID) {
            return res.getString(R.string.folder_download);
        } else if (bucketId == MediaSetUtils.IMPORTED_BUCKET_ID) {
            return res.getString(R.string.folder_imported);
        } else if (bucketId == MediaSetUtils.SNAPSHOT_BUCKET_ID) {
            return res.getString(R.string.folder_screenshot);
        } else if (bucketId == MediaSetUtils.EDITED_ONLINE_PHOTOS_BUCKET_ID) {
            return res.getString(R.string.folder_edited_online_photos);
        } else if (bucketId == MediaSetUtils.INTERNAL_STORAGE_BUCKET_ID) {
            return res.getString(R.string.internal_storage);
        } else if (bucketId == MediaSetUtils.EXTERNAL_STORAGE_BUCKET_ID) {
            return res.getString(R.string.external_storage);
        } else {
            return name;
        }
    }

    // Relative path is the absolute path minus external storage path
    public static String getRelativePath(int bucketId) {
        String relativePath = "/";
        if (bucketId == MediaSetUtils.CAMERA_BUCKET_ID) {
            relativePath += BucketNames.CAMERA;
        } else if (bucketId == MediaSetUtils.DOWNLOAD_BUCKET_ID) {
            relativePath += BucketNames.DOWNLOAD;
        } else if (bucketId == MediaSetUtils.IMPORTED_BUCKET_ID) {
            relativePath += BucketNames.IMPORTED;
        } else if (bucketId == MediaSetUtils.SNAPSHOT_BUCKET_ID) {
            relativePath += BucketNames.SCREENSHOTS;
        } else if (bucketId == MediaSetUtils.EDITED_ONLINE_PHOTOS_BUCKET_ID) {
            relativePath += BucketNames.EDITED_ONLINE_PHOTOS;
        } else {
            // If the first few cases didn't hit the matching path, do a
            // thorough search in the local directories.
            File extStorage = Environment.getExternalStorageDirectory();
            String path = GalleryUtils.searchDirForPath(extStorage, bucketId);
            if (path == null) {
                Log.w(TAG, "Relative path for bucket id: " + bucketId + " is not found.");
                relativePath = null;
            } else {
                relativePath = path.substring(extStorage.getAbsolutePath().length());
            }
        }
        return relativePath;
    }

    /* SPRD ADD: Fix Bug 380684, try to scan the whole sdcard to get relative path @{ */
    public String getRelativePath() {
        String relativePath = "/";
        int bucket_id = mBucketId == null ? 0 : mBucketId;
        if (bucket_id == MediaSetUtils.CAMERA_BUCKET_ID) {
            relativePath += BucketNames.CAMERA;
        } else if (bucket_id == MediaSetUtils.DOWNLOAD_BUCKET_ID) {
            relativePath += BucketNames.DOWNLOAD;
        } else if (bucket_id == MediaSetUtils.IMPORTED_BUCKET_ID) {
            relativePath += BucketNames.IMPORTED;
        } else if (bucket_id == MediaSetUtils.SNAPSHOT_BUCKET_ID) {
            relativePath += BucketNames.SCREENSHOTS;
        } else if (bucket_id == MediaSetUtils.EDITED_ONLINE_PHOTOS_BUCKET_ID) {
            relativePath += BucketNames.EDITED_ONLINE_PHOTOS;
        } else {
            ArrayList<MediaItem> oneInArray = getMediaItem(0, 1);
            if (oneInArray == null || oneInArray.size() <= 0) {
                return null;
            }
            MediaItem item = oneInArray.get(0);
            File extStorage = Environment.getExternalStorageDirectory();
            String itemPath = item.getFilePath();
            if (!itemPath.contains(extStorage.getAbsolutePath())) {
                return null;
            }
            String[] array = itemPath.split("/");
            String parentDir = itemPath
                    .substring(0, itemPath.length() - array[array.length - 1].length() - 1);
            relativePath = parentDir.substring(extStorage.getAbsolutePath().length());
            if (relativePath.length() == 0) {
                return "/";
            }
        }
        return relativePath;
    }
    /* @} */

    public boolean isImage() {
        return mIsImage;
    }

    @Override
    public int getBucketId() {
        return mBucketId == null ? 0 : mBucketId;
    }

    private String whereClause() {
        String where = mWhereClause;
        if (mBucketId == null) {
            if (where == null) {
                where = mHidedAlbumsClause;
            } else {
                where += ") and (" + mHidedAlbumsClause;
            }
        }
        return where;
    }

    @Override
    public void reForceDirty(boolean forceDirty) {
        mForceDirty = forceDirty;
    }
}
