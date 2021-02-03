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

import android.net.Uri;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.util.Log;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.data.BucketHelper.BucketEntry;
import com.android.gallery3d.ui.SelectionManager;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.MediaSetUtils;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.util.ThreadPool.JobContext;
import com.android.gallery3d.v2.data.AllMediaAlbum;
import com.android.gallery3d.v2.data.CameraMergeAlbum;
import com.android.gallery3d.v2.data.CameraSource;
import com.android.gallery3d.v2.trash.TrashManager;
import com.android.gallery3d.v2.trash.data.TrashAlbum;
import com.android.gallery3d.v2.trash.db.TrashStore;
import com.android.gallery3d.v2.util.Config;
import com.sprd.frameworks.StandardFrameworks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

// LocalAlbumSet lists all image or video albums in the local storage.
// The path should be "/local/image", "local/video" or "/local/all"
public class LocalAlbumSet extends MediaSet
        implements FutureListener<ArrayList<MediaSet>> {
    @SuppressWarnings("unused")
    protected String TAG = LocalAlbumSet.class.getSimpleName();

    public static final Path PATH_ALL = Path.fromString("/local/all");
    public static final Path PATH_IMAGE = Path.fromString("/local/image");
    public static final Path PATH_VIDEO = Path.fromString("/local/video");

    private static final Uri[] mWatchUris = {
            Images.Media.EXTERNAL_CONTENT_URI,
            /*SPRD: bug 530002 insert WatchUri for Mtp */
            Video.Media.EXTERNAL_CONTENT_URI,
            StandardFrameworks.getInstances().getMtpObjectsUri("external"),
            TrashStore.Local.Media.CONTENT_URI
    };

    private final GalleryApp mApplication;
    private final int mType;
    private ArrayList<MediaSet> mAlbums = new ArrayList<>();
    private final ChangeNotifier mNotifier;
    private final String mName;
    private boolean mIsLoading;

    private Future<ArrayList<MediaSet>> mLoadTask;
    private ArrayList<MediaSet> mLoadBuffer;

    public LocalAlbumSet(Path path, GalleryApp application) {
        super(path, nextVersionNumber());
        mApplication = application;
        mType = getTypeFromPath(path);
        mNotifier = new ChangeNotifier(this, mWatchUris, application);
        mName = application.getResources().getString(
                R.string.set_label_local_albums);
    }

    private static int getTypeFromPath(Path path) {
        String name[] = path.split();
        if (name.length < 2) {
            throw new IllegalArgumentException(path.toString());
        }
        return getTypeFromString(name[1]);
    }

    @Override
    public MediaSet getSubMediaSet(int index) {
        return mAlbums.get(index);
    }

    @Override
    public int getSubMediaSetCount() {
        return mAlbums.size();
    }

    @Override
    public String getName() {
        return mName;
    }

    private static int findBucket(BucketEntry entries[], int bucketId) {
        for (int i = 0, n = entries.length; i < n; ++i) {
            if (entries[i].bucketId == bucketId) {
                return i;
            }
        }
        return -1;
    }

    private class AlbumsLoader implements ThreadPool.Job<ArrayList<MediaSet>> {

        @Override
        @SuppressWarnings("unchecked")
        public ArrayList<MediaSet> run(JobContext jc) {
            // Note: it will be faster if we only select media_type and bucket_id.
            //       need to test the performance if that is worth
            Log.d(TAG, "AlbumsLoader loadBucketEntries B.");
            BucketEntry[] entries = BucketHelper.loadBucketEntries(
                    jc, mApplication.getContentResolver(), mType);
            Log.d(TAG, "AlbumsLoader loadBucketEntries E.");
            if (jc.isCancelled()) {
                return null;
            }

            int offset = 0;
            // Move camera and download bucket to the front, while keeping the
            // order of others.
            // Storage
            int index = findBucket(entries, MediaSetUtils.INTERNAL_STORAGE_BUCKET_ID);
            if (index != -1) {
                circularShiftRight(entries, offset++, index);
            }
            index = findBucket(entries, MediaSetUtils.EXTERNAL_STORAGE_BUCKET_ID);
            if (index != -1) {
                circularShiftRight(entries, offset++, index);
            }
            // Camera
            index = findBucket(entries, MediaSetUtils.CAMERA_BUCKET_ID);
            if (index != -1) {
                circularShiftRight(entries, offset++, index);
            }
            index = findBucket(entries, MediaSetUtils.EXTERNAL_CAMERA_BUCKET_ID);
            if (index != -1) {
                circularShiftRight(entries, offset++, index);
            }
            // Download
            index = findBucket(entries, MediaSetUtils.DOWNLOAD_BUCKET_ID);
            if (index != -1) {
                circularShiftRight(entries, offset++, index);
            }
            index = findBucket(entries, MediaSetUtils.EXTERNAL_DOWNLOAD_BUCKET_ID);
            if (index != -1) {
                circularShiftRight(entries, offset++, index);
            }
            // Snapshot
            index = findBucket(entries, MediaSetUtils.SNAPSHOT_BUCKET_ID);
            if (index != -1) {
                circularShiftRight(entries, offset++, index);
            }
            index = findBucket(entries, MediaSetUtils.EXTERNAL_SNAPSHOT_BUCKET_ID);
            if (index != -1) {
                circularShiftRight(entries, offset++, index);
            }
            // Bluetooth
            index = findBucket(entries, MediaSetUtils.BLUETOOTH_BUCKET_ID);
            if (index != -1) {
                circularShiftRight(entries, offset++, index);
            }
            index = findBucket(entries, MediaSetUtils.EXTERNAL_BLUETOOTH_BUCKET_ID);
            if (index != -1) {
                circularShiftRight(entries, offset++, index);
            }
            index = findBucket(entries, MediaSetUtils.BLUETOOTH_BUCKET_2_ID);
            if (index != -1) {
                circularShiftRight(entries, offset++, index);
            }
            index = findBucket(entries, MediaSetUtils.EXTERNAL_BLUETOOTH_BUCKET_2_ID);
            if (index != -1) {
                circularShiftRight(entries, offset++, index);
            }
            // Video
            index = findBucket(entries, MediaSetUtils.VIDEO_ID);
            if (index != -1) {
                circularShiftRight(entries, offset++, index);
            }
            index = findBucket(entries, MediaSetUtils.EXTERNAL_VIDEO_ID);
            if (index != -1) {
                circularShiftRight(entries, offset++, index);
            }
            // Browser
            index = findBucket(entries, MediaSetUtils.BROWSER_ID);
            if (index != -1) {
                circularShiftRight(entries, offset++, index);
            }
            index = findBucket(entries, MediaSetUtils.EXTERNAL_BROWSER_ID);
            if (index != -1) {
                circularShiftRight(entries, offset++, index);
            }
            // QQ Images
            index = findBucket(entries, MediaSetUtils.QQ_IMAGES_ID);
            if (index != -1) {
                circularShiftRight(entries, offset++, index);
            }
            index = findBucket(entries, MediaSetUtils.EXTERNAL_QQ_IMAGES_ID);
            if (index != -1) {
                circularShiftRight(entries, offset++, index);
            }

            // Array MyAlbums
            arrayMyAlbums(entries);

            ArrayList<MediaSet> albums = new ArrayList<>();
            DataManager dataManager = mApplication.getDataManager();
            /* judge if otg has been choosed */
            String currentPath = dataManager.getOtgDeviceCurrentPath();
            HashMap<String, String> otgDeviceInfos = dataManager.getOtgDeviceInfos();

            /*hide albums */
            Set<String> hideAlbums = new HashSet<>();
            MediaSet album;

            hideAlbums = Config.getPref(SelectionManager.KEY_SELECT_ALBUM_ITEMS, hideAlbums);
            boolean hasHideAlbums = Config.getPref(SelectionManager.KEY_SELECT_ALBUM_FLAG, false);
            boolean hasLocalAlbums = Config.getPref(SelectionManager.KEY_SELECT_LOCAL_ALBUMS, false);
            boolean isInEditScreen = Config.getPref(SelectionManager.KEY_EDIT_ALBUMS_SCREEN, false);
            Set<String> allBucketId = dataManager.getAllBucketId();
            dataManager.setAllBucketEntry(mPath, entries);

            Log.d(TAG, "AlbumsLoader isInEditScreen : " + isInEditScreen + ", hided album size : " + hideAlbums.size());

            if (GalleryUtils.isSupportV2UI()) {
                //add All Source
                album = getAllMediaAlbum(dataManager, mType);
                Log.d(TAG, "AlbumsLoader albums.add(" + album.getName() + ")");
                albums.add(album);
            }

            //add Camera Source
            ArrayList<Integer> cameraSource = CameraSource.getCameraSources();
            //是否是otg视图
            boolean isOtgContent = otgDeviceInfos.size() != 0 && currentPath != null;
            //非otg视图, 才显示 CameraMergeAlbum
            if (!isOtgContent) {
                for (BucketEntry entry : entries) {
                    if (cameraSource.contains(entry.bucketId)) {
                        album = getCameraAlbum(dataManager, mType);
                        Log.d(TAG, "AlbumsLoader albums.add(" + album.getName() + ")");
                        albums.add(album);
                        break;
                    }
                }
            }
            //
            for (BucketEntry entry : entries) {
                if (!isOtgContent) {
                    //非otg视图下, DCIM/Camera路径不用显示, 已包含在了 CameraMergeAlbum 中
                    if (cameraSource.contains(entry.bucketId)) {
                        continue;
                    }
                }
                if (entry.bucketId == TrashManager.getBucketId()) {
                    album = dataManager.getMediaSet(TrashAlbum.PATH);
                    Log.d(TAG, "AlbumsLoader albums.add(" + album.getName() + ")");
                    albums.add(album);
                    continue;
                } else {
                    album = getLocalAlbum(mType, mPath, entry.bucketId, entry.bucketName);
                    album.setMyAlbum(entry.isMyAlbum());
                }

                if (!isInEditScreen) {
                    /* only show local albums */
                    if (hasLocalAlbums) {
                        if (!allBucketId.contains(String.valueOf(entry.bucketId))) {
                            continue;
                        }
                    }
                    /* hide third party albums */
                    if (hasHideAlbums && !hasLocalAlbums) {
                        boolean isHideAlbum = false;
                        if (hideAlbums.size() > 0) {
                            for (String hideAlbum : hideAlbums) {
                                if (hideAlbum.contains(String.valueOf(entry.bucketId))) {
                                    isHideAlbum = true;
                                    break;
                                }
                            }
                            if (isHideAlbum) {
                                continue;
                            }
                        }
                    }
                }

                /*otg device content show */
                if (otgDeviceInfos.size() != 0 && currentPath != null) {
                    if (otgDeviceInfos.containsKey(currentPath)) {
                        if (entry.bucketFilename.contains(currentPath)) {
                            Log.d(TAG, "AlbumsLoader otg albums.add(" + album.getName() + ")");
                            albums.add(album);
                        }
                    }
                } else {
                    Log.d(TAG, "AlbumsLoader albums.add(" + album.getName() + ")");
                    albums.add(album);
                }
            }
            Log.d(TAG, "AlbumsLoader load albums success, size is " + albums.size());
            return albums;
        }
    }

    public static MediaSet getLocalAlbum(int type, Path parent, int id, String name) {
        synchronized (DataManager.LOCK) {
            GalleryApp application = GalleryAppImpl.getApplication();
            Path path = parent.getChild(id);
            MediaObject object = application.getDataManager().peekMediaObject(path);
            if (object != null) {
                return (MediaSet) object;
            }
            switch (type) {
                case MEDIA_TYPE_IMAGE:
                    return new LocalAlbum(path, application, id, true, name);
                case MEDIA_TYPE_VIDEO:
                    return new LocalAlbum(path, application, id, false, name);
                case MEDIA_TYPE_ALL:
                    return new LocalMergeAlbum(path, new MediaSet[]{
                            getLocalAlbum(MEDIA_TYPE_IMAGE, PATH_IMAGE, id, name),
                            getLocalAlbum(MEDIA_TYPE_VIDEO, PATH_VIDEO, id, name)}, id);
                default:
                    throw new IllegalArgumentException(String.valueOf(type));
            }

        }
    }

    @Override
    public synchronized boolean isLoading() {
        return mIsLoading;
    }

    private boolean mForceDirty = false;

    @Override
    public void reForceDirty(boolean forceDirty) {
        mForceDirty = forceDirty;

    }

    @Override
    // synchronized on this function for
    //   1. Prevent calling reload() concurrently.
    //   2. Prevent calling onFutureDone() and reload() concurrently
    public synchronized long reload() {
        /* when hide albums ,need to reload data*/
        if (mNotifier.isDirty() || mForceDirty) {
            if (mLoadTask != null) {
                mLoadTask.cancel();
            }
            mIsLoading = true;
            if (mForceDirty) {
                if (GalleryUtils.isSupportV2UI()) {
                    MediaSet album = getAllMediaAlbum(mApplication.getDataManager(), mType);
                    if (album != null) {
                        album.reForceDirty(true);
                    }
                }
            }
            mLoadTask = mApplication.getThreadPool().submit(new AlbumsLoader(), this);
            mForceDirty = false;
        }
        if (mLoadBuffer != null) {
            mAlbums = mLoadBuffer;
            mLoadBuffer = null;
            for (MediaSet album : mAlbums) {
                album.reload();
            }
            mDataVersion = nextVersionNumber();
        }
        return mDataVersion;
    }

    @Override
    public void onFutureDone(Future<ArrayList<MediaSet>> future) {
        synchronized (this) {
            if (mLoadTask != future) {
                return; // ignore, wait for the latest task
            }
            mLoadBuffer = future.get();
            mIsLoading = false;
            if (mLoadBuffer == null) {
                mLoadBuffer = new ArrayList<MediaSet>();
            }
        }
        Log.d(TAG, "onFutureDone notifyContentChanged");
        notifyContentChanged(Uri.parse("content://com.android.gallery3d.data.LocalAlbumSet/onFutureDone"));
    }

    // For debug only. Fake there is a ContentObserver.onChange() event.
    void fakeChange() {
        mNotifier.fakeChange();
    }

    // Circular shift the array range from a[i] to a[j] (inclusive). That is,
    // a[i] -> a[i+1] -> a[i+2] -> ... -> a[j], and a[j] -> a[i]
    private static <T> void circularShiftRight(T[] array, int i, int j) {
        T temp = array[j];
        for (int k = j; k > i; k--) {
            array[k] = array[k - 1];
        }
        array[i] = temp;
    }

    private static void arrayMyAlbums(BucketEntry[] entries) {
        int offset = entries.length - 1;
        for (int index = entries.length - 1; index >= 0; index--) {
            if (!entries[index].isMyAlbum()) {
                continue;
            }
            BucketEntry entry = entries[index];
            for (int i = index; i < offset; i++) {
                entries[i] = entries[i + 1];
            }
            entries[offset--] = entry;
        }
    }

    private MediaSet getAllMediaAlbum(DataManager dataManager, int type) {
        synchronized (DataManager.LOCK) {
            switch (type) {
                case MEDIA_TYPE_IMAGE:
                    return dataManager.getMediaSet(AllMediaAlbum.IMAGE_PATH);
                case MEDIA_TYPE_VIDEO:
                    return dataManager.getMediaSet(AllMediaAlbum.VIDEO_PATH);
                case MEDIA_TYPE_ALL:
                    return dataManager.getMediaSet(AllMediaAlbum.PATH);
                default:
                    throw new IllegalArgumentException(String.valueOf(type));
            }
        }
    }

    private MediaSet getCameraAlbum(DataManager dataManager, int type) {
        synchronized (DataManager.LOCK) {
            switch (type) {
                case MEDIA_TYPE_IMAGE:
                    return dataManager.getMediaSet(CameraMergeAlbum.IMAGE_PATH);
                case MEDIA_TYPE_VIDEO:
                    return dataManager.getMediaSet(CameraMergeAlbum.VIDEO_PATH);
                case MEDIA_TYPE_ALL:
                    return dataManager.getMediaSet(CameraMergeAlbum.PATH);
                default:
                    throw new IllegalArgumentException(String.valueOf(type));
            }
        }
    }
}
