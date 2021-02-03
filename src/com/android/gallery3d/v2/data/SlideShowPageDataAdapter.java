package com.android.gallery3d.v2.data;

import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;

import com.android.gallery3d.data.ContentListener;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.v2.app.GalleryActivity2;
import com.android.gallery3d.v2.page.SlideShowPageFragment;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

public class SlideShowPageDataAdapter implements SlideShowPageFragment.Model {
    private static final String TAG = SlideShowPageDataAdapter.class.getSimpleName();

    private static final int IMAGE_QUEUE_CAPACITY = 3;

    public interface SlideshowSource {
        void addContentListener(ContentListener listener);

        void removeContentListener(ContentListener listener);

        long reload();

        MediaItem getMediaItem(int index);

        int findItemIndex(Path path, int hint);
    }

    private final SlideshowSource mSource;

    private int mLoadIndex = 0;
    private int mNextOutput = 0;
    private boolean mIsActive = false;
    private boolean mNeedReset;
    private boolean mDataReady;
    private Path mInitialPath;

    private final LinkedList<SlideShowPageFragment.Slide> mImageQueue = new LinkedList<>();

    private Future<Void> mReloadTask;
    private final ThreadPool mThreadPool;

    private long mDataVersion = MediaObject.INVALID_DATA_VERSION;
    private final AtomicBoolean mNeedReload = new AtomicBoolean(false);
    private final SourceListener mSourceListener = new SourceListener();

    // The index is just a hint if initialPath is set
    public SlideShowPageDataAdapter(GalleryActivity2 activity, SlideshowSource source, int index,
                                    Path initialPath) {
        mSource = source;
        mInitialPath = initialPath;
        mLoadIndex = index;
        mNextOutput = index;
        mThreadPool = activity.getThreadPool();
    }

    private MediaItem loadItem() {
        if (mNeedReload.compareAndSet(true, false)) {
            long v = mSource.reload();
            if (v != mDataVersion) {
                mDataVersion = v;
                mNeedReset = true;
                return null;
            }
        }
        int index = mLoadIndex;
        if (mInitialPath != null) {
            index = mSource.findItemIndex(mInitialPath, index);
            mInitialPath = null;
        }
        return mSource.getMediaItem(index);
    }

    private class ReloadTask implements ThreadPool.Job<Void> {
        @Override
        public Void run(ThreadPool.JobContext jc) {
            while (true) {
                synchronized (SlideShowPageDataAdapter.this) {
                    while (mIsActive && (!mDataReady
                            || mImageQueue.size() >= IMAGE_QUEUE_CAPACITY)) {
                        try {
                            SlideShowPageDataAdapter.this.wait();
                        } catch (InterruptedException ex) {
                            // ignored.
                        }
                        continue;
                    }
                }
                if (!mIsActive) {
                    return null;
                }
                mNeedReset = false;

                MediaItem item = loadItem();

                if (mNeedReset) {
                    synchronized (SlideShowPageDataAdapter.this) {
                        mImageQueue.clear();
                        mLoadIndex = mNextOutput;
                    }
                    continue;
                }

                if (item == null) {
                    synchronized (SlideShowPageDataAdapter.this) {
                        if (!mNeedReload.get()) {
                            mDataReady = false;
                        }
                        SlideShowPageDataAdapter.this.notifyAll();
                    }
                    continue;
                }

                Bitmap bitmap = item
                        .requestImage(MediaItem.TYPE_THUMBNAIL)
                        .run(jc);

                if (bitmap != null) {
                    synchronized (SlideShowPageDataAdapter.this) {
                        mImageQueue.addLast(
                                new SlideShowPageFragment.Slide(item, mLoadIndex, bitmap));
                        if (mImageQueue.size() == 1) {
                            SlideShowPageDataAdapter.this.notifyAll();
                        }
                    }
                }
                ++mLoadIndex;
            }
        }
    }

    private class SourceListener implements ContentListener {
        @Override
        public void onContentDirty(Uri uri) {
            synchronized (SlideShowPageDataAdapter.this) {
                mNeedReload.set(true);
                mDataReady = true;
                SlideShowPageDataAdapter.this.notifyAll();
            }
        }
    }

    private synchronized SlideShowPageFragment.Slide innerNextBitmap() {
        while (mIsActive && mDataReady && mImageQueue.isEmpty()) {
            try {
                wait();
            } catch (InterruptedException t) {
                throw new AssertionError();
            }
        }
        if (mImageQueue.isEmpty()) {
            return null;
        }
        mNextOutput++;
        this.notifyAll();
        return mImageQueue.removeFirst();
    }

    @Override
    public Future<SlideShowPageFragment.Slide> nextSlide(FutureListener<SlideShowPageFragment.Slide> listener) {
        return mThreadPool.submit(new ThreadPool.Job<SlideShowPageFragment.Slide>() {
            @Override
            public SlideShowPageFragment.Slide run(ThreadPool.JobContext jc) {
                jc.setMode(ThreadPool.MODE_NONE);
                return innerNextBitmap();
            }
        }, listener);
    }

    @Override
    public void pause() {
        // SPRD: Modify for bug 580368,620359 NPE is mReloadTask is null
        if (mReloadTask != null) {
            mReloadTask.cancel();
        }
        synchronized (this) {
            mIsActive = false;
            notifyAll();
        }
        mSource.removeContentListener(mSourceListener);
        // SPRD: Modify for bug572296, set mReloadTask to null in FutureListener callback
        // mReloadTask.waitDone();
        // mReloadTask = null;
    }

    @Override
    public synchronized void resume() {
        mIsActive = true;
        mSource.addContentListener(mSourceListener);
        mNeedReload.set(true);
        mDataReady = true;
        /*
         * SPRD: Modify for bug572296, add FutureListener to monitor status of job, and then do
         * something in onFutureDone callback @{
         */
        mReloadTask = mThreadPool.submit(new ReloadTask(), new FutureListener<Void>() {

            @Override
            public void onFutureDone(Future<Void> future) {
                Log.d(TAG, "<ReloadTask.onFutureDone>");
                // SPRD: bug 620359,mReloadTask is null
                if (future != null && future.isCancelled()) {
                    mReloadTask = null;
                }
            }
        });
        /* @} */
    }
}
