package com.android.gallery3d.v2.media.extras;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Handler;
import android.os.Process;
import android.provider.MediaStore;
import android.util.Log;

import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.common.Utils;

import java.util.ArrayList;
import java.util.Calendar;

public class MediaExtrasManager extends ContentObserver {
    private static final String TAG = MediaExtrasManager.class.getSimpleName();

    private Task mTask;

    public MediaExtrasManager() {
        super(new Handler());
        //监听图片, 视频 数据库变化
        GalleryAppImpl.getApplication().getContentResolver().registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                this);
        GalleryAppImpl.getApplication().getContentResolver().registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                this);
    }

    @Override
    public void onChange(boolean selfChange) {
        if (mTask != null) {
            mTask.notifyDirty();
        }
    }

    public void onResume() {
        if (mTask == null) {
            Log.d(TAG, "onResume");
            mTask = new Task();
            mTask.start();
        }
    }

    public void onPause() {
        if (mTask != null) {
            Log.d(TAG, "onPause");
            mTask.terminate();
            mTask = null;
        }
    }

    private class Task extends Thread {
        private volatile boolean mActive = true;
        private volatile boolean mDirty = true;
        private ContentResolver mContentResolver;

        private Task() {
            mContentResolver = GalleryAppImpl.getApplication().getContentResolver();
        }

        synchronized void notifyDirty() {
            mDirty = true;
            notifyAll();
        }

        synchronized void terminate() {
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
                ArrayList<ExtrasItem> unMappedList = getUnMappedList();
                if (!mActive || mDirty) {
                    continue;
                }
                doInsert(unMappedList);
                if (!mActive || mDirty) {
                    continue;
                }
                clearDeletedItems();
                ///////////////////////////end//////////////////////////////
            }
            Log.d(TAG, "run end.");
        }

        private void doInsert(ArrayList<ExtrasItem> unMapped) {
            if (unMapped.size() > 0) {
                Log.d(TAG, "need insert count = " + unMapped.size());
                Calendar c = Calendar.getInstance();
                int timezone_offset = c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET);
                for (ExtrasItem item : unMapped) {
                    if (!mActive || mDirty) {
                        break;
                    }
                    ContentValues values = new ContentValues();
                    values.put(MediaExtras.Extension.Columns._ID, item.id);
                    values.put(MediaExtras.Extension.Columns.TIMEZONE_OFFSET, timezone_offset);
                    mContentResolver.insert(MediaExtras.Extension.Media.CONTENT_URI, values);
                }
            }
        }

        private void clearDeletedItems() {
            String all = getAllMediaList();
            int count = mContentResolver.delete(MediaExtras.Extension.Media.CONTENT_URI,
                    MediaExtras.Extension.Columns._ID + " not in (" + all + ")", null);
            if (count > 0) {
                Log.d(TAG, "deleted count = " + count);
            }
        }

        private String getMappedList() {
            ArrayList<Integer> list = new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            Cursor cursor = null;
            try {
                cursor = mContentResolver.query(MediaExtras.Extension.Media.CONTENT_URI,
                        new String[]{MediaExtras.Extension.Columns._ID}, null, null, null);
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        if (!mActive || mDirty) {
                            break;
                        }
                        list.add(cursor.getInt(0));
                    }
                }
            } catch (Exception ignored) {
            } finally {
                Utils.closeSilently(cursor);
            }
            for (Integer i : list) {
                if (!mActive || mDirty) {
                    break;
                }
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(i);
            }
            return sb.toString();
        }

        private ArrayList<ExtrasItem> getUnMappedList() {
            String mappedList = getMappedList();
            ArrayList<ExtrasItem> list = new ArrayList<>();
            if (!mActive || mDirty) {
                return list;
            }
            Cursor cursor = null;
            try {
                cursor = mContentResolver.query(MediaStore.Files.getContentUri("external"),
                        new String[]{MediaStore.Files.FileColumns._ID},
                        MediaStore.Files.FileColumns.MEDIA_TYPE + " = ? OR "
                                + MediaStore.Files.FileColumns.MEDIA_TYPE + " = ? ) AND ("
                                + MediaStore.Files.FileColumns._ID + " not in (" + mappedList + ")",
                        new String[]{String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE),
                                String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)}, null);
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        if (!mActive || mDirty) {
                            break;
                        }
                        ExtrasItem item = new ExtrasItem();
                        item.id = cursor.getInt(cursor.getColumnIndex(MediaStore.Files.FileColumns._ID));
                        list.add(item);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                Utils.closeSilently(cursor);
            }
            return list;
        }

        private String getAllMediaList() {
            ArrayList<Integer> list = new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            Cursor cursor = null;
            try {
                cursor = mContentResolver.query(MediaStore.Files.getContentUri("external"),
                        new String[]{MediaStore.Files.FileColumns._ID},
                        MediaStore.Files.FileColumns.MEDIA_TYPE + " = ? OR "
                                + MediaStore.Files.FileColumns.MEDIA_TYPE + " = ? ",
                        new String[]{String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE),
                                String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)}, null);
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        if (!mActive || mDirty) {
                            break;
                        }
                        list.add(cursor.getInt(0));
                    }
                }
            } catch (Exception ignored) {
            } finally {
                Utils.closeSilently(cursor);
            }
            for (Integer i : list) {
                if (!mActive || mDirty) {
                    break;
                }
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(i);
            }
            return sb.toString();
        }
    }

    private class ExtrasItem {
        int id;
    }
}
