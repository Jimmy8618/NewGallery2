package com.android.gallery3d.app;

import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.AlbumSetData;
import com.android.gallery3d.data.ComboAlbumSet;
import com.android.gallery3d.data.ContentListener;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.sprd.gallery3d.app.AlbumSetLoadingListener;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class SprdAlbumSetDataLoader {
    @SuppressWarnings("unused")
    private static final String TAG = SprdAlbumSetDataLoader.class.getSimpleName();

    private static final int MSG_LOAD_START = 1;
    private static final int MSG_LOAD_FINISH = 2;
    private static final int MSG_RUN_OBJECT = 3;
    private static final int MSG_LOAD_WILL = 4;
    private static final int MSG_MEDIA_DATA_LOAD_START = 5;
    private static final int MSG_MEDIA_DATA_LOAD_FINISH = 6;

    public interface MediaSetDataChangedListener {
        void onMediaSetDataChangeStarted();

        void onMediaSetDataChanged(AlbumSetData data);

        void onMediaSetDataChangeFinished();
    }

    private final ArrayList<MediaSet> mData;
    private int mCurrentLoaded = 0;
    private boolean mMediaDataLoading;

    private final MediaSet mSource;
    private long mSourceVersion = MediaObject.INVALID_DATA_VERSION;
    private int mSize;

    private MediaSetDataChangedListener mMediaSetDataChangedListener;
    private AlbumSetLoadingListener mLoadingListener;
    private ReloadTask mReloadTask;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private final MySourceListener mSourceListener = new MySourceListener();

    private Handler mMainHandler;

    public SprdAlbumSetDataLoader(AbstractGalleryActivity activity, MediaSet albumSet, int cacheSize) {
        mSource = Utils.checkNotNull(albumSet);
        mData = new ArrayList<MediaSet>();
        mMainHandler = new MySynchronizedHandler(activity.getGLRoot(), this);
    }

    private static class MySynchronizedHandler extends SynchronizedHandler {
        private final WeakReference<SprdAlbumSetDataLoader> mSprdAlbumSetDataLoader;

        public MySynchronizedHandler(GLRoot root, SprdAlbumSetDataLoader sprdAlbumSetDataLoader) {
            super(root);
            mSprdAlbumSetDataLoader = new WeakReference<SprdAlbumSetDataLoader>(sprdAlbumSetDataLoader);
        }

        @Override
        public void handleMessage(Message message) {
            SprdAlbumSetDataLoader sprdAlbumSetDataLoader = mSprdAlbumSetDataLoader.get();
            if (sprdAlbumSetDataLoader != null) {
                sprdAlbumSetDataLoader.handleMySynchronizedHandlerMsg(message);
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
            case MSG_LOAD_WILL:
                if (mLoadingListener != null) {
                    mLoadingListener.onLoadingWill();
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

    public void pause() {
        mReloadTask.terminate();
        mReloadTask = null;
        mSource.removeContentListener(mSourceListener);
        if (mBackgroundThread != null) {
            mBackgroundThread.quit();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        }
    }

    public void resume() {
        if (mBackgroundThread == null) {
            mBackgroundThread = new HandlerThread("SprdAlbumSetDataLoader thread");
            mBackgroundThread.start();
            mBackgroundHandler = new BackgroundHanlder(mBackgroundThread.getLooper());
        }
        mSource.addContentListener(mSourceListener);
        mReloadTask = new ReloadTask();
        mReloadTask.start();
    }

    public MediaSet getMediaSet(int index) {
        if (index >= mData.size() || index < 0) {
            return null;
        }
        return mData.get(index);
    }

    public int size() {
        return mSize;
    }

    private void onMediaDataLoadingStart() {
        if (!mMediaDataLoading) {
            mMediaDataLoading = true;
            updateLoadingWill();
            mMainHandler.sendEmptyMessage(MSG_MEDIA_DATA_LOAD_START);
        }
    }

    private void onMediaDataLoadingFinish() {
        if (mMediaDataLoading) {
            mMediaDataLoading = false;
            mMainHandler.sendEmptyMessage(MSG_MEDIA_DATA_LOAD_FINISH);
        }
    }

    private class MySourceListener implements ContentListener {
        @Override
        public void onContentDirty(Uri uri) {
            if (mReloadTask != null) {
                mReloadTask.notifyDirty();
            }
        }
    }

    public void setLoadingListener(AlbumSetLoadingListener listener) {
        mLoadingListener = listener;
    }

    public void setMediaSetDataChangedListener(MediaSetDataChangedListener listener) {
        mMediaSetDataChangedListener = listener;
    }

    private static class UpdateInfo {
        public MediaSet item;
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
        private final UpdateInfo mUpdateInfo;

        public UpdateContent(UpdateInfo info) {
            mUpdateInfo = info;
        }

        @Override
        public Void call() {
            if (mReloadTask == null || mUpdateInfo.item == null) {
            } else {
                mData.add(mUpdateInfo.item);
                if (mMediaSetDataChangedListener != null) {
                    mMediaSetDataChangedListener.onMediaSetDataChanged(new AlbumSetData(mCurrentLoaded, mUpdateInfo.item, mSize));
                }
                mCurrentLoaded++;
            }
            if (mCurrentLoaded == mSize) {
                onMediaDataLoadingFinish();
            }
            return null;
        }
    }

    private <T> T executeAndWait(Callable<T> callable) {
        FutureTask<T> task = new FutureTask<T>(callable);
        try {
            mBackgroundHandler.sendMessage(mBackgroundHandler.obtainMessage(MSG_RUN_OBJECT, task));
        } catch (NullPointerException e) {
            return null;
        }
        try {
            return task.get();
        } catch (InterruptedException e) {
            return null;
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    // TODO: load active range first
    private class ReloadTask extends Thread {
        private volatile boolean mActive = true;
        private volatile boolean mDirty = true;
        private volatile boolean mIsLoading = false;

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
                        if (!mSource.isLoading()) {
                            updateLoading(false);
                        }
                        Utils.waitWithoutInterrupt(this);
                        continue;
                    }
                }
                mDirty = false;
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
                    mSize = mSource.getSubMediaSetCount();
                    Log.d(TAG, "ReloadTask run SubMediaSetCount = " + mSize);
                    if (mSize == 0 || mCurrentLoaded >= mSize) {
                        continue;
                    }
                }
                info.item = mSource.getSubMediaSet(mCurrentLoaded);
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

    private void updateLoadingWill() {
        mMainHandler.sendEmptyMessage(MSG_LOAD_WILL);
    }

    boolean mForceDirty = false;

    public void reloadData() {
        if (null != mReloadTask && mSource instanceof ComboAlbumSet) {
            mForceDirty = true;
            mSource.reForceDirty(true);
            mReloadTask.notifyDirty();
        }
    }

    public void sourceReforceDirty() {
        if (mSource instanceof ComboAlbumSet) {
            mForceDirty = true;
            mSource.reForceDirty(true);
        }
    }
}