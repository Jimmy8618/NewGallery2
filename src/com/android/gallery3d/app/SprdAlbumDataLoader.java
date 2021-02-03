package com.android.gallery3d.app;

import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.AlbumData;
import com.android.gallery3d.data.ContentListener;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.ui.SynchronizedHandler;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class SprdAlbumDataLoader {
    @SuppressWarnings("unused")
    private static final String TAG = SprdAlbumDataLoader.class.getSimpleName();

    private static final int LOAD_DATA_SIZE = 32;

    private static final int MSG_LOAD_START = 1;
    private static final int MSG_LOAD_FINISH = 2;
    private static final int MSG_RUN_OBJECT = 3;

    private static final int MSG_MEDIA_DATA_LOAD_START = 4;
    private static final int MSG_MEDIA_DATA_LOAD_FINISH = 5;

    private ArrayList<MediaItem> mData;

    private boolean mMediaDataLoading = false;

    public interface MediaSetDataChangedListener {
        void onMediaSetDataChangeStarted();

        void onMediaSetDataChanged(AlbumData data);

        void onMediaSetDataChangeFinished();
    }

    private MediaSetDataChangedListener mMediaSetDataChangedListener;

    private final MediaSet mSource;
    private long mSourceVersion = MediaObject.INVALID_DATA_VERSION;

    private final Handler mMainHandler;
    private int mSize = 0;
    private int mCurrentLoaded = 0;
    private MySourceListener mSourceListener = new MySourceListener();
    private LoadingListener mLoadingListener;

    private ReloadTask mReloadTask;

    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    public SprdAlbumDataLoader(AbstractGalleryActivity context, MediaSet mediaSet) {
        mSource = mediaSet;
        mData = new ArrayList<MediaItem>();
        mMainHandler = new MySynchronizedHandler(context.getGLRoot(), this);
    }

    private static class MySynchronizedHandler extends SynchronizedHandler {
        private final WeakReference<SprdAlbumDataLoader> mSprdAlbumDataLoader;

        public MySynchronizedHandler(GLRoot root, SprdAlbumDataLoader sprdAlbumDataLoader) {
            super(root);
            mSprdAlbumDataLoader = new WeakReference<>(sprdAlbumDataLoader);
        }

        @Override
        public void handleMessage(Message message) {
            SprdAlbumDataLoader sprdAlbumDataLoader = mSprdAlbumDataLoader.get();
            if (sprdAlbumDataLoader != null) {
                sprdAlbumDataLoader.handleMySynchronizedHandlerMsg(message);
            }
        }
    }

    private void handleMySynchronizedHandlerMsg(Message message) {
        switch (message.what) {
            case MSG_LOAD_START:
                if (mLoadingListener != null) {
                    mLoadingListener.onLoadingStarted();
                }
                return;
            case MSG_LOAD_FINISH:
                if (mLoadingListener != null) {
                    mLoadingListener.onLoadingFinished(false);
                }
                return;
            case MSG_MEDIA_DATA_LOAD_START:
                if (mMediaSetDataChangedListener != null) {
                    mMediaSetDataChangedListener.onMediaSetDataChangeStarted();
                }
                return;
            case MSG_MEDIA_DATA_LOAD_FINISH:
                if (mMediaSetDataChangedListener != null) {
                    mMediaSetDataChangedListener.onMediaSetDataChangeFinished();
                }
                return;
        }
    }

    private class BackgroundHanlder extends Handler {
        public BackgroundHanlder(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            // TODO Auto-generated method stub
            switch (msg.what) {
                case MSG_RUN_OBJECT:
                    ((Runnable) msg.obj).run();
                    return;
            }
        }
    }

    public MediaSet getMediaSet() {
        return mSource;
    }

    public void resume() {
        Log.d(TAG, "resume");
        if (mBackgroundThread == null) {
            mBackgroundThread = new HandlerThread("SprdAlbumDataLoader thread");
            mBackgroundThread.start();
            mBackgroundHandler = new BackgroundHanlder(mBackgroundThread.getLooper());
        }
        mSource.addContentListener(mSourceListener);
        mReloadTask = new ReloadTask();
        mReloadTask.start();
    }

    public void pause() {
        Log.d(TAG, "pause");
        mReloadTask.terminate();
        mReloadTask = null;
        mSource.removeContentListener(mSourceListener);
        if (mBackgroundThread != null) {
            mBackgroundThread.quit();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        }
    }

    public MediaItem get(int index) {
        if (index >= 0 && index < mData.size()) {
            return mData.get(index);
        }
        return null;
    }

    public int size() {
        return mSize;
    }

    public int findItem(Path id) {
        for (int i = 0; i < mData.size(); i++) {
            MediaItem item = mData.get(i);
            if (item != null && id == item.getPath()) {
                return i;
            }
        }
        return -1;
    }

    private class MySourceListener implements ContentListener {
        @Override
        public void onContentDirty(Uri uri) {
            if (mReloadTask != null) {
                mReloadTask.notifyDirty();
            }
        }
    }


    public void setMediaSetDataChangedListener(MediaSetDataChangedListener l) {
        mMediaSetDataChangedListener = l;
    }

    public void setLoadingListener(LoadingListener listener) {
        mLoadingListener = listener;
    }

    private <T> T executeAndWait(Callable<T> callable) {
        FutureTask<T> task = new FutureTask<T>(callable);
        try {
            mBackgroundHandler.sendMessage(
                    mBackgroundHandler.obtainMessage(MSG_RUN_OBJECT, task));
        } catch (NullPointerException e) {
            return null;
        }
        try {
            return task.get();
        } catch (InterruptedException e) {
            return null;
        } catch (ExecutionException e) {
            return null;
        }
    }

    private void onMediaDataLoadingStart() {
        if (!mMediaDataLoading) {
            mMediaDataLoading = true;
            mMainHandler.sendEmptyMessage(MSG_MEDIA_DATA_LOAD_START);
        }
    }

    private void onMediaDataLoadingFinish() {
        if (mMediaDataLoading) {
            mMediaDataLoading = false;
            mMainHandler.sendEmptyMessage(MSG_MEDIA_DATA_LOAD_FINISH);
        }
    }

    private static class UpdateInfo {
        public ArrayList<MediaItem> items;
    }

    private class GetUpdateInfo implements Callable<UpdateInfo> {
        private final long mVersion;

        public GetUpdateInfo(long version) {
            mVersion = version;
        }

        @Override
        public UpdateInfo call() {
            UpdateInfo info = new UpdateInfo();
            if (mSourceVersion != mVersion) {
                mSourceVersion = mVersion;
                mData.clear();
                mCurrentLoaded = 0;
                return info;
            }
            return mCurrentLoaded == mSize ? null : info;
        }
    }

    private class UpdateContent implements Callable<Void> {

        private UpdateInfo mUpdateInfo;

        public UpdateContent(UpdateInfo info) {
            mUpdateInfo = info;
        }

        @Override
        public Void call() {
            UpdateInfo info = mUpdateInfo;
            ArrayList<MediaItem> items = info.items;
            if ((items == null) || items.isEmpty()) {
                Log.d(TAG, "UpdateContent loading failed");
                return null;
            }
            mData.addAll(items);
            if (mMediaSetDataChangedListener != null) {
                mMediaSetDataChangedListener.onMediaSetDataChanged(new AlbumData(mCurrentLoaded, mSource, mSize, items));
            }
            mCurrentLoaded += items.size();
            if (mCurrentLoaded == mSize) {
                onMediaDataLoadingFinish();
            }
            return null;
        }
    }

    private class ReloadTask extends Thread {

        private volatile boolean mActive = true;
        private volatile boolean mDirty = true;
        private boolean mIsLoading = false;

        private void updateLoading(boolean loading) {
            if (mIsLoading == loading) {
                return;
            }
            mIsLoading = loading;
            mMainHandler.sendEmptyMessage(loading ? MSG_LOAD_START : MSG_LOAD_FINISH);
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            boolean updateComplete = false;
            while (mActive) {
                synchronized (this) {
                    if (mActive && !mDirty && updateComplete) {
                        updateLoading(false);
                        Utils.waitWithoutInterrupt(this);
                        continue;
                    }
                    mDirty = false;
                }
                updateLoading(true);
                long sourceVersion = mSourceVersion;
                long version = mSource.reload();
                UpdateInfo info = executeAndWait(new GetUpdateInfo(version));
                updateComplete = info == null;
                Log.d(TAG, "ReloadTask run updateComplete = " + updateComplete + " version = " + version + " sourceVersion = " + sourceVersion);
                if (updateComplete) {
                    continue;
                }

                onMediaDataLoadingStart();

                if (sourceVersion != version) {
                    mSize = mSource.getMediaItemCount();
                    Log.d(TAG, "ReloadTask run MediaItemCount = " + mSize);
                }

                info.items = mSource.getMediaItem(mCurrentLoaded, LOAD_DATA_SIZE);
                executeAndWait(new UpdateContent(info));
            }
            updateLoading(false);
        }

        public synchronized void notifyDirty() {
            mDirty = true;
            notifyAll();
        }

        public synchronized void terminate() {
            mActive = false;
            notifyAll();
        }
    }
}
