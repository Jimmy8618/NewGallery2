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

import java.util.ArrayList;

public class ClusterAlbum extends MediaSet implements ContentListener {
    @SuppressWarnings("unused")
    private static final String TAG = "ClusterAlbum";
    private ArrayList<Path> mPaths = new ArrayList<Path>();
    private String mName = "";
    private DataManager mDataManager;
    private MediaSet mClusterAlbumSet;
    private MediaItem mCover;

    public ClusterAlbum(Path path, DataManager dataManager,
                        MediaSet clusterAlbumSet) {
        super(path, nextVersionNumber());
        mDataManager = dataManager;
        mClusterAlbumSet = clusterAlbumSet;
        mClusterAlbumSet.addContentListener(this);
    }

    public void setCoverMediaItem(MediaItem cover) {
        mCover = cover;
    }

    @Override
    public MediaItem getCoverMediaItem() {
        return mCover != null ? mCover : super.getCoverMediaItem();
    }

    void setMediaItems(ArrayList<Path> paths) {
        mPaths = paths;
    }

    ArrayList<Path> getMediaItems() {
        return mPaths;
    }

    public void setName(String name) {
        mName = name;
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public int getMediaItemCount() {
        return mPaths.size();
    }

    @Override
    public ArrayList<MediaItem> getMediaItem(int start, int count) {
        return getMediaItemFromPath(mPaths, start, count, mDataManager);
    }

    public static ArrayList<MediaItem> getMediaItemFromPath(
            ArrayList<Path> paths, int start, int count,
            DataManager dataManager) {
        if (start >= paths.size()) {
            return new ArrayList<MediaItem>();
        }
        int end = Math.min(start + count, paths.size());
        ArrayList<Path> subset = new ArrayList<Path>(paths.subList(start, end));
        final MediaItem[] buf = new MediaItem[end - start];
        ItemConsumer consumer = new ItemConsumer() {
            @Override
            public void consume(int index, MediaItem item) {
                buf[index] = item;
            }
        };
        dataManager.mapMediaItems(subset, consumer, 0);
        ArrayList<MediaItem> result = new ArrayList<MediaItem>(end - start);
        for (int i = 0; i < buf.length; i++) {
            result.add(buf[i]);
        }
        return result;
    }

    @Override
    protected int enumerateMediaItems(ItemConsumer consumer, int startIndex) {
        mDataManager.mapMediaItems(mPaths, consumer, startIndex);
        return mPaths.size();
    }

    @Override
    public int getTotalMediaItemCount() {
//        // SPRD: Modify 20160106 for bug520818, Cluster cannot be deleted sometimes.
//        // For Cluster album delete operation, do not fresh clusterContent
//        // and note the deleted images. @{
//        /* return mPaths.size(); */
        return mPaths.size() - mNumberOfDeletedImage;
        // @}
    }

    @Override
    public long reload() {
        // SPRD: Modify 20160106 for bug520818, Cluster cannot be deleted sometimes.
        // if current operation is delete cluster. @{
        mClusterAlbumSet.mCurrentClusterAlbum = this;
        mClusterAlbumSet.offsetInStack = offsetInStack + 1;

        if (mClusterAlbumSet.reload() > mDataVersion) {
            mDataVersion = nextVersionNumber();
        }

        offsetInStack = 0;
        // @}
        return mDataVersion;
    }

    @Override
    public void onContentDirty(Uri uri) {
        notifyContentChanged(uri);
    }

    @Override
    public int getSupportedOperations() {
        return SUPPORT_SHARE | SUPPORT_DELETE | SUPPORT_INFO;
    }

    @Override
    public void delete() {
        ItemConsumer consumer = new ItemConsumer() {
            @Override
            public void consume(int index, MediaItem item) {
                if ((item.getSupportedOperations() & SUPPORT_DELETE) != 0) {
                    item.delete();
                    // SPRD: Modify 20160106 for bug520818, Cluster cannot be deleted sometimes.
                    // For Cluster album delete operation, and note the deleted images. @{
                    mNumberOfDeletedImage++;
                    // @}
                }
            }
        };
        mDataManager.mapMediaItems(mPaths, consumer, 0);
    }

    @Override
    public boolean isLeafAlbum() {
        return true;
    }

    /* SPRD: fix bug 381452, album set not sync @{ */
    public long nextVersion() {
        mDataVersion = nextVersionNumber();
        return mDataVersion;
    }

    void addMediaItems(Path paths, int index) {
        if (paths != null) {
            mPaths.add(index, paths);
            nextVersion();
        }
    }
    /* @} */

    /*
     * SPRD: Modify 20160106 for bug520818, Cluster cannot be deleted sometimes
     * @{
     */
    // note the number of deleted image.
    private int mNumberOfDeletedImage;

    // set and get delete number for Cluster album delete operation
    public void setNumberOfDeletedImage(int number) {
        mNumberOfDeletedImage = number;
    }

    public int getNumberOfDeletedImage() {
        return mNumberOfDeletedImage;
    }
    /* @} */
}
