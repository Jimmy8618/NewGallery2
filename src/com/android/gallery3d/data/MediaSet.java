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
import android.provider.MediaStore;
import android.util.Log;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.ui.SelectionManager;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.MediaSetUtils;
import com.android.gallery3d.v2.util.Config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

// MediaSet is a directory-like data structure.
// It contains MediaItems and sub-MediaSets.
//
// The primary interface are:
// getMediaItemCount(), getMediaItem() and
// getSubMediaSetCount(), getSubMediaSet().
//
// getTotalMediaItemCount() returns the number of all MediaItems, including
// those in sub-MediaSets.
public abstract class MediaSet extends MediaObject {
    @SuppressWarnings("unused")
    private static final String TAG = "MediaSet";

    public static final int MEDIAITEM_BATCH_FETCH_COUNT = 500;
    public static final int INDEX_NOT_FOUND = -1;

    public static final int SYNC_RESULT_SUCCESS = 0;
    public static final int SYNC_RESULT_CANCELLED = 1;
    public static final int SYNC_RESULT_ERROR = 2;

    protected boolean mQuitQuery = false;

    private boolean mIsMyAlbum;

    /**
     * Listener to be used with requestSync(SyncListener).
     */
    public interface SyncListener {
        /**
         * Called when the sync task completed. Completion may be due to normal termination,
         * an exception, or cancellation.
         *
         * @param mediaSet   the MediaSet that's done with sync
         * @param resultCode one of the SYNC_RESULT_* constants
         */
        void onSyncDone(MediaSet mediaSet, int resultCode);
    }

    public MediaSet(Path path, long version) {
        super(path, version);
    }

    public int getMediaItemCount() {
        return 0;
    }

    // Returns the media items in the range [start, start + count).
    //
    // The number of media items returned may be less than the specified count
    // if there are not enough media items available. The number of
    // media items available may not be consistent with the return value of
    // getMediaItemCount() because the contents of database may have already
    // changed.
    public ArrayList<MediaItem> getMediaItem(int start, int count) {
        return new ArrayList<MediaItem>();
    }

    public MediaItem getCoverMediaItem() {
        ArrayList<MediaItem> items = getMediaItem(0, 1);
        if (items.size() > 0) {
            return items.get(0);
        }
        for (int i = 0, n = getSubMediaSetCount(); i < n; i++) {
            MediaItem cover = getSubMediaSet(i).getCoverMediaItem();
            if (cover != null) {
                return cover;
            }
        }
        return null;
    }

    public int getSubMediaSetCount() {
        return 0;
    }

    public MediaSet getSubMediaSet(int index) {
        throw new IndexOutOfBoundsException();
    }

    public boolean isLeafAlbum() {
        return false;
    }

    public boolean isCameraRoll() {
        return false;
    }

    /**
     * Method {@link #reload()} may process the loading task in background, this method tells
     * its client whether the loading is still in process or not.
     */
    public boolean isLoading() {
        return false;
    }

    public int getTotalMediaItemCount() {
        int total = getMediaItemCount();
        MediaSet mediaSet = null;
        for (int i = 0, n = getSubMediaSetCount(); i < n; i++) {
            mediaSet = getSubMediaSet(i);
            total += mediaSet == null ? 0 : mediaSet.getTotalMediaItemCount();
        }
        return total;
    }

    // TODO: we should have better implementation of sub classes
    public int getIndexOfItem(Path path, int hint) {
        // hint < 0 is handled below
        // first, try to find it around the hint
        int start = Math.max(0,
                hint - MEDIAITEM_BATCH_FETCH_COUNT / 2);
        ArrayList<MediaItem> list = getMediaItem(
                start, MEDIAITEM_BATCH_FETCH_COUNT);
        int index = getIndexOf(path, list);
        if (index != INDEX_NOT_FOUND) {
            return start + index;
        }

        // try to find it globally
        start = start == 0 ? MEDIAITEM_BATCH_FETCH_COUNT : 0;
        list = getMediaItem(start, MEDIAITEM_BATCH_FETCH_COUNT);
        while (true) {
            index = getIndexOf(path, list);
            if (index != INDEX_NOT_FOUND) {
                return start + index;
            }
            if (list.size() < MEDIAITEM_BATCH_FETCH_COUNT) {
                return INDEX_NOT_FOUND;
            }
            start += MEDIAITEM_BATCH_FETCH_COUNT;
            list = getMediaItem(start, MEDIAITEM_BATCH_FETCH_COUNT);
        }
    }

    protected int getIndexOf(Path path, ArrayList<MediaItem> list) {
        for (int i = 0, n = list.size(); i < n; ++i) {
            // item could be null only in ClusterAlbum
            MediaObject item = list.get(i);
            if (item != null && item.mPath == path) {
                return i;
            }
        }
        return INDEX_NOT_FOUND;
    }

    public abstract String getName();

    private WeakHashMap<ContentListener, Object> mListeners =
            new WeakHashMap<ContentListener, Object>();

    // NOTE: The MediaSet only keeps a weak reference to the listener. The
    // listener is automatically removed when there is no other reference to
    // the listener.
    public void addContentListener(ContentListener listener) {
        synchronized (mListeners) {
            mListeners.put(listener, null);
        }
    }

    public void removeContentListener(ContentListener listener) {
        synchronized (mListeners) {
            mListeners.remove(listener);
        }
    }

    // This should be called by subclasses when the content is changed.
    public void notifyContentChanged(Uri uri) {
        synchronized (mListeners) {
            for (ContentListener listener : mListeners.keySet()) {
                listener.onContentDirty(uri);
            }
        }
    }

    // Reload the content. Return the current data version. reload() should be called
    // in the same thread as getMediaItem(int, int) and getSubMediaSet(int).
    public abstract long reload();

    @Override
    public MediaDetails getDetails() {
        MediaDetails details = super.getDetails();
        details.addDetail(MediaDetails.INDEX_TITLE, getName());
        return details;
    }

    // Enumerate all media items in this media set (including the ones in sub
    // media sets), in an efficient order. ItemConsumer.consumer() will be
    // called for each media item with its index.
    public void enumerateMediaItems(ItemConsumer consumer) {
        enumerateMediaItems(consumer, 0);
    }

    public void enumerateTotalMediaItems(ItemConsumer consumer) {
        enumerateTotalMediaItems(consumer, 0);
    }

    public interface ItemConsumer {
        void consume(int index, MediaItem item);
    }

    // The default implementation uses getMediaItem() for enumerateMediaItems().
    // Subclasses may override this and use more efficient implementations.
    // Returns the number of items enumerated.
    protected int enumerateMediaItems(ItemConsumer consumer, int startIndex) {
        int total = getMediaItemCount();
        int start = 0;
        while (start < total) {
            int count = Math.min(MEDIAITEM_BATCH_FETCH_COUNT, total - start);
            ArrayList<MediaItem> items = getMediaItem(start, count);
            for (int i = 0, n = items.size(); i < n; i++) {
                MediaItem item = items.get(i);
                consumer.consume(startIndex + start + i, item);
            }
            start += count;
        }
        return total;
    }

    // Recursively enumerate all media items under this set.
    // Returns the number of items enumerated.
    protected int enumerateTotalMediaItems(
            ItemConsumer consumer, int startIndex) {
        int start = 0;
        start += enumerateMediaItems(consumer, startIndex);
        int m = getSubMediaSetCount();
        for (int i = 0; i < m; i++) {
            MediaSet mediaSet = getSubMediaSet(i);
            if (mediaSet == null) {
                continue;
            }
            start += mediaSet.enumerateTotalMediaItems(
                    consumer, startIndex + start);
        }
        return start;
    }

    /**
     * Requests sync on this MediaSet. It returns a Future object that can be used by the caller
     * to query the status of the sync. The sync result code is one of the SYNC_RESULT_* constants
     * defined in this class and can be obtained by Future.get().
     * <p>
     * Subclasses should perform sync on a different thread.
     * <p>
     * The default implementation here returns a Future stub that does nothing and returns
     * SYNC_RESULT_SUCCESS by get().
     */
    public Future<Integer> requestSync(SyncListener listener) {
        listener.onSyncDone(this, SYNC_RESULT_SUCCESS);
        return FUTURE_STUB;
    }

    private static final Future<Integer> FUTURE_STUB = new Future<Integer>() {
        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public Integer get() {
            return SYNC_RESULT_SUCCESS;
        }

        @Override
        public void waitDone() {
        }
    };

    protected Future<Integer> requestSyncOnMultipleSets(MediaSet[] sets, SyncListener listener) {
        return new MultiSetSyncFuture(sets, listener);
    }

    private class MultiSetSyncFuture implements Future<Integer>, SyncListener {
        @SuppressWarnings("hiding")
        private static final String TAG = "Gallery.MultiSetSync";

        private final SyncListener mListener;
        private final Future<Integer> mFutures[];

        private boolean mIsCancelled = false;
        private int mResult = -1;
        private int mPendingCount;

        @SuppressWarnings("unchecked")
        MultiSetSyncFuture(MediaSet[] sets, SyncListener listener) {
            mListener = listener;
            mPendingCount = sets.length;
            mFutures = new Future[sets.length];

            synchronized (this) {
                for (int i = 0, n = sets.length; i < n; ++i) {
                    mFutures[i] = sets[i].requestSync(this);
                    Log.d(TAG, "  request sync: " + Utils.maskDebugInfo(sets[i].getName()));
                }
            }
        }

        @Override
        public synchronized void cancel() {
            if (mIsCancelled) {
                return;
            }
            mIsCancelled = true;
            for (Future<Integer> future : mFutures) {
                future.cancel();
            }
            if (mResult < 0) {
                mResult = SYNC_RESULT_CANCELLED;
            }
        }

        @Override
        public synchronized boolean isCancelled() {
            return mIsCancelled;
        }

        @Override
        public synchronized boolean isDone() {
            return mPendingCount == 0;
        }

        @Override
        public synchronized Integer get() {
            waitDone();
            return mResult;
        }

        @Override
        public synchronized void waitDone() {
            try {
                while (!isDone()) {
                    wait();
                }
            } catch (InterruptedException e) {
                Log.d(TAG, "waitDone() interrupted");
            }
        }

        // SyncListener callback
        @Override
        public void onSyncDone(MediaSet mediaSet, int resultCode) {
            SyncListener listener = null;
            synchronized (this) {
                if (resultCode == SYNC_RESULT_ERROR) {
                    mResult = SYNC_RESULT_ERROR;
                }
                --mPendingCount;
                if (mPendingCount == 0) {
                    listener = mListener;
                    notifyAll();
                }
                Log.d(TAG, "onSyncDone: " + Utils.maskDebugInfo(mediaSet.getName())
                        + " #pending=" + mPendingCount);
            }
            if (listener != null) {
                listener.onSyncDone(MediaSet.this, mResult);
            }
        }
    }

    /* SPRD: fix bug 381452, album set not sync @{ */
    public MediaSet mCurrentClusterAlbum;
    public int offsetInStack = 0;
    /* @} */

    public void enumerateLocalPhoto(ItemConsumer consumer) {
        int m = getSubMediaSetCount();
        for (int i = 0; i < m; i++) {
            if (MediaSetUtils.isCameraSource(getSubMediaSet(i).getPath())) {
                getSubMediaSet(i).enumerateTotalMediaItems(consumer);
                //break;
            }
        }
    }

    public void enumerateVideoItems(final ItemConsumer consumer) {
        enumerateTotalMediaItems(new ItemConsumer() {
            int total = getTotalMediaItemCount();

            @Override
            public void consume(int index, MediaItem item) {
                if (index < 0 || index >= total
                        || item.getMediaType() != MediaObject.MEDIA_TYPE_VIDEO) {
                    return;
                }
                consumer.consume(index, item);
            }
        });
    }

    public void reForceDirty(boolean forceDirty) {

    }

    public void setQuitQuery(boolean quitQuery) {
        /*
        Log.d(TAG, "setQuitQuery quitQuery = " + quitQuery);
        mQuitQuery = quitQuery;
        */
    }

    public boolean isMyAlbum() {
        return mIsMyAlbum;
    }

    public void setMyAlbum(boolean myAlbum) {
        mIsMyAlbum = myAlbum;
    }

    public boolean isPlaceHolder() {
        return false;
    }

    public String getHead() {
        return null;
    }

    public int getBucketId() {
        throw new UnsupportedOperationException();
    }

    protected String hidedAlbumsClause() {
        Set<String> hidedAlbums = new HashSet<>();
        hidedAlbums = Config.getPref(SelectionManager.KEY_SELECT_ALBUM_ITEMS, hidedAlbums);
        StringBuilder sb = new StringBuilder();
        for (String s : hidedAlbums) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(s.substring(s.lastIndexOf("/") + 1));
        }
        return MediaStore.Images.ImageColumns.BUCKET_ID + " not in (" + sb.toString() + ")";
    }
}
