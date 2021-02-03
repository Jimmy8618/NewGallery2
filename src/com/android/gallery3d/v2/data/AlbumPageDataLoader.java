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
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.ui.SynchronizedHandler;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class AlbumPageDataLoader {
    private static final String TAG = AlbumPageDataLoader.class.getSimpleName();

    private static final int LOAD_DATA_SIZE = 32;

    private static final int MSG_LOAD_START = 1;
    private static final int MSG_LOAD_LOADING = 2;
    private static final int MSG_LOAD_FINISH = 3;
    private static final int MSG_LOAD_EMPTY = 4;
    private static final int MSG_RUN_OBJECT = 5;

    private final List<MediaItem> mData;
    private final List<LabelItem> mLabelData;
    private final MediaSet mSource;
    private long mSourceVersion = MediaObject.INVALID_DATA_VERSION;

    private final Handler mMainHandler;
    private int mCurrentLoaded = 0;
    private int mCurrentPosition = 0;
    private int mSize = 0;

    private ReloadTask mReloadTask;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    private MySourceListener mSourceListener = new MySourceListener();

    private AlbumLoadingListener mLoadingListener;

    public AlbumPageDataLoader(MediaSet mediaSet) {
        mSource = Utils.checkNotNull(mediaSet);
        mData = new ArrayList<>();
        mLabelData = new ArrayList<>();
        mMainHandler = new MySynchronizedHandler(this);
    }

    public void setAlbumLoadingListener(AlbumLoadingListener albumLoadingListener) {
        mLoadingListener = albumLoadingListener;
    }

    private static class MySynchronizedHandler extends SynchronizedHandler {
        private final WeakReference<AlbumPageDataLoader> mAlbumPageDataLoader;

        public MySynchronizedHandler(AlbumPageDataLoader albumPageDataLoader) {
            super(null);
            mAlbumPageDataLoader = new WeakReference<>(albumPageDataLoader);
        }

        @Override
        public void handleMessage(Message message) {
            AlbumPageDataLoader albumPageDataLoader = mAlbumPageDataLoader.get();
            if (albumPageDataLoader != null) {
                albumPageDataLoader.handleMySynchronizedHandlerMsg(message);
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
                    LoadItem item = (LoadItem) msg.obj;
                    mLoadingListener.loading(item.index, item.size, item.items, item.loadedSize);
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
            mBackgroundThread = new HandlerThread("AlbumPageDataLoader Thread");
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
        public List<MediaItem> items;
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
                mLabelData.clear();
                mCurrentLoaded = 0;
                mCurrentPosition = 0;
                return info;
            }
            return mCurrentLoaded >= mSize ? null : info;
        }
    }

    private class UpdateContent implements Callable<Void> {
        private UpdateInfo mUpdateInfo;

        public UpdateContent(UpdateInfo info) {
            mUpdateInfo = info;
        }

        @Override
        public Void call() {
            List<MediaItem> items = mUpdateInfo.items;
            if (mReloadTask == null || items == null || items.isEmpty()) {
            } else {
                MediaItem mTmpMediaItem;

                LabelItem mTmpLabelItem = null;
                ImageItem mTmpImageItem;

                LoadItem loadItem = new LoadItem();
                loadItem.index = mCurrentLoaded;
                loadItem.size = mSize;
                loadItem.items = new ArrayList<>();
                loadItem.loadedSize = items.size();

                for (int i = 0; i < items.size(); i++) {
                    mTmpMediaItem = items.get(i);
                    //判断是否有LabelItem
                    if (mLabelData.size() <= 0) {
                        //Label, 创建LabelItem对象
                        mTmpLabelItem = new LabelItem(mSource, mTmpMediaItem.getDate(), mCurrentPosition++);
                        mLabelData.add(mTmpLabelItem);
                        //Image, 创建ImageItem对象
                        mTmpImageItem = new ImageItem(mSource, mTmpMediaItem, mTmpLabelItem, mCurrentPosition++, mCurrentLoaded);

                        loadItem.items.add(mTmpLabelItem);
                        loadItem.items.add(mTmpImageItem);
                    } else {
                        if (mTmpLabelItem == null) {
                            //从mLabelData中取出最后一个LabelItem
                            mTmpLabelItem = mLabelData.get(mLabelData.size() - 1);
                        }

                        //如果mTmpMediaItem日期和mTmpLabelItem日期相同, 仅创建ImageItem对象返回即可
                        if (Utils.equals(mTmpMediaItem.getDate(), mTmpLabelItem.getDate())) {
                            //Image
                            mTmpImageItem = new ImageItem(mSource, mTmpMediaItem, mTmpLabelItem, mCurrentPosition++, mCurrentLoaded);

                            loadItem.items.add(mTmpImageItem);
                        } else {//日期不同, 新创建一个LabelItem
                            //Label, 创建LabelItem对象
                            mTmpLabelItem = new LabelItem(mSource, mTmpMediaItem.getDate(), mCurrentPosition++);
                            mLabelData.add(mTmpLabelItem);
                            //Image, 创建ImageItem对象
                            mTmpImageItem = new ImageItem(mSource, mTmpMediaItem, mTmpLabelItem, mCurrentPosition++, mCurrentLoaded);

                            loadItem.items.add(mTmpLabelItem);
                            loadItem.items.add(mTmpImageItem);
                        }
                    }
                    mData.add(mTmpMediaItem);
                    mCurrentLoaded++;
                }
                if (mLoadingListener != null) {
                    mMainHandler.obtainMessage(MSG_LOAD_LOADING, loadItem).sendToTarget();
                }
            }
            return null;
        }
    }

    private class LoadItem {
        int index;
        int size;
        List<AlbumItem> items;
        int loadedSize;
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
                        updateLoading(false);
                        Log.d(TAG, "ReloadTask wait, mSize = " + mSize);
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
                if (updateComplete) {
                    continue;
                }

                if (sourceVersion != version) {
                    mSize = mSource.getMediaItemCount();
                }
                info.items = mSource.getMediaItem(mCurrentLoaded, LOAD_DATA_SIZE);
                executeAndWait(new UpdateContent(info));
            }
            updateLoading(false);
            Log.d(TAG, "ReloadTask Out.");
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
