package com.android.gallery3d.v2.data;

import android.net.Uri;
import android.os.Process;
import android.provider.MediaStore;
import android.util.Log;

import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.app.GalleryStorageUtil;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.ContentListener;
import com.android.gallery3d.data.LocalMediaItem;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.v2.trash.TrashManager;
import com.android.gallery3d.v2.util.SdCardPermission;

import java.util.Collection;
import java.util.LinkedList;
import java.util.WeakHashMap;

/**
 * @author baolin.li
 */
public class DeleteManager extends Thread {
    private static final String TAG = DeleteManager.class.getSimpleName();

    private volatile static DeleteManager sDeleteManager;

    /**
     * 存放需要删除的媒体文件路径
     */
    private final LinkedList<LocalMediaItem> mLinkedList = new LinkedList<>();

    /**
     * 是否正在删除文件, 线程正忙
     */
    private volatile boolean mIsBusy;

    /**
     * 用于通知更新变化
     */
    private final WeakHashMap<ContentListener, Object> mContentListeners = new WeakHashMap<>();

    private DeleteManager() {
        mIsBusy = false;
    }

    /**
     * 单列对象
     *
     * @return DeleteManager
     */
    public static DeleteManager getDefault() {
        if (sDeleteManager == null) {
            synchronized (DeleteManager.class) {
                if (sDeleteManager == null) {
                    sDeleteManager = new DeleteManager();
                    sDeleteManager.start();
                }
            }
        }
        return sDeleteManager;
    }

    /**
     * 注册通知更新
     *
     * @param listener listener
     */
    public void registerContentListener(ContentListener listener) {
        synchronized (mContentListeners) {
            mContentListeners.put(listener, null);
        }
    }

    /**
     * 将需要删除的文件路径添加到列表中
     *
     * @param item LocalMediaItem
     */
    public void addMediaInfo(LocalMediaItem item) {
        boolean needNotify = false;
        synchronized (mLinkedList) {
            if (mLinkedList.isEmpty()) {
                needNotify = true;
            }
            mLinkedList.add(item);
        }
        if (needNotify) {
            onDirty();
        }
    }

    public void addMediaInfo(Collection<? extends LocalMediaItem> items) {
        boolean needNotify = false;
        synchronized (mLinkedList) {
            if (mLinkedList.isEmpty()) {
                needNotify = true;
            }
            mLinkedList.addAll(items);
        }
        if (needNotify) {
            onDirty();
        }
    }

    /**
     * 从列表中获得一个文件路径
     *
     * @return 文件路径 or null
     */
    private LocalMediaItem post() {
        synchronized (mLinkedList) {
            if (mLinkedList.isEmpty()) {
                return null;
            } else {
                return mLinkedList.remove();
            }
        }
    }

    /**
     * 唤醒线程工作
     */
    private synchronized void onDirty() {
        notifyAll();
    }

    @Override
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        LocalMediaItem lmItem;
        while (true) {
            lmItem = post();
            synchronized (this) {
                if (lmItem == null) {
                    Log.d(TAG, "Task wait.");
                    if (mIsBusy) {
                        mIsBusy = false;
                        //停止删除文件后, 主动更新下数据
                        onContentDirty();
                    }
                    Utils.waitWithoutInterrupt(this);
                    continue;
                }
            }
            mIsBusy = true;

            if (GalleryUtils.isSupportRecentlyDelete() && !lmItem.mIsDrmFile && (lmItem.getSize() < MediaItem.DELTE_FILE_LARGE_SIZE)) {
                TrashManager.getDefault().recycle(lmItem);
            }
            //删除文件
            Log.d(TAG, "run: delete file: " + lmItem.id + ", " + lmItem.filePath);
            if (!GalleryStorageUtil.isInInternalStorage(lmItem.filePath)) {
                SdCardPermission.deleteFile(lmItem.filePath);
            }
            Uri uri = Uri.withAppendedPath(MediaStore.Files.getContentUri("external"),
                    String.valueOf(lmItem.id));
            GalleryAppImpl.getApplication().getContentResolver().delete(uri, null, null);
        }
    }

    /**
     * 判断删除线程是否正在工作
     *
     * @return true if deleting
     */
    public synchronized boolean isBusy() {
        return mIsBusy;
    }

    /**
     * 主动触发数据更新
     */
    public synchronized void onContentDirty() {
        for (ContentListener listener : mContentListeners.keySet()) {
            listener.onContentDirty(null);
        }
    }
}
