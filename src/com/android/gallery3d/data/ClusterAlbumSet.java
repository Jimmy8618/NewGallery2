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

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.android.gallery3d.app.GalleryApp;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

public class ClusterAlbumSet extends MediaSet implements ContentListener {
    @SuppressWarnings("unused")
    private static final String TAG = "ClusterAlbumSet";
    private GalleryApp mApplication;
    private MediaSet mBaseSet;
    private int mKind;
    private ArrayList<ClusterAlbum> mAlbums = new ArrayList<ClusterAlbum>();
    private boolean mFirstReloadDone;
    // SPRD: fix bug 381452, album set not sync
    public int currentIndexOfSet;
    private Object Lock = new Object();
    // SPRD:fix bug 527446,if system language changed,gallery language should change in time
    private String mCurrentLanguage;

    public ClusterAlbumSet(Path path, GalleryApp application,
                           MediaSet baseSet, int kind) {
        super(path, INVALID_DATA_VERSION);
        mApplication = application;
        mBaseSet = baseSet;
        mKind = kind;
        baseSet.addContentListener(this);
        // SPRD:fix bug 527446,if system language changed,gallery language should change in time
        mCurrentLanguage = Locale.getDefault().getLanguage().toString();
    }

    @Override
    public MediaSet getSubMediaSet(int index) {
        if (index >= mAlbums.size()) {
            return null;
        }
        return mAlbums.get(index);
    }

    @Override
    public int getSubMediaSetCount() {
        return mAlbums.size();
    }

    @Override
    public String getName() {
        return mBaseSet.getName();
    }

    @Override
    public long reload() {
        /* SPRD: fix bug 381452, album set not sync @{ */
        if (mBaseSet instanceof ClusterAlbum) {
            mBaseSet.offsetInStack = offsetInStack + 1;
        }
        /* @} */
        // SPRD: bug 521688, Add synchronization to prevent code execute repeatedly
        synchronized (this) {
            if (mBaseSet.reload() > mDataVersion) {
                if (mFirstReloadDone) {
                    // SPRD: Modify 20160106 for bug520818, Cluster cannot be deleted sometimes.
                    // use different update logic for Cluster delete operation. @{
                    if (!isDeleteOperation) {
                        if (!SprdMediaLoader.RE_QUERY) {
                            updateClustersContents();
                        } else {
                            updateClustersContents2();
                        }
                    } else {
                        // add for Cluster delete operation.
                        updateClustersContentsForDelete();
                    }
                    // @}
                } else {
                    updateClusters();
                    mFirstReloadDone = true;
                }
                mDataVersion = nextVersionNumber();
            } else {
                // SPRD:fix bug 527446,if system language changed,gallery language should change in time
                checkLanguageChange();
            }
        }
        /* SPRD: fix bug 381452, album set not sync @{ */
        mCurrentClusterAlbum = null;
        offsetInStack = 0;
        currentIndexOfSet = -1;
        /* @} */
        return mDataVersion;
    }

    /* SPRD:fix bug 527446,if system language changed,gallery language should change in time @{*/
    private void checkLanguageChange() {
        String language = Locale.getDefault().getLanguage().toString();
        if (language != null && !language.equals(mCurrentLanguage)) {
            updateClusters();
            mCurrentLanguage = language;
            mDataVersion = nextVersionNumber();
        }
    }
    /* @} */

    @Override
    public void onContentDirty(Uri uri) {
        notifyContentChanged(uri);
    }

    private void updateClusters() {
        mAlbums.clear();
        Clustering clustering;
        Context context = mApplication.getAndroidContext();
        Log.d(TAG, "updateClusters mKind=" + mKind);
        switch (mKind) {
            case ClusterSource.CLUSTER_ALBUMSET_ALL:
                clustering = new TimeClustering(context);
                break;
            case ClusterSource.CLUSTER_ALBUMSET_PHOTO:
                //clustering = new LocationClustering(context);
                clustering = new LocalPhotoClustering(context);
                break;
            case ClusterSource.CLUSTER_ALBUMSET_TAG:
                clustering = new TagClustering(context);
                break;
            case ClusterSource.CLUSTER_ALBUMSET_VIDEO:
                //clustering = new FaceClustering(context);
                clustering = new VideoClustering(context);
                break;
            default: /* CLUSTER_ALBUMSET_SIZE */
                clustering = new SizeClustering(context);
                break;
        }
        Log.d(TAG, "updateClusters.");
        clustering.run(mBaseSet);
        int n = clustering.getNumberOfClusters();
        DataManager dataManager = mApplication.getDataManager();
        for (int i = 0; i < n; i++) {
            Path childPath;
            String childName = clustering.getClusterName(i);
            String date = clustering.getClusterDate(i);
            if (mKind == ClusterSource.CLUSTER_ALBUMSET_TAG) {
                childPath = mPath.getChild(Uri.encode(childName));
            } else if (mKind == ClusterSource.CLUSTER_ALBUMSET_SIZE) {
                long minSize = ((SizeClustering) clustering).getMinSize(i);
                childPath = mPath.getChild(minSize);
            } else {
                String path = mPath.getChild(i).toString();
                path = path.substring(0, path.lastIndexOf("/") + 1) + date;
                childPath = Path.fromString(path);
            }

            ClusterAlbum album;
            synchronized (DataManager.LOCK) {
                album = (ClusterAlbum) dataManager.peekMediaObject(childPath);
                if (album == null) {
                    album = new ClusterAlbum(childPath, dataManager, this);
                }
            }
            album.setMediaItems(clustering.getCluster(i));
            // SPRD: Modify 20160106 for bug520818, Cluster cannot be deleted sometimes @{
            // do updateClusters, means not in cluster album delete
            // operation, so should set isDeleteOperation = false.
            // and set mNumberOfDeletedImage = 0;
            isDeleteOperation = false;
            album.setNumberOfDeletedImage(0);
            // @}
            album.setName(childName);
            album.setCoverMediaItem(clustering.getClusterCover(i));
            Log.d(TAG, "updateClusters childName=" + childName + ", album.getMediaItemCount()=" + album
                    .getMediaItemCount());
            mAlbums.add(album);
        }
    }

    private void updateClustersContents() {
        final HashSet<Path> existing = new HashSet<Path>();
        // SPRD: fix bug 381452, album set not sync
        final HashMap<Path, MediaItem> existingMediaItem = new HashMap<Path, MediaItem>();
        Log.d(TAG, "updateClustersContents mBaseSet=" + mBaseSet);
        MediaSet.ItemConsumer consumer = new MediaSet.ItemConsumer() {
            @Override
            public void consume(int index, MediaItem item) {
                existing.add(item.getPath());
                // SPRD: fix bug 381452, album set not sync
                existingMediaItem.put(item.getPath(), item);
            }
        };
        switch (mKind) {
            case ClusterSource.CLUSTER_ALBUMSET_PHOTO:
                mBaseSet.enumerateLocalPhoto(consumer);
                break;
            case ClusterSource.CLUSTER_ALBUMSET_VIDEO:
                mBaseSet.enumerateVideoItems(consumer);
                break;
            default:
                mBaseSet.enumerateTotalMediaItems(consumer);
                break;
        }
        /*SPRD :fix bug 512098,JavaCrash,log:java.lang.IndexOutOfBoundsException@{*/
        Log.d(TAG, "synchronized(mAlbums)");
        synchronized (Lock) {
            int n = mAlbums.size();
            /* SPRD: fix bug 381452, album set not sync @{ */
            HashSet<Path> oldPathHashSet = new HashSet<Path>();
            int deletedItem = 0;
            /* @} */
            // The loop goes backwards because we may remove empty albums from
            // mAlbums.
            for (int i = n - 1; i >= 0; i--) {
                ArrayList<Path> oldPaths = mAlbums.get(i).getMediaItems();
                ArrayList<Path> newPaths = new ArrayList<Path>();
                int m = oldPaths.size();
                for (int j = 0; j < m; j++) {
                    Path p = oldPaths.get(j);
                    // SPRD: fix bug 381452, album set not sync
                    oldPathHashSet.add(p);
                    if (existing.contains(p)) {
                        newPaths.add(p);
                    } else {
                        // SPRD: fix bug 381452, album set not sync
                        deletedItem++;
                    }
                }
                mAlbums.get(i).setMediaItems(newPaths);
                if (deletedItem > 0) {
                    mAlbums.get(i).nextVersion();
                }
                if (newPaths.isEmpty()) {
                    mAlbums.remove(i);
                }
            }
            Log.d(TAG, "updateClustersContents existing.size():" + existing.size()
                    + " deletedItem:" + deletedItem + " oldPathHashSet.size():"
                    + oldPathHashSet.size());
            /* SPRD: fix bug 381452,bug 513585 album set not sync @{ */
            if (existing.size() + deletedItem > oldPathHashSet.size()) {
                if (offsetInStack >= 1) {
                    ArrayList<Path> addedPath = new ArrayList<Path>();
                    ArrayList<Path> mNewPath = new ArrayList<Path>(existing);
                    int sizeOfExistingPath = mNewPath.size();
                    for (int i = 0; i < sizeOfExistingPath; i++) {
                        if (!oldPathHashSet.contains(mNewPath.get(i))) {
                            addedPath.add(mNewPath.get(i));
                        }
                    }
                    setCurrentIndexOfSet();
                    try {
                        updateAlbumInClusters(addedPath, existingMediaItem);
                    } catch (ConcurrentModificationException e) {
                        e.printStackTrace();
                    }
                } else {
                    updateClusters();
                    for (int i = mAlbums.size() - 1; i >= 0; i--) {
                        mAlbums.get(i).nextVersion();
                    }
                }
            }
        }
        /* @} */
    }

    public void setCurrentIndexOfSet() {
        int albumSize = mAlbums.size();
        if (mCurrentClusterAlbum != null) {
            boolean hasFindSet = false;
            for (int i = 0; i < albumSize; i++) {
                if (mAlbums.get(i) == mCurrentClusterAlbum) {
                    currentIndexOfSet = i;
                    hasFindSet = true;
                    break;
                }
            }
            if (!hasFindSet) {
                currentIndexOfSet = 0;
            }
        }
    }

    private void updateAlbumInClusters(ArrayList<Path> paths,
                                       HashMap<Path, MediaItem> exisingMediaItem) {
        if (mAlbums != null) {
            if (currentIndexOfSet < mAlbums.size() && currentIndexOfSet >= 0) {
                try {
                    MediaSet mMediaSet = mApplication.getDataManager().getMediaSet(
                            mAlbums.get(currentIndexOfSet).mPath);
                    ArrayList<MediaItem> mOldItem = mMediaSet.getMediaItem(0,
                            mMediaSet.getMediaItemCount());
                    int sizeOfOldMediaItems = mOldItem.size();
                    int sizeofAddPaths = paths.size();
                    for (int j = 0; j < sizeofAddPaths; j++) {
                        MediaItem item = exisingMediaItem.get(paths.get(j));
                        int k = 0;
                        for (; k < sizeOfOldMediaItems; k++) {
                            if (item.getDateInMs() == mOldItem.get(k).getDateInMs()) {
                                mAlbums.get(currentIndexOfSet).addMediaItems(paths.get(j), k);
                                break;
                            }
                        }
                        if (k == sizeOfOldMediaItems) {
                            mAlbums.get(currentIndexOfSet).addMediaItems(paths.get(j), 0);
                        }
                    }
                } catch (OutOfMemoryError e) {
                    Log.w(TAG, " maybe sizeOldMediaItems is too big:" + e);
                }
            }
        }
    }
    /* @} */

    /*
     * SPRD: Modify 20160106 for bug520818, Cluster cannot be deleted sometimes,
     * use different update logic for Cluster delete operation. @{
     */
    private static boolean isDeleteOperation = false;

    // set and get ClusterDeleteOperation.
    public static void setClusterDeleteOperation(boolean deleteOperation) {
        Log.d(TAG, "<setClusterDeleteOperation>setClusterDeleteOperation isDeleteOperation: "
                + deleteOperation);
        isDeleteOperation = deleteOperation;
    }

    public static boolean getClusterDeleteOperation() {
        Log.d(TAG, "<getClusterDeleteOperation> isDeleteOperation: " + isDeleteOperation);
        return isDeleteOperation;
    }

    private void updateClustersContentsForDelete() {
        int n = mAlbums.size();
        for (int i = n - 1; i >= 0; i--) {
            // If the number of deleted image equal with albums size means there is not image in this album.
            // So then delete the album.
            if (mAlbums.get(i).getNumberOfDeletedImage() == mAlbums.get(i)
                    .getMediaItemCount()) {
                mAlbums.get(i).setNumberOfDeletedImage(0);
                mAlbums.remove(i);
            }
        }
    }
    /* @} */

    /*{@数据优化 Begin*/
    private class SmallItem {
        private Path mPath;
        private long mDateInMs;

        public SmallItem(Path path, long dateInMs) {
            mPath = path;
            mDateInMs = dateInMs;
        }

        public Path getPath() {
            return mPath;
        }

        public long getDateInMs() {
            return mDateInMs;
        }
    }

    private void updateClustersContents2() {
        Log.d(TAG, "updateClustersContents2 B.");
        final HashSet<Path> existing = new HashSet<Path>();
        final HashMap<Path, SmallItem> existingSmallItem = new HashMap<Path, SmallItem>();
        Log.d(TAG, "updateClustersContents2 enumerate items B.");
        SprdMediaLoader.MediaConsumer consumer = new SprdMediaLoader.MediaConsumer() {
            @Override
            public void consume(Path path, long dateInMs, long modifyDateInSec, double latitude, double longtitude) {
                existing.add(path);
                if (dateInMs == 0) {
                    dateInMs = modifyDateInSec * 1000;
                }
                existingSmallItem.put(path, new SmallItem(path, dateInMs));
            }
        };
        switch (mKind) {
            case ClusterSource.CLUSTER_ALBUMSET_PHOTO:
                SprdMediaLoader.enumerateLocalImages(mApplication.getAndroidContext(), consumer);
                break;
            case ClusterSource.CLUSTER_ALBUMSET_VIDEO:
                SprdMediaLoader.enumerateTotalVideos(mApplication.getAndroidContext(), consumer);
                break;
            default:
                SprdMediaLoader.enumerateTotalMedias(mApplication.getAndroidContext(), consumer);
                break;
        }
        Log.d(TAG, "updateClustersContents2 enumerate items E.");
        synchronized (Lock) {
            int n = mAlbums.size();
            HashSet<Path> oldPathHashSet = new HashSet<Path>();
            int deletedItem = 0;
            for (int i = n - 1; i >= 0; i--) {
                ArrayList<Path> oldPaths = mAlbums.get(i).getMediaItems();
                ArrayList<Path> newPaths = new ArrayList<Path>();
                int m = oldPaths.size();
                for (int j = 0; j < m; j++) {
                    Path p = oldPaths.get(j);
                    oldPathHashSet.add(p);
                    if (existing.contains(p)) {
                        newPaths.add(p);
                    } else {
                        deletedItem++;
                    }
                }
                mAlbums.get(i).setMediaItems(newPaths);
                if (deletedItem > 0) {
                    mAlbums.get(i).nextVersion();
                }
                if (newPaths.isEmpty()) {
                    mAlbums.remove(i);
                }
            }

            Log.d(TAG, "updateClustersContents2 existing size = " + existing.size() + " deletedItem = " + deletedItem + " oldPathHashSet size = " + oldPathHashSet.size());
            if (existing.size() + deletedItem > oldPathHashSet.size()) {
                Log.d(TAG, "updateClustersContents2 (existing.size + deletedItem) > oldPathHashSet.size");
                if (false /*offsetInStack >= 1 && mAlbums.size() != 0*/) {
                    ArrayList<Path> addedPath = new ArrayList<Path>();
                    ArrayList<Path> mNewPath = new ArrayList<Path>(existing);
                    int sizeOfExistingPath = mNewPath.size();
                    for (int i = 0; i < sizeOfExistingPath; i++) {
                        if (!oldPathHashSet.contains(mNewPath.get(i))) {
                            addedPath.add(mNewPath.get(i));
                        }
                    }
                    setCurrentIndexOfSet();
                    try {
                        updateAlbumInClusters2(addedPath, existingSmallItem);
                    } catch (ConcurrentModificationException e) {
                        e.printStackTrace();
                    }
                } else {
                    updateClusters();
                    for (int i = mAlbums.size() - 1; i >= 0; i--) {
                        mAlbums.get(i).nextVersion();
                    }
                }
            }

            Log.d(TAG, "updateClustersContents2 E.");
        }
    }

    private void updateAlbumInClusters2(ArrayList<Path> paths,
                                        HashMap<Path, SmallItem> existingSmallItem) {
        if (mAlbums != null) {
            if (currentIndexOfSet < mAlbums.size() && currentIndexOfSet >= 0) {
                try {
                    MediaSet mMediaSet = mApplication.getDataManager().getMediaSet(
                            mAlbums.get(currentIndexOfSet).mPath);
                    ArrayList<MediaItem> mOldItem = mMediaSet.getMediaItem(0,
                            mMediaSet.getMediaItemCount());
                    int sizeOfOldMediaItems = mOldItem.size();
                    int sizeofAddPaths = paths.size();
                    for (int j = 0; j < sizeofAddPaths; j++) {
                        SmallItem item = existingSmallItem.get(paths.get(j));
                        int k = 0;
                        for (; k < sizeOfOldMediaItems; k++) {
                            if (item.getDateInMs() == mOldItem.get(k).getDateInMs()) {
                                mAlbums.get(currentIndexOfSet).addMediaItems(paths.get(j), k);
                                break;
                            }
                        }
                        if (k == sizeOfOldMediaItems) {
                            mAlbums.get(currentIndexOfSet).addMediaItems(paths.get(j), 0);
                        }
                    }
                } catch (OutOfMemoryError e) {
                    Log.w(TAG, " maybe sizeOldMediaItems is too big:" + e);
                }
            }
        }
    }
    /*数据优化 End@}*/
}
