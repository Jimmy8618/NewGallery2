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

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Video;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.util.Log;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.app.GalleryStorageUtil;
import com.android.gallery3d.app.StitchingChangeListener;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.BucketHelper.BucketEntry;
import com.android.gallery3d.data.MediaObject.PanoramaSupportCallback;
import com.android.gallery3d.data.MediaSet.ItemConsumer;
import com.android.gallery3d.data.MediaSource.PathId;
import com.android.gallery3d.exif.ExifInterface;
import com.android.gallery3d.exif.ExifTag;
import com.android.gallery3d.picasasource.PicasaSource;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.MediaSetUtils;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.v2.data.AllMediaSource;
import com.android.gallery3d.v2.data.CameraSource;
import com.android.gallery3d.v2.data.DeleteManager;
import com.android.gallery3d.v2.data.HideSource;
import com.android.gallery3d.v2.discover.data.DiscoverSource;
import com.android.gallery3d.v2.trash.TrashManager;
import com.android.gallery3d.v2.trash.data.TrashSource;
import com.android.gallery3d.v2.util.SdCardPermission;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Set;
import java.util.WeakHashMap;

// DataManager manages all media sets and media items in the system.
//
// Each MediaSet and MediaItem has a unique 64 bits id. The most significant
// 32 bits represents its parent, and the least significant 32 bits represents
// the self id. For MediaSet the self id is is globally unique, but for
// MediaItem it's unique only relative to its parent.
//
// To make sure the id is the same when the MediaSet is re-created, a child key
// is provided to obtainSetId() to make sure the same self id will be used as
// when the parent and key are the same. A sequence of child keys is called a
// path. And it's used to identify a specific media set even if the process is
// killed and re-created, so child keys should be stable identifiers.

public class DataManager implements StitchingChangeListener {
    public static final int INCLUDE_IMAGE = 1;
    public static final int INCLUDE_VIDEO = 2;
    public static final int INCLUDE_ALL = INCLUDE_IMAGE | INCLUDE_VIDEO;
    public static final int INCLUDE_LOCAL_ONLY = 4;
    public static final int INCLUDE_LOCAL_IMAGE_ONLY =
            INCLUDE_LOCAL_ONLY | INCLUDE_IMAGE;
    public static final int INCLUDE_LOCAL_VIDEO_ONLY =
            INCLUDE_LOCAL_ONLY | INCLUDE_VIDEO;
    public static final int INCLUDE_LOCAL_ALL_ONLY =
            INCLUDE_LOCAL_ONLY | INCLUDE_IMAGE | INCLUDE_VIDEO;
    public static final int INCLUDE_CAMERA_ONLY = 0x1000;

    // Any one who would like to access data should require this lock
    // to prevent concurrency issue.
    public static final Object LOCK = new Object();

    public static DataManager from(Context context) {
        GalleryApp app = (GalleryApp) context.getApplicationContext();
        return app.getDataManager();
    }

    private static final String TAG = "DataManager";

    // This is the path for the media set seen by the user at top level.
    public static final String TOP_SET_PATH = "/combo/{/local/all,/picasa/all}";

    private static final String TOP_IMAGE_SET_PATH = "/combo/{/local/image,/picasa/image}";

    private static final String TOP_VIDEO_SET_PATH =
            "/combo/{/local/video,/picasa/video}";

    private static final String TOP_LOCAL_SET_PATH = "/local/all";

    private static final String TOP_LOCAL_IMAGE_SET_PATH = "/local/image";

    private static final String TOP_LOCAL_VIDEO_SET_PATH = "/local/video";

    private static final String TOP_CAMERA_SET_PATH = "/camera/all";

    // SPRD: add to support delete local media file
    private static final String ACTION_DELETE_PICTURE = "com.android.gallery3d.action.DELETE_PICTURE";

    public static final Comparator<MediaItem> sDateTakenComparator =
            new DateTakenComparator();

    /* get usb device path*/
    private HashMap<String, String> mOTGdevicesInfos = new HashMap<String, String>();
    private String mCurrentOtgDevicePath;

    private String mBuckPath;
    private Set<String> mBuckIdSet = new HashSet<String>();
    private Set<String> mSelectedAlbums = new HashSet<String>();

    private static class DateTakenComparator implements Comparator<MediaItem> {
        @Override
        public int compare(MediaItem item1, MediaItem item2) {
            return -Utils.compare(item1.getModifiedInSec(), item2.getModifiedInSec());
        }
    }

    private final Handler mDefaultMainHandler;

    private GalleryApp mApplication;
    private int mActiveCount = 0;

    private HashMap<Uri, NotifyBroker> mNotifierMap =
            new HashMap<Uri, NotifyBroker>();


    private HashMap<String, MediaSource> mSourceMap =
            new LinkedHashMap<String, MediaSource>();

    public DataManager(GalleryApp application) {
        mApplication = application;
        mDefaultMainHandler = new Handler(application.getMainLooper());
    }

    public synchronized void initializeSourceMap() {
        if (!mSourceMap.isEmpty()) {
            return;
        }

        // the order matters, the UriSource must come last
        addSource(new LocalSource(mApplication));
        addSource(new PicasaSource(mApplication));
        addSource(new ComboSource(mApplication));
        addSource(new ClusterSource(mApplication));
        addSource(new FilterSource(mApplication));
        addSource(new SecureSource(mApplication));
        addSource(new UriSource(mApplication));
        addSource(new SnailSource(mApplication));
        addSource(new CameraSource(mApplication));
        addSource(new TrashSource(mApplication));
        addSource(new DiscoverSource(mApplication));
        addSource(new AllMediaSource(mApplication));
        addSource(new HideSource(mApplication));

        if (mActiveCount > 0) {
            for (MediaSource source : mSourceMap.values()) {
                source.resume();
            }
        }
    }

    public String getTopSetPath(int typeBits) {

        switch (typeBits) {
            case INCLUDE_IMAGE:
                return TOP_IMAGE_SET_PATH;
            case INCLUDE_VIDEO:
                return TOP_VIDEO_SET_PATH;
            case INCLUDE_ALL:
                return TOP_SET_PATH;
            case INCLUDE_LOCAL_IMAGE_ONLY:
                return TOP_LOCAL_IMAGE_SET_PATH;
            case INCLUDE_LOCAL_VIDEO_ONLY:
                return TOP_LOCAL_VIDEO_SET_PATH;
            case INCLUDE_LOCAL_ALL_ONLY:
                return TOP_LOCAL_SET_PATH;
            case INCLUDE_CAMERA_ONLY:
                return TOP_CAMERA_SET_PATH;
            default:
                throw new IllegalArgumentException();
        }
    }

    // open for debug
    void addSource(MediaSource source) {
        if (source == null) {
            return;
        }
        mSourceMap.put(source.getPrefix(), source);
    }

    // A common usage of this method is:
    // synchronized (DataManager.LOCK) {
    //     MediaObject object = peekMediaObject(path);
    //     if (object == null) {
    //         object = createMediaObject(...);
    //     }
    // }
    public MediaObject peekMediaObject(Path path) {
        return path.getObject();
    }

    public MediaObject getMediaObject(Path path) {
        synchronized (LOCK) {
            MediaObject obj = path.getObject();
            //Log.d(TAG, "getMediaObject path=" + path + ", obj=" + obj);
            if (obj != null) {
                return obj;
            }

            MediaSource source = mSourceMap.get(path.getPrefix());
            if (source == null) {
                Log.w(TAG, "cannot find media source for path: " + path);
                return null;
            }
            //Log.d(TAG, "getMediaObject path=" + path + ", source=" + source);
            try {
                MediaObject object = source.createMediaObject(path);
                if (object == null) {
                    Log.w(TAG, "cannot create media object: " + path);
                }
                return object;
            } catch (Throwable t) {
                Log.w(TAG, "exception in creating media object: " + path, t);
                return null;
            }
        }
    }

    public MediaObject getMediaObject(String s) {
        return getMediaObject(Path.fromString(s));
    }

    public MediaSet getMediaSet(Path path) {
        return (MediaSet) getMediaObject(path);
    }

    public MediaSet getMediaSet(String s) {
        return (MediaSet) getMediaObject(s);
    }

    public MediaSet[] getMediaSetsFromString(String segment) {
        String[] seq = Path.splitSequence(segment);
        int n = seq.length;
        MediaSet[] sets = new MediaSet[n];
        for (int i = 0; i < n; i++) {
            sets[i] = getMediaSet(seq[i]);
        }
        return sets;
    }

    // Maps a list of Paths to MediaItems, and invoke consumer.consume()
    // for each MediaItem (may not be in the same order as the input list).
    // An index number is also passed to consumer.consume() to identify
    // the original position in the input list of the corresponding Path (plus
    // startIndex).
    public void mapMediaItems(ArrayList<Path> list, ItemConsumer consumer,
                              int startIndex) {
        HashMap<String, ArrayList<PathId>> map =
                new HashMap<String, ArrayList<PathId>>();

        // Group the path by the prefix.
        int n = list.size();
        for (int i = 0; i < n; i++) {
            Path path = list.get(i);
            String prefix = path.getPrefix();
            ArrayList<PathId> group = map.get(prefix);
            if (group == null) {
                group = new ArrayList<PathId>();
                map.put(prefix, group);
            }
            group.add(new PathId(path, i + startIndex));
        }

        // For each group, ask the corresponding media source to map it.
        for (Entry<String, ArrayList<PathId>> entry : map.entrySet()) {
            String prefix = entry.getKey();
            MediaSource source = mSourceMap.get(prefix);
            source.mapMediaItems(entry.getValue(), consumer);
        }
    }

    // The following methods forward the request to the proper object.
    public int getSupportedOperations(Path path) {
        return getMediaObject(path).getSupportedOperations();
    }

    public void getPanoramaSupport(Path path, PanoramaSupportCallback callback) {
        getMediaObject(path).getPanoramaSupport(callback);
    }

    public void delete(Path path) {
        getMediaObject(path).delete();
    }

    public void restore(Path path) {
        getMediaObject(path).restore();
    }

    public void rotate(Path path, int degrees) {
        getMediaObject(path).rotate(degrees);
    }

    public Uri getContentUri(Path path) {
        return getMediaObject(path).getContentUri();
    }

    public int getMediaType(Path path) {
        return getMediaObject(path).getMediaType();
    }

    public Path findPathByUri(Uri uri, String type) {
        if (uri == null) {
            return null;
        }
        for (MediaSource source : mSourceMap.values()) {
            Path path = source.findPathByUri(uri, type);
            if (path != null) {
                return path;
            }
        }
        return null;
    }

    /**
     * SPRD:473267 M porting add video entrance & related bug-fix
     * Modify 20150106 of bug 390428,video miss after crop @{
     */
    public Path getDefaultSetOf(boolean flag, Path item, String action) {
        MediaSource source = mSourceMap.get(item.getPrefix());
        return source == null ? null : source.getDefaultSetOf(flag, item, action);
    }

    /**
     * @}
     */
    // Returns number of bytes used by cached pictures currently downloaded.
    public long getTotalUsedCacheSize() {
        long sum = 0;
        for (MediaSource source : mSourceMap.values()) {
            sum += source.getTotalUsedCacheSize();
        }
        return sum;
    }

    // Returns number of bytes used by cached pictures if all pending
    // downloads and removals are completed.
    public long getTotalTargetCacheSize() {
        long sum = 0;
        for (MediaSource source : mSourceMap.values()) {
            sum += source.getTotalTargetCacheSize();
        }
        return sum;
    }

    public void registerChangeNotifier(Uri uri, ChangeNotifier notifier) {
        NotifyBroker broker = null;
        synchronized (mNotifierMap) {
            broker = mNotifierMap.get(uri);
            if (broker == null) {
                broker = new NotifyBroker(mDefaultMainHandler);
                mApplication.getContentResolver()
                        .registerContentObserver(uri, true, broker);
                mNotifierMap.put(uri, broker);
            }
        }
        broker.registerNotifier(notifier);
    }

    public void resume() {
        if (++mActiveCount == 1) {
            for (MediaSource source : mSourceMap.values()) {
                source.resume();
            }
        }
    }

    public void pause() {
        if (--mActiveCount == 0) {
            for (MediaSource source : mSourceMap.values()) {
                source.pause();
            }
        }
    }

    private static class NotifyBroker extends ContentObserver {
        private WeakHashMap<ChangeNotifier, Object> mNotifiers =
                new WeakHashMap<ChangeNotifier, Object>();

        public NotifyBroker(Handler handler) {
            super(handler);
        }

        public synchronized void registerNotifier(ChangeNotifier notifier) {
            mNotifiers.put(notifier, null);
        }

        @Override
        public synchronized void onChange(boolean selfChange, Uri uri) {
            for (ChangeNotifier notifier : mNotifiers.keySet()) {
                notifier.onChange(selfChange, uri);
            }
        }
    }

    @Override
    public void onStitchingQueued(Uri uri) {
        // Do nothing.
    }

    @Override
    public void onStitchingResult(Uri uri) {
        Path path = findPathByUri(uri, null);
        if (path != null) {
            MediaObject mediaObject = getMediaObject(path);
            if (mediaObject != null) {
                mediaObject.clearCachedPanoramaSupport();
            }
        }
    }

    @Override
    public void onStitchingProgress(Uri uri, int progress) {
        // Do nothing.
    }

    /**
     * SPRD: AndroidL Performance issues porting
     * Batch delete local media file, only support local videos and local images @{
     *
     * @param items Want to delete items list.
     * @return Whether success to delete
     */
    public boolean delete(ArrayList<Path> items) {
        Log.d(TAG, "delete multi-item start, size = " + items.size());
        if (items.size() < 1) {
            Log.w(TAG, "The size of delete items must greater than 0!");
            return true;
        }
        GalleryUtils.assertNotInRenderThread();
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_FOREGROUND);
        LocalMediaItem lmItem;
        StringBuilder ids = new StringBuilder();
        boolean isSupportTrash = GalleryUtils.isSupportRecentlyDelete();
        ArrayList<LocalMediaItem> willBeDeletedItems = new ArrayList<>();
        Log.d(TAG, "delete: START build delete list.");
        while (!items.isEmpty()) {
            lmItem = (LocalMediaItem) getMediaObject(items.remove(items.size() - 1));
            if (null != lmItem) {
                if (MediaObject.MEDIA_TYPE_IMAGE_BURST_COVER == lmItem.getMediaType()) {
                    if (isSupportTrash) {
                        TrashManager.getDefault().recycleBurstItem((LocalImage) lmItem);
                    }
                    lmItem.delete();
                } else {
                    if (ids.length() > 0) {
                        ids.append(',');
                    }
                    ids.append(lmItem.id);
                    //将需要删除的文件路径放入这个 willBeDeletedItems 中, 一次性放入DeleteManager中删除
                    willBeDeletedItems.add(lmItem);
                }
            } else {
                Log.w(TAG, "Fail to delete, object is null!" + items.size());
                return false;
            }
        }
        Log.d(TAG, "delete: END build delete list.");
        if (ids.length() > 0) {
            StringBuilder sb = new StringBuilder("_id in (");
            sb.append(ids).append(")");
            Log.d(TAG, "delete: call MediaProvider to update is_pending = 1, " + sb);
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            mApplication.getContentResolver().update(MediaStore.Files.getContentUri("external"),
                    values, sb.toString(), null);
            Log.d(TAG, "delete: MediaProvider update finish");
            //先批量update数据库, 然后调用此方法, 主动地更新一下数据
            DeleteManager.getDefault().onContentDirty();
        }

        if (!willBeDeletedItems.isEmpty()) {
            DeleteManager.getDefault().addMediaInfo(willBeDeletedItems);
        }
        if (items.size() > 0) {
            delete(items);
        }
        mApplication.getDataManager().broadcastLocalDeletion();
        Log.d(TAG, "delete multi-item end.");
        return true;
    }

    public void batchDelete(ArrayList<Path> items) {
        Log.d(TAG, "batchDelete start !");
        GalleryUtils.assertNotInRenderThread();
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        for (Path path : items) {
            MediaObject mo = getMediaObject(path);
            if (null == mo) {
                Log.d(TAG, "the MediaObject of path = " + path + " is null");
                return;
            }
            Uri baseUri;
            if (mo instanceof LocalImage) {
                baseUri = Images.Media.EXTERNAL_CONTENT_URI;
            } else if (mo instanceof LocalVideo) {
                baseUri = Video.Media.EXTERNAL_CONTENT_URI;
            } else {
                Log.d(TAG, "is not LocalImage or LocalVideo, cancel delete path = " + path);
                return;
            }
            LocalMediaItem localItem = (LocalMediaItem) mo;
            ContentProviderOperation build = null;
            if (MediaObject.MEDIA_TYPE_IMAGE_BURST_COVER == localItem.getMediaType()) {
                long dateTaken = localItem.getDateInMs();
                String burstTitlePrefix = localItem.caption.substring(0, localItem.caption.lastIndexOf("_"));
                Log.d(TAG, "delete burst image : " +
                        String.format("(file_flag=%d or file_flag=%d) and datetaken=%d", LocalImage.IMG_TYPE_MODE_BURST_COVER, LocalImage.IMG_TYPE_MODE_BURST, dateTaken));
                build = ContentProviderOperation.newDelete(baseUri).
                        withSelection("(file_flag=?" + " or file_flag=?) and " + ImageColumns.DATE_TAKEN + "=?",
                                new String[]{String.valueOf(LocalImage.IMG_TYPE_MODE_BURST_COVER), String.valueOf(LocalImage.IMG_TYPE_MODE_BURST), String.valueOf(dateTaken)})
                        .build();
            } else {
                Uri uri = baseUri.buildUpon().appendPath(String.valueOf(localItem.id)).build();
                build = ContentProviderOperation.newDelete(uri).build();
            }

            if (build != null) {
                ops.add(build);
            }
        }

        try {
            mApplication.getContentResolver().applyBatch(MediaStore.AUTHORITY, ops);
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.d(TAG, "batchDelete end !");
    }

    public void batchRotate(ArrayList<Path> items, int degrees) {
        Log.d(TAG, "batchRotate start !");
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        int index = 0;
        for (Path path : items) {
            LocalImage item = null;
            MediaObject mediaObject = getMediaObject(path);
            if (mediaObject instanceof LocalImage) {
                item = (LocalImage) mediaObject;
            }
            if (item == null) {
                Log.d(TAG, "can't rotate item, path = " + path);
                continue;
            }
            GalleryUtils.assertNotInRenderThread();
            Uri baseUri = Images.Media.EXTERNAL_CONTENT_URI;
            ContentValues values = new ContentValues();
            int rotation = (item.getRotation() + degrees) % 360;
            if (rotation < 0) {
                rotation += 360;
            }

            if (item.mimeType.equalsIgnoreCase("image/jpeg")) {
                ExifInterface exifInterface = new ExifInterface();
                ExifTag tag = exifInterface.buildTag(ExifInterface.TAG_ORIENTATION,
                        ExifInterface.getOrientationValueForRotation(rotation));
                if (tag != null) {
                    exifInterface.setTag(tag);
                    ParcelFileDescriptor fd1 = null;
                    ParcelFileDescriptor fd2 = null;
                    try {
                        if (!GalleryStorageUtil.isInInternalStorage(item.filePath)) {
                            fd1 = SdCardPermission.createExternalFileDescriptor(item.filePath, "rw");
                        }
                        if (Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.Q) {
                            exifInterface.reWriteExif(mApplication.getContentResolver(), item.getContentUri());
                        } else {
                            exifInterface.forceRewriteExif(item.filePath, fd1);
                        }
                        File file = new File(item.filePath);
                        item.fileSize = file.length();
                        long lastModifiedSeconds = file.lastModified() / 1000;
                        values.put(Images.Media.SIZE, item.fileSize);
                        values.put(Images.Media.DATE_MODIFIED, lastModifiedSeconds);
                    } catch (FileNotFoundException e) {
                        Log.w(TAG, "cannot find file to set exif: " + item.filePath, e);
                    } catch (IOException e) {
                        Log.w(TAG, "cannot set exif data: " + item.filePath, e);
                        if (!GalleryStorageUtil.isInInternalStorage(item.filePath)) {
                            fd2 = SdCardPermission.createExternalFileDescriptor(item.filePath, "rw");
                        }
                        setExifRotation(item.filePath, fd2, rotation);
                    } finally {
                        Utils.closeSilently(fd1);
                        Utils.closeSilently(fd2);
                    }
                } else {
                    Log.w(TAG, "Could not build tag: " + ExifInterface.TAG_ORIENTATION);
                }
            }

            item.rotation = rotation;
            values.put(Images.Media.ORIENTATION, rotation);
            ContentProviderOperation build = ContentProviderOperation.newUpdate(baseUri)
                    .withSelection("_id=?", new String[]{String.valueOf(item.id)})
                    .withValues(values)
                    .build();
            ops.add(build);

            if (mBatchListener != null) {
                mBatchListener.updateIndex(++index);
            }
        }

        try {
            mApplication.getContentResolver().applyBatch(MediaStore.AUTHORITY, ops);
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.d(TAG, "batchRotate end !");
    }

    public interface BatchListener {
        void updateIndex(int index);
    }

    private BatchListener mBatchListener;

    public void setBatchRotateListener(BatchListener batchListener) {
        mBatchListener = batchListener;
    }

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

    // used to update the thumbnail shown in the camera app.
    public void broadcastLocalDeletion() {
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(
                mApplication.getAndroidContext());
        Intent intent = new Intent(ACTION_DELETE_PICTURE);
        manager.sendBroadcast(intent);
    }

    /* get usb device path*/
    public void setOtgDeviceInfos(HashMap<String, String> devicelist) {
        mOTGdevicesInfos = devicelist;
    }

    public HashMap<String, String> getOtgDeviceInfos() {
        return mOTGdevicesInfos;
    }

    public void setOtgDeviceCurrentPath(String currentPath) {
        mCurrentOtgDevicePath = currentPath;
    }

    public String getOtgDeviceCurrentPath() {
        return mCurrentOtgDevicePath;
    }
    /* get usb device path*/

    /* add external and intenal bucketId*/
    public Set<String> getAllBucketId() {
        Set<String> allBucketId = new HashSet<String>();
        /* internal device */
        if (MediaSetUtils.CAMERA_BUCKET_ID != -1) {
            allBucketId.add(String.valueOf(MediaSetUtils.CAMERA_BUCKET_ID));
            allBucketId.add(String.valueOf(MediaSetUtils.DOWNLOAD_BUCKET_ID));
            allBucketId.add(String.valueOf(MediaSetUtils.SNAPSHOT_BUCKET_ID));
            allBucketId.add(String.valueOf(MediaSetUtils.BLUETOOTH_BUCKET_ID));
        }

        /* external device */
        if (MediaSetUtils.EXTERNAL_CAMERA_BUCKET_ID != -1) {
            allBucketId.add(String.valueOf(MediaSetUtils.EXTERNAL_CAMERA_BUCKET_ID));
            allBucketId.add(String.valueOf(MediaSetUtils.EXTERNAL_DOWNLOAD_BUCKET_ID));
            allBucketId.add(String.valueOf(MediaSetUtils.EXTERNAL_SNAPSHOT_BUCKET_ID));
            allBucketId.add(String.valueOf(MediaSetUtils.EXTERNAL_BLUETOOTH_BUCKET_ID));
        }

        return allBucketId;
    }

    public void setAllBucketEntry(Path path, BucketEntry[] entries) {
        mBuckIdSet.clear();
        for (BucketEntry entry : entries) {
            mBuckIdSet.add(String.valueOf(path.getChild(entry.bucketId)));
        }
        mBuckPath = path.toString();
        //Log.d(TAG, "setAllBucketEntry mBuckPath=" + mBuckPath + ", mBuckIdSet=" + mBuckIdSet);
    }

    public Set<String> getAllBucketEntry() {
        return mBuckIdSet;
    }

    public static Set<String> getLocalAlbumBuckedIdSet() {
        Set<String> set = new HashSet<String>();
        /* internal device */
        if (MediaSetUtils.CAMERA_BUCKET_ID != -1) {
            set.add(String.valueOf(MediaSetUtils.CAMERA_BUCKET_ID));
            set.add(String.valueOf(MediaSetUtils.DOWNLOAD_BUCKET_ID));
            set.add(String.valueOf(MediaSetUtils.SNAPSHOT_BUCKET_ID));
            set.add(String.valueOf(MediaSetUtils.BLUETOOTH_BUCKET_ID));
        }
        /* external device */
        if (MediaSetUtils.EXTERNAL_CAMERA_BUCKET_ID != -1) {
            set.add(String.valueOf(MediaSetUtils.EXTERNAL_CAMERA_BUCKET_ID));
            set.add(String.valueOf(MediaSetUtils.EXTERNAL_DOWNLOAD_BUCKET_ID));
            set.add(String.valueOf(MediaSetUtils.EXTERNAL_SNAPSHOT_BUCKET_ID));
            set.add(String.valueOf(MediaSetUtils.EXTERNAL_BLUETOOTH_BUCKET_ID));
        }
        return set;
    }

    public String getBuckPath() {
        return mBuckPath;
    }

    public Handler getDefaultMainHandler() {
        return mDefaultMainHandler;
    }

    public void moveOutThings(Path path) {
        getMediaObject(path).moveOutThings();
    }

    public void moveOutPeople(Path path) {
        getMediaObject(path).moveOutPeople();
    }


    private Future<Boolean> mBuildFuture;
    private BuildDeleteListTask mBuildTask;

    public boolean deleteUntilReady() {
        if (mBuildTask == null) {
            Log.d(TAG, "deleteUntilReady: mBuildTask is null!");
            return false;
        }
        Log.d(TAG, "deleteUntilReady: start until delete list is ready.");
        mBuildTask.waitReady();
        int num = mBuildTask.getDeleteItemSize();
        Log.d(TAG, "to be deleted item number: " + num);
        if (num < 1) {
            Log.w(TAG, "The size of delete items must greater than 0!");
            return true;
        }
        GalleryUtils.assertNotInRenderThread();
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_FOREGROUND);
        boolean isSupportTrash = GalleryUtils.isSupportRecentlyDelete();
        Log.d(TAG, "deleteUntilReady: isSupportTrash = " + isSupportTrash);

        long t = System.currentTimeMillis();
        ArrayList<LocalMediaItem> externalItems = new ArrayList<>();
        while (mBuildTask.getDeleteItemSize() > 0) {
            LocalMediaItem item = mBuildTask.peekLastItem();
            boolean isInternalFile = GalleryStorageUtil.isInInternalStorage(item.getFilePath());
            if (MediaObject.MEDIA_TYPE_IMAGE_BURST_COVER == item.getMediaType()) {
                if (isSupportTrash) {
                    TrashManager.getDefault().recycleBurstItem((LocalImage) item);
                }
                item.delete();
            } else {
                if (isInternalFile) {
                    if (isSupportTrash && !item.mIsDrmFile) {
                        TrashManager.getDefault().recycle(item);
                    }
                } else {
                    externalItems.add(item);
                }
            }
        }
        if (!externalItems.isEmpty()) {
            DeleteManager.getDefault().addMediaInfo(externalItems);
        }
        Log.d(TAG, "deleteUntilReady: perform to delete or recycle items. cost " + (System.currentTimeMillis() - t) + "ms");

        //delete internal and external items from media database
        if (mBuildTask.getDeleteItemIds().length() > 0) {
            StringBuilder sb = new StringBuilder("_id in (");
            sb.append(mBuildTask.getDeleteItemIds()).append(")");
            Log.d(TAG, "deleteUntilReady: call MediaProvider to delete " + sb);
            mApplication.getContentResolver().delete(MediaStore.Files.getContentUri("external"), sb.toString(), null);
            Log.d(TAG, "deleteUntilReady: MediaProvider delete end");
            //先批量删除数据库, 然后调用此方法, 主动地更新一下数据
            DeleteManager.getDefault().onContentDirty();
        }
        mBuildTask = null;
        mBuildFuture = null;
        mApplication.getDataManager().broadcastLocalDeletion();
        Log.d(TAG, "deleteUntilReady: end.");
        return true;
    }

    private class BuildDeleteListTask implements ThreadPool.Job<Boolean> {
        ArrayList<Path> items;
        private ArrayList<LocalMediaItem> needDeleteItems = new ArrayList<>();
        private StringBuilder deleteItemIds = new StringBuilder();
        private ConditionVariable deleteListReady = new ConditionVariable(false);

        public BuildDeleteListTask(ArrayList<Path> items) {
            this.items = new ArrayList<>(items);
            synchronized (deleteListReady) {
                deleteListReady.close();
            }
        }

        public void waitReady() {
            synchronized (deleteListReady) {
                Log.d(TAG, "waitReady: " + deleteListReady);
                deleteListReady.block();
            }
        }

        public int getDeleteItemSize() {
            return needDeleteItems.size();
        }

        public LocalMediaItem peekLastItem() {
            return needDeleteItems.remove(needDeleteItems.size() - 1);
        }

        public String getDeleteItemIds() {
            return deleteItemIds.toString();
        }

        private void clearData() {
            needDeleteItems.clear();
            deleteItemIds.delete(0, deleteItemIds.length() - 1);
        }

        @Override
        public Boolean run(ThreadPool.JobContext jc) {
            Log.d(TAG, "BuildDeleteListTask.run: B items.size = " + items.size());
            if (items.size() < 1) {
                Log.w(TAG, "BuildDeleteListTask.run: E The size of delete items must greater than 0!");
                return false;
            }
            GalleryUtils.assertNotInRenderThread();

            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_FOREGROUND);
            LocalMediaItem lmItem;
            boolean result = true;
            while (!items.isEmpty()) {
                if (jc.isCancelled()) {
                    Log.d(TAG, "BuildDeleteListTask.run: E canceled task, remain " + items.size() + " items");
                    clearData();
                    result = false;
                    break;
                }
                lmItem = (LocalMediaItem) getMediaObject(items.remove(items.size() - 1));
                if (null != lmItem) {
                    needDeleteItems.add(lmItem);
                    if (MediaObject.MEDIA_TYPE_IMAGE_BURST_COVER != lmItem.getMediaType()) {
                        if (deleteItemIds.length() > 0) {
                            deleteItemIds.append(',');
                        }
                        deleteItemIds.append(lmItem.id);
                    }
                } else {
                    Log.w(TAG, "BuildDeleteListTask.run: E Fail to delete, object is null! remain item size: " + items.size());
                    clearData();
                    result = false;
                    break;
                }
            }
            synchronized (deleteListReady) {
                Log.d(TAG, "BuildDeleteListTask.run open condition " + deleteListReady);
                deleteListReady.open();
            }
            Log.d(TAG, "BuildDeleteListTask.run: E deleteItemIds = " + deleteItemIds);
            return result;
        }
    }

    public void buildDeleteList(ArrayList<Path> items) {
        Log.d(TAG, "buildDeleteList: B");
        abortBuildDeleteItemTask();
        mBuildTask = new BuildDeleteListTask(items);
        mBuildFuture = GalleryAppImpl.getApplication().getThreadPool().submit(mBuildTask);
        Log.d(TAG, "buildDeleteList: E mBuildTask=" + mBuildTask + ", mBuildFuture=" + mBuildFuture);
    }

    public void abortBuildDeleteItemTask() {
        Log.d(TAG, "abortBuildDeleteItemTask: mBuildFuture=" + mBuildFuture + ", mBuildTask=" + mBuildTask);
        if (mBuildFuture != null) {
            Log.d(TAG, "abortBuildDeleteItemTask: isDone ? " + mBuildFuture.isDone());
            if (!mBuildFuture.isDone()) {
                mBuildFuture.cancel();
            }
            mBuildFuture = null;
        }

        if (mBuildTask != null) {
            mBuildTask = null;
        }
    }

    private final WeakHashMap<ContentListener, Object> mContentListeners = new WeakHashMap<>();

    public void registerContentListener(ContentListener listener) {
        synchronized (mContentListeners) {
            mContentListeners.put(listener, null);
        }
    }

    public synchronized void onContentDirty() {
        for (ContentListener listener : mContentListeners.keySet()) {
            listener.onContentDirty(null);
        }
    }
}
