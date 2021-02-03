package com.android.gallery3d.v2.trash;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.os.Process;
import android.util.Log;

import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.v2.trash.db.TrashStore;

import java.io.File;

//最近删除SD卡图片会保存在SD卡中, 若SD卡插入或移除, 需要更新最近删除数据库, 让图片是否显示
public class TrashMonitor extends Thread {
    private static final String TAG = TrashMonitor.class.getSimpleName();
    private volatile boolean mActive = true;
    private volatile boolean mDirty = true;
    private ContentResolver mContentResolver;

    public TrashMonitor() {
        mContentResolver = GalleryAppImpl.getApplication().getContentResolver();
    }

    public synchronized void notifyDirty() {
        mDirty = true;
        notifyAll();
    }

    public synchronized void terminate() {
        mActive = false;
        notifyAll();
    }

    @Override
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        Log.d(TAG, "run start.");
        while (mActive) {
            synchronized (this) {
                if (mActive && !mDirty) {
                    Log.d(TAG, "run wait.");
                    Utils.waitWithoutInterrupt(this);
                    continue;
                }
            }
            mDirty = false;
            //////////////////////////begin/////////////////////////////
            Cursor cursor = null;
            try {
                cursor = mContentResolver.query(TrashStore.Local.Media.CONTENT_URI,
                        new String[]{
                                TrashStore.Local.Columns._ID,
                                TrashStore.Local.Columns.TRASH_FILE_PATH,
                                TrashStore.Local.Columns.IS_PENDING
                        }, null, null, null);

                if (cursor != null) {
                    int _id;
                    int _pending;
                    String _path;
                    boolean _exist;
                    while (cursor.moveToNext()) {
                        if (!mActive || mDirty) {
                            break;
                        }
                        _id = cursor.getInt(cursor.getColumnIndex(TrashStore.Local.Columns._ID));
                        _pending = cursor.getInt(cursor.getColumnIndex(TrashStore.Local.Columns.IS_PENDING));
                        _path = cursor.getString(cursor.getColumnIndex(TrashStore.Local.Columns.TRASH_FILE_PATH));
                        _exist = new File(_path).exists();
                        if (_exist && _pending == 1) {
                            ContentValues values = new ContentValues(1);
                            values.put(TrashStore.Local.Columns.IS_PENDING, 0);
                            mContentResolver.update(TrashStore.Local.Media.CONTENT_URI, values, TrashStore.Local.Columns._ID + " = " + _id, null);
                        } else if (!_exist && _pending == 0) {
                            ContentValues values = new ContentValues(1);
                            values.put(TrashStore.Local.Columns.IS_PENDING, 1);
                            mContentResolver.update(TrashStore.Local.Media.CONTENT_URI, values, TrashStore.Local.Columns._ID + " = " + _id, null);
                        }
                    }
                }
            } catch (Exception ignored) {
            }

            Utils.closeSilently(cursor);
            ///////////////////////////end//////////////////////////////
        }
        Log.d(TAG, "run end.");
    }
}
