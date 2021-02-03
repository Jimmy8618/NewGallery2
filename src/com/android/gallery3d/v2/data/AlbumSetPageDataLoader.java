package com.android.gallery3d.v2.data;

import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.ContentListener;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.ui.SynchronizedHandler;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class AlbumSetPageDataLoader {
    private static final String TAG = AlbumSetPageDataLoader.class.getSimpleName();

    private static final int MSG_LOAD_START = 1;
    private static final int MSG_LOAD_LOADING = 2;
    private static final int MSG_LOAD_FINISH = 3;
    private static final int MSG_LOAD_EMPTY = 4;
    private static final int MSG_RUN_OBJECT = 5;

    private MediaSet mSource;
    private final List<MediaSet> mData;
    private final List<MediaSet> mCache;
    private final List<String> mHideMediaSets;
    private int mCurrentLoaded = 0;
    private int mSize;
    private long mSourceVersion = MediaObject.INVALID_DATA_VERSION;
    private boolean mNeedAddMyAlbumLabel;

    private ReloadTask mReloadTask;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private Handler mMainHandler;

    private final MySourceListener mSourceListener = new MySourceListener();

    private AlbumSetLoadingListener mLoadingListener;

    public AlbumSetPageDataLoader(MediaSet mediaSet, List<String> hideMediaSets) {
        mSource = Utils.checkNotNull(mediaSet);
        mData = new ArrayList<>();
        mCache = new ArrayList<>();
        mHideMediaSets = hideMediaSets == null ? new ArrayList<String>() : hideMediaSets;
        mMainHandler = new MySynchronizedHandler(null, this);
    }

    public void setLoadingListener(AlbumSetLoadingListener loadingListener) {
        mLoadingListener = loadingListener;
    }

    private static class MySynchronizedHandler extends SynchronizedHandler {
        private final WeakReference<AlbumSetPageDataLoader> mAlbumSetPageDataLoader;

        public MySynchronizedHandler(GLRoot root, AlbumSetPageDataLoader albumSetPageDataLoader) {
            super(root);
            mAlbumSetPageDataLoader = new WeakReference<>(albumSetPageDataLoader);
        }

        @Override
        public void handleMessage(Message message) {
            AlbumSetPageDataLoader albumSetPageDataLoader = mAlbumSetPageDataLoader.get();
            if (albumSetPageDataLoader != null) {
                albumSetPageDataLoader.handleMySynchronizedHandlerMsg(message);
            }
        }
    }

    private void handleMySynchronizedHandlerMsg(Message msg) {
        switch (msg.what) {
            case MSG_LOAD_START:
                if (mLoadingListener != null) {
                    mLoadingListener.loadStart();
                }
                return;
            case MSG_LOAD_LOADING:
                if (mLoadingListener != null) {
                    mLoadingListener.loading(msg.arg1, msg.arg2, (AlbumSetItem[]) msg.obj);
                }
                return;
            case MSG_LOAD_FINISH:
                if (mLoadingListener != null) {
                    mLoadingListener.loadEnd();
                }
                return;
            case MSG_LOAD_EMPTY:
                if (mLoadingListener != null) {
                    mLoadingListener.loadEmpty();
                }
                return;
            default:
                return;
        }
    }

    private class BackgroundHandler extends Handler {
        public BackgroundHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_RUN_OBJECT:
                    ((Runnable) msg.obj).run();
                    return;
            }
        }
    }

    public void pause() {
        if (mReloadTask == null) {
            return;
        }

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
        if (mReloadTask != null) {
            return;
        }

        if (mBackgroundThread == null) {
            mBackgroundThread = new HandlerThread("AlbumSetPageDataLoader Thread");
            mBackgroundThread.start();
            mBackgroundHandler = new BackgroundHandler(mBackgroundThread.getLooper());
        }
        mSource.addContentListener(mSourceListener);
        mReloadTask = new ReloadTask();
        mReloadTask.start();
    }

    private class MySourceListener implements ContentListener {
        @Override
        public void onContentDirty(Uri uri) {
            if (mReloadTask != null) {
                mReloadTask.notifyDirty();
            }
        }
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
                mNeedAddMyAlbumLabel = true;
                return info;
            }
            return mCurrentLoaded >= mSize ? null : info;
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
                if (mLoadingListener != null) {
                    AlbumSetItem label = null;
                    if (mNeedAddMyAlbumLabel && mUpdateInfo.item.isMyAlbum()) {
                        mNeedAddMyAlbumLabel = false;
                        label = new MyAlbumLabelItem();
                    }
                    AlbumSetItem item = new AlbumSetItem(mUpdateInfo.item);
                    AlbumSetItem[] result;
                    if (label == null) {
                        result = new AlbumSetItem[]{item};
                    } else {
                        result = new AlbumSetItem[]{label, item};
                    }
                    mMainHandler.obtainMessage(MSG_LOAD_LOADING, mCurrentLoaded, mSize, result).sendToTarget();
                }
                mCurrentLoaded++;
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
            if (!loading && mSize == 0) {
                mMainHandler.sendEmptyMessage(MSG_LOAD_EMPTY);
            }
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            Log.d(TAG, "ReloadTask In.");
            boolean updateComplete = false;
            while (mActive) {
                synchronized (this) {
                    if (mActive && !mDirty && updateComplete) {
                        if (!mSource.isLoading()) {
                            updateLoading(false);
                        }
                        Log.d(TAG, "ReloadTask wait, mSize = " + mSize);
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
                if (updateComplete) {
                    continue;
                }

                if (sourceVersion != version) {
                    mSize = mSource.getSubMediaSetCount();
                    //
                    mCache.clear();
                    MediaSet mediaSet;
                    for (int i = 0; i < mSize; i++) {
                        mediaSet = mSource.getSubMediaSet(i);
                        if (mediaSet == null
                                || mHideMediaSets.contains(mediaSet.getPath().toString())) {
                            continue;
                        }
                        mCache.add(mediaSet);
                    }
                    mSize = mCache.size();
                    //
                    if (mSize == 0 || mCurrentLoaded >= mSize) {
                        continue;
                    }
                }
                info.item = getSubMediaSet(mCurrentLoaded);//mSource.getSubMediaSet(mCurrentLoaded);
                executeAndWait(new UpdateContent(info));
            }
            updateLoading(false);
            Log.d(TAG, "ReloadTask Out.");
        }

        private MediaSet getSubMediaSet(int index) {
            if (index >= mCache.size()) {
                return null;
            }
            return mCache.get(index);
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
