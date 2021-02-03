package com.android.gallery3d.v2.trash;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.MediaStore;

import androidx.annotation.NonNull;

import android.util.Log;

import com.android.gallery3d.app.GalleryAppImpl;
import com.android.gallery3d.app.GalleryStorageUtil;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.data.LocalVideo;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.gadget.PhotoAppWidgetProvider;
import com.android.gallery3d.gadget.WidgetDatabaseHelper;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.v2.trash.data.TrashAlbum;
import com.android.gallery3d.v2.trash.data.TrashItem;
import com.android.gallery3d.v2.trash.db.TrashStore;
import com.android.gallery3d.v2.util.SdCardPermission;
import com.sprd.frameworks.StandardFrameworks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author baolin.li
 */
public class TrashManager {
    private static final String TAG = TrashManager.class.getSimpleName();

    private static final String TRASH_DIR_NAME = "trash_files";

    private static final int INDEX_DATA = 0;
    private static final int INDEX_SIZE = 1;
    private static final int INDEX_DISPLAY_NAME = 2;
    private static final int INDEX_MIME_TYPE = 3;
    private static final int INDEX_TITLE = 4;
    private static final int INDEX_DATE_ADDED = 5;
    private static final int INDEX_DATE_MODIFIED = 6;
    private static final int INDEX_LATITUDE = 7;
    private static final int INDEX_LONGITUDE = 8;
    private static final int INDEX_DATE_TAKEN = 9;
    private static final int INDEX_ORIENTATION = 10;
    private static final int INDEX_BUCKET_ID = 11;
    private static final int INDEX_BUCKET_DISPLAY_NAME = 12;
    private static final int INDEX_WIDTH = 13;
    private static final int INDEX_HEIGHT = 14;
    private static final int INDEX_ID = 15;

    private static final ArrayList<String> PROJECTION_ARRAY = new ArrayList<>();

    private static final String[] IMAGE_COLUMNS;

    static {
        PROJECTION_ARRAY.add(MediaStore.Images.ImageColumns.DATA);                //0
        PROJECTION_ARRAY.add(MediaStore.Images.ImageColumns.SIZE);                //1
        PROJECTION_ARRAY.add(MediaStore.Images.ImageColumns.DISPLAY_NAME);        //2
        PROJECTION_ARRAY.add(MediaStore.Images.ImageColumns.MIME_TYPE);           //3
        PROJECTION_ARRAY.add(MediaStore.Images.ImageColumns.TITLE);               //4
        PROJECTION_ARRAY.add(MediaStore.Images.ImageColumns.DATE_ADDED);          //5
        PROJECTION_ARRAY.add(MediaStore.Images.ImageColumns.DATE_MODIFIED);       //6
        PROJECTION_ARRAY.add(MediaStore.Images.ImageColumns.LATITUDE);            //7
        PROJECTION_ARRAY.add(MediaStore.Images.ImageColumns.LONGITUDE);           //8
        PROJECTION_ARRAY.add(MediaStore.Images.ImageColumns.DATE_TAKEN);          //9
        PROJECTION_ARRAY.add(MediaStore.Images.ImageColumns.ORIENTATION);         //10
        PROJECTION_ARRAY.add(MediaStore.Images.ImageColumns.BUCKET_ID);           //11
        PROJECTION_ARRAY.add(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME); //12
        PROJECTION_ARRAY.add(MediaStore.Images.ImageColumns.WIDTH);               //13
        PROJECTION_ARRAY.add(MediaStore.Images.ImageColumns.HEIGHT);              //14
        PROJECTION_ARRAY.add(MediaStore.Images.ImageColumns._ID);                 //15
        if (StandardFrameworks.getInstances().isSupportIsDrm()) {
            PROJECTION_ARRAY.add(MediaItem.COLUMN_IS_DRM);                        //16
        }
        if (StandardFrameworks.getInstances().isSupportFileFlag()) {
            PROJECTION_ARRAY.add(MediaItem.COLUMN_FILE_FLAG);                     //17
        }

        IMAGE_COLUMNS = PROJECTION_ARRAY.toArray(new String[0]);
    }

    private Task mTask;

    private static TrashManager sTrashManager;

    /**
     * 是否正在删除或恢复
     */
    private volatile boolean mIsBusy = false;

    private TrashManager() {
    }

    public static TrashManager getDefault() {
        if (sTrashManager == null) {
            synchronized (TrashManager.class) {
                if (sTrashManager == null) {
                    sTrashManager = new TrashManager();
                }
            }
        }
        return sTrashManager;
    }

    public void recycle(@NonNull MediaItem item) {
        String dir = item.filePath.substring(0, item.filePath.lastIndexOf("/"));
        String bucketDisplayName = dir.substring(dir.lastIndexOf("/") + 1);
        if (item instanceof LocalImage) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.ImageColumns._ID, ((LocalImage) item).id);
            // 0
            values.put(MediaStore.Images.ImageColumns.DATA, item.filePath);
            // 1
            values.put(MediaStore.Images.ImageColumns.SIZE, ((LocalImage) item).fileSize);
            // 2
            values.put(MediaStore.Images.ImageColumns.DISPLAY_NAME, ((LocalImage) item).displayName);
            // 3
            values.put(MediaStore.Images.ImageColumns.MIME_TYPE, ((LocalImage) item).mimeType);
            // 4
            values.put(MediaStore.Images.ImageColumns.TITLE, ((LocalImage) item).caption);
            // 5
            values.put(MediaStore.Images.ImageColumns.DATE_ADDED, ((LocalImage) item).dateAddedInSec);
            // 6
            values.put(MediaStore.Images.ImageColumns.DATE_MODIFIED, ((LocalImage) item).dateModifiedInSec);
            // 7
            values.put(MediaStore.Images.ImageColumns.LATITUDE, ((LocalImage) item).latitude);
            // 8
            values.put(MediaStore.Images.ImageColumns.LONGITUDE, ((LocalImage) item).longitude);
            // 9
            values.put(MediaStore.Images.ImageColumns.DATE_TAKEN, ((LocalImage) item).dateTakenInMs);
            // 10
            values.put(MediaStore.Images.ImageColumns.ORIENTATION, ((LocalImage) item).rotation);
            // 11
            values.put(MediaStore.Images.ImageColumns.BUCKET_ID, ((LocalImage) item).bucketId);
            // 12
            values.put(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME, bucketDisplayName);
            // 13
            values.put(MediaStore.Images.ImageColumns.WIDTH, ((LocalImage) item).width);
            // 14
            values.put(MediaStore.Images.ImageColumns.HEIGHT, ((LocalImage) item).height);
            // 15
            if (StandardFrameworks.getInstances().isSupportIsDrm()) {
                values.put(MediaItem.COLUMN_IS_DRM, ((LocalImage) item).isDrm);
            }
            // 16
            if (StandardFrameworks.getInstances().isSupportFileFlag()) {
                values.put(MediaItem.COLUMN_FILE_FLAG, item.mFileFlag);
            }
            backup(true, values);
        } else if (item instanceof LocalVideo) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.VideoColumns._ID, ((LocalVideo) item).id);
            // 0
            values.put(MediaStore.Video.VideoColumns.DATA, item.filePath);
            // 1
            values.put(MediaStore.Video.VideoColumns.SIZE, ((LocalVideo) item).fileSize);
            // 2
            values.put(MediaStore.Video.VideoColumns.DISPLAY_NAME, ((LocalVideo) item).displayName);
            // 3
            values.put(MediaStore.Video.VideoColumns.MIME_TYPE, ((LocalVideo) item).mimeType);
            // 4
            values.put(MediaStore.Video.VideoColumns.TITLE, ((LocalVideo) item).caption);
            // 5
            values.put(MediaStore.Video.VideoColumns.DATE_ADDED, ((LocalVideo) item).dateAddedInSec);
            // 6
            values.put(MediaStore.Video.VideoColumns.DATE_MODIFIED, ((LocalVideo) item).dateModifiedInSec);
            // 7
            values.put(MediaStore.Video.VideoColumns.DURATION, ((LocalVideo) item).durationInMs);
            // 8
            values.put(MediaStore.Video.VideoColumns.RESOLUTION, ((LocalVideo) item).width + "x" + ((LocalVideo) item).height);
            // 9
            values.put(MediaStore.Video.VideoColumns.LATITUDE, ((LocalVideo) item).latitude);
            // 10
            values.put(MediaStore.Video.VideoColumns.LONGITUDE, ((LocalVideo) item).longitude);
            // 11
            values.put(MediaStore.Video.VideoColumns.DATE_TAKEN, ((LocalVideo) item).dateTakenInMs);
            // 12
            values.put(MediaStore.Video.VideoColumns.WIDTH, ((LocalVideo) item).width);
            // 13
            values.put(MediaStore.Video.VideoColumns.HEIGHT, ((LocalVideo) item).height);
            // 14
            values.put(MediaStore.Video.VideoColumns.BUCKET_ID, ((LocalVideo) item).bucketId);
            // 15
            values.put(MediaStore.Video.VideoColumns.ALBUM, bucketDisplayName);
            // 16
            values.put(MediaStore.Video.VideoColumns.BUCKET_DISPLAY_NAME, bucketDisplayName);
            // 17
            if (StandardFrameworks.getInstances().isSupportIsDrm()) {
                values.put(MediaItem.COLUMN_IS_DRM, ((LocalVideo) item).isDrm);
            }
            backup(false, values);
        }
    }

    public void recycleBurstItem(@NonNull LocalImage item) {
        if (item.getMediaType() != MediaObject.MEDIA_TYPE_IMAGE_BURST_COVER) {
            return;
        }
        Cursor cursor = GalleryAppImpl.getApplication().getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                IMAGE_COLUMNS, "(file_flag=?" + " or file_flag=?) and " + MediaStore.Images.ImageColumns.DATE_TAKEN + "=?",
                new String[]{
                        String.valueOf(LocalImage.IMG_TYPE_MODE_BURST_COVER),
                        String.valueOf(LocalImage.IMG_TYPE_MODE_BURST),
                        String.valueOf(item.getDateInMs())
                }, null);
        ArrayList<ContentValues> valuesArrayList = new ArrayList<>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.ImageColumns._ID, cursor.getString(INDEX_ID));
                // 0
                values.put(MediaStore.Images.ImageColumns.DATA, cursor.getString(INDEX_DATA));
                // 1
                values.put(MediaStore.Images.ImageColumns.SIZE, cursor.getLong(INDEX_SIZE));
                // 2
                values.put(MediaStore.Images.ImageColumns.DISPLAY_NAME, cursor.getString(INDEX_DISPLAY_NAME));
                // 3
                values.put(MediaStore.Images.ImageColumns.MIME_TYPE, cursor.getString(INDEX_MIME_TYPE));
                // 4
                values.put(MediaStore.Images.ImageColumns.TITLE, cursor.getString(INDEX_TITLE));
                // 5
                values.put(MediaStore.Images.ImageColumns.DATE_ADDED, cursor.getLong(INDEX_DATE_ADDED));
                // 6
                values.put(MediaStore.Images.ImageColumns.DATE_MODIFIED, cursor.getLong(INDEX_DATE_MODIFIED));
                // 7
                values.put(MediaStore.Images.ImageColumns.LATITUDE, cursor.getLong(INDEX_LATITUDE));
                // 8
                values.put(MediaStore.Images.ImageColumns.LONGITUDE, cursor.getLong(INDEX_LONGITUDE));
                // 9
                values.put(MediaStore.Images.ImageColumns.DATE_TAKEN, cursor.getLong(INDEX_DATE_TAKEN));
                // 10
                values.put(MediaStore.Images.ImageColumns.ORIENTATION, cursor.getInt(INDEX_ORIENTATION));
                // 11
                values.put(MediaStore.Images.ImageColumns.BUCKET_ID, cursor.getInt(INDEX_BUCKET_ID));
                // 12
                values.put(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME, cursor.getString(INDEX_BUCKET_DISPLAY_NAME));
                // 13
                values.put(MediaStore.Images.ImageColumns.WIDTH, cursor.getInt(INDEX_WIDTH));
                // 14
                values.put(MediaStore.Images.ImageColumns.HEIGHT, cursor.getInt(INDEX_HEIGHT));
                // 15
                if (StandardFrameworks.getInstances().isSupportIsDrm()) {
                    values.put(MediaItem.COLUMN_IS_DRM, cursor.getInt(cursor.getColumnIndex(MediaItem.COLUMN_IS_DRM)));
                }
                // 16
                if (StandardFrameworks.getInstances().isSupportFileFlag()) {
                    values.put(MediaItem.COLUMN_FILE_FLAG, cursor.getInt(cursor.getColumnIndex(MediaItem.COLUMN_FILE_FLAG)));
                }
                valuesArrayList.add(values);
            }
        }
        Utils.closeSilently(cursor);

        for (ContentValues values : valuesArrayList) {
            backup(true, values);
        }
    }

    private void backup(boolean isImage, @NonNull ContentValues values) {
        if (!values.containsKey(MediaStore.MediaColumns.DATA)) {
            return;
        }
        String src = values.getAsString(MediaStore.MediaColumns.DATA);
        String baseDir;
        if (GalleryStorageUtil.isInInternalStorage(src)) {
            baseDir = StandardFrameworks.getInstances().getInternalFilesDir(GalleryAppImpl.getApplication());
        } else {
            baseDir = StandardFrameworks.getInstances().getExternalFilesDir(GalleryAppImpl.getApplication(), src);
            if (baseDir == null) {
                baseDir = StandardFrameworks.getInstances().getInternalFilesDir(GalleryAppImpl.getApplication());
            }
        }
        String dir = baseDir + "/" + TRASH_DIR_NAME;
        mkdir(dir);

        String title = src.substring(src.lastIndexOf("/") + 1);
        int indexOfPoint = title.lastIndexOf(".");
        String pName = title.substring(0, indexOfPoint == -1 ? title.length() : indexOfPoint);
        String sName = indexOfPoint == -1 ? "" : title.substring(indexOfPoint);

        String name = str2MD5(pName + sName);
        int copy = 1;
        while (new File(dir + "/" + name).exists()) {
            name = str2MD5(pName + "-" + (copy++) + sName);
        }

        String dst = dir + "/" + name;
        backup(isImage, src, dst, values);
    }

    private void mkdir(String dir) {
        File file = new File(dir);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    private String str2MD5(String key) {
        String cacheKey;
        try {
            final MessageDigest mDigest = MessageDigest.getInstance("MD5");
            mDigest.update(key.getBytes());
            cacheKey = bytesToHexString(mDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(key.hashCode());
        }
        return cacheKey;
    }

    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    private void backup(boolean isImage, String src, String dst, @NonNull ContentValues values) {
        Log.d(TAG, "backup isImage = " + isImage + ", src = " + src + ", dst = " + dst + ", values = " + values);

        if (copyFileOnly(new File(src), new File(dst))) {
            TrashStoreValue item = new TrashStoreValue(isImage, values);
            ContentValues v = new ContentValues();
            v.put(TrashStore.Local.Columns.LOCAL_PATH, src);
            v.put(TrashStore.Local.Columns.TRASH_FILE_PATH, dst);
            v.put(TrashStore.Local.Columns.DELETED_TIME, System.currentTimeMillis());
            v.put(TrashStore.Local.Columns.IS_IMAGE, isImage ? 1 : 0);
            v.put(TrashStore.Local.Columns.FILE_FLAG, TrashStoreValue.asInt(values.getAsInteger("file_flag")));
            v.put(TrashStore.Local.Columns.MEDIA_STORE_VALUES, item.toByte());
            v.put(TrashStore.Local.Columns.DATE_TAKEN, item.getDatetaken());
            GalleryAppImpl.getApplication().getContentResolver().insert(TrashStore.Local.Media.CONTENT_URI, v);
        }
    }

    private boolean copyFileOnly(File src, File dst) {
        boolean success = true;
        FileInputStream in = null;
        FileOutputStream out = null;
        FileChannel inChannel = null;
        FileChannel outChannel = null;

        String cacheDir = StandardFrameworks.getInstances().getInternalFilesDir(GalleryAppImpl.getApplication()) + "/" + TRASH_DIR_NAME;

        try {
            in = new FileInputStream(src);
            if (GalleryStorageUtil.isInInternalStorage(dst.getAbsolutePath())
                    || dst.getAbsolutePath().startsWith(cacheDir)) {
                out = new FileOutputStream(dst);
            } else {
                //创建文件
                SdCardPermission.mkFile(dst);
                out = (FileOutputStream) SdCardPermission.createExternalOutputStream(dst.getAbsolutePath());
            }
            inChannel = in.getChannel();
            outChannel = out.getChannel();
            //拷贝文件
            inChannel.transferTo(0, inChannel.size(), outChannel);
        } catch (Exception e) {
            success = false;
            Log.e(TAG, "copyFileOnly failed", e);
        } finally {
            Utils.closeSilently(in);
            Utils.closeSilently(out);
            Utils.closeSilently(inChannel);
            Utils.closeSilently(outChannel);
        }

        if (!success && dst.exists()) {
            if (GalleryStorageUtil.isInInternalStorage(dst.getAbsolutePath())
                    || dst.getAbsolutePath().startsWith(cacheDir)) {
                dst.delete();
            } else {
                SdCardPermission.deleteFile(dst.getAbsolutePath());
            }
        }

        return success;
    }

    public static int getBucketId() {
        return GalleryUtils.getBucketId(StandardFrameworks.getInstances().getInternalFilesDir(GalleryAppImpl.getApplication()) + "/" + TRASH_DIR_NAME);
    }

    public void restore(TrashItem item) {
        List<TrashItem> trashItemList = new ArrayList<>();
        trashItemList.add(item);
        //burst image
        if (item.mFileFlag == LocalImage.IMG_TYPE_MODE_BURST_COVER) {
            Cursor cursor = GalleryAppImpl.getApplication().getContentResolver().query(TrashStore.Local.Media.CONTENT_URI,
                    null, TrashStore.Local.Columns.FILE_FLAG + "=? AND "
                            + TrashStore.Local.Columns.DATE_TAKEN + "=?", new String[]{
                            String.valueOf(LocalImage.IMG_TYPE_MODE_BURST),
                            String.valueOf(item.getDateInMs())
                    }, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    int id = cursor.getInt(cursor.getColumnIndex(TrashStore.Local.Columns._ID));
                    Path childPath = TrashItem.ITEM_PATH.getChild(id);
                    trashItemList.add(TrashAlbum.load(childPath, cursor));
                }
            }
            Utils.closeSilently(cursor);
        }

        for (TrashItem trashItem : trashItemList) {
            restore(trashItem.id, trashItem.filePath, trashItem.localPath, trashItem.isImage, trashItem.getStoreValue());
        }
    }

    private void restore(int trashId, String srcPath, String dstPath, boolean isImage, TrashStoreValue storeValue) {
        File src = new File(srcPath);
        if (!src.exists()) {
            GalleryAppImpl.getApplication().getContentResolver().delete(TrashStore.Local.Media.CONTENT_URI,
                    TrashStore.Local.Columns._ID + "=" + trashId, null);
            return;
        }
        File dst = new File(dstPath);
        String dir = dstPath.substring(0, dstPath.lastIndexOf("/"));
        mkdir(dir);
        //先插数据库占位, 若先拷贝文件, 会触发文件扫描, 后续自己再插入数据库时报错
        Uri uri;
        if (isImage) {
            if (Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.Q) {
                uri = Utils.getImageMediaDataBaseUri(GalleryAppImpl.getApplication(), dstPath);
            } else {
                uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            }
        } else {
            if (Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.Q) {
                uri = Utils.getVideoMediaDataBaseUri(GalleryAppImpl.getApplication(), dstPath);
            } else {
                uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            }
        }
        ContentValues values = TrashStoreValue.values(storeValue);
        //恢复需要插入数据库, 使用自动新增_id号, 先取出原始_id
        Integer oldId = values.getAsInteger(MediaStore.Images.ImageColumns._ID);
        values.remove(MediaStore.Images.ImageColumns._ID);
        //插入数据库
        values.put(MediaStore.Images.ImageColumns.IS_PENDING, 1); //置为其他应用不可查得信息
        Uri newUri = GalleryAppImpl.getApplication().getContentResolver().insert(uri, values);
        Log.d(TAG, "restore src : " + src + " to dst : " + dst + ", newUri = " + newUri);
        //
        if (newUri != null && copyFileOnly(src, dst)) {
            src.delete();
            //拷贝成功, 更新 IS_PENDING 记录为 0
            ContentValues updateValues = new ContentValues();
            updateValues.put(MediaStore.Images.ImageColumns.IS_PENDING, 0);
            GalleryAppImpl.getApplication().getContentResolver().update(newUri, updateValues, null, null);
            //删除Trash数据库中记录
            GalleryAppImpl.getApplication().getContentResolver().delete(TrashStore.Local.Media.CONTENT_URI,
                    TrashStore.Local.Columns._ID + "=" + trashId, null);
            //检测小部件数据库中是否需要更新图片Uri, 因为之前添加的为旧的_id,恢复后会找不到指定项
            if (isImage && oldId != null) {
                final AppWidgetManager appWidgetManager = AppWidgetManager
                        .getInstance(GalleryAppImpl.getApplication());
                int[] widgets = null;
                if (appWidgetManager != null) {
                    widgets = appWidgetManager.getAppWidgetIds(new ComponentName(
                            GalleryAppImpl.getApplication(), PhotoAppWidgetProvider.class));
                }
                //检测桌面上有没有小部件
                if (widgets != null && widgets.length > 0) {
                    Log.d(TAG, "we have widgets on launcher, count is " + widgets.length);
                    WidgetDatabaseHelper helper = new WidgetDatabaseHelper(GalleryAppImpl.getApplication());
                    try {
                        helper.checkWidgetSinglePhotoUri(oldId, (int) ContentUris.parseId(newUri));
                    } finally {
                        helper.close();
                    }
                }
            }
        } else {
            //若文件恢复失败, 则删除数据库记录
            if (newUri != null) {
                GalleryAppImpl.getApplication().getContentResolver().delete(newUri, null, null);
            }
            throw new RuntimeException("restore trash failed");
        }
    }

    public void delete(TrashItem item) {
        List<TrashItem> trashItemList = new ArrayList<>();
        trashItemList.add(item);
        //burst image
        if (item.mFileFlag == LocalImage.IMG_TYPE_MODE_BURST_COVER) {
            Cursor cursor = GalleryAppImpl.getApplication().getContentResolver().query(TrashStore.Local.Media.CONTENT_URI,
                    null, TrashStore.Local.Columns.FILE_FLAG + "=? AND "
                            + TrashStore.Local.Columns.DATE_TAKEN + "=?", new String[]{
                            String.valueOf(LocalImage.IMG_TYPE_MODE_BURST),
                            String.valueOf(item.getDateInMs())
                    }, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    int id = cursor.getInt(cursor.getColumnIndex(TrashStore.Local.Columns._ID));
                    Path childPath = TrashItem.ITEM_PATH.getChild(id);
                    trashItemList.add(TrashAlbum.load(childPath, cursor));
                }
            }
            Utils.closeSilently(cursor);
        }

        for (TrashItem trashItem : trashItemList) {
            delete(trashItem.id, trashItem.filePath);
        }
    }

    private void delete(int id, String path) {
        File src = new File(path);
        if (!src.exists()) {
            Log.e(TAG, "delete " + path + " not exists.");
            GalleryAppImpl.getApplication().getContentResolver().delete(TrashStore.Local.Media.CONTENT_URI,
                    TrashStore.Local.Columns._ID + "=" + id, null);
            return;
        }
        if (src.delete()) {
            GalleryAppImpl.getApplication().getContentResolver().delete(TrashStore.Local.Media.CONTENT_URI,
                    TrashStore.Local.Columns._ID + "=" + id, null);
        }
    }

    public void onResume() {
        Log.d(TAG, "onResume");
        if (mTask == null) {
            mTask = new Task();
            mTask.start();
        }
    }

    public void onPause() {
        Log.d(TAG, "onPause");
        if (mTask != null) {
            mTask.pause();
            mTask = null;
        }
    }

    private class Task extends Thread {
        private boolean mPaused;
        private List<Item> mItems = new ArrayList<>();

        public void pause() {
            this.mPaused = true;
        }

        @Override
        public void run() {
            Log.d(TAG, "Task run B.");
            long t = System.currentTimeMillis();
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            Cursor cursor = null;
            try {
                cursor = GalleryAppImpl.getApplication().getContentResolver().query(TrashStore.Local.Media.CONTENT_URI,
                        new String[]{
                                TrashStore.Local.Columns._ID,
                                TrashStore.Local.Columns.DELETED_TIME,
                                TrashStore.Local.Columns.TRASH_FILE_PATH
                        }, null, null, null);
            } catch (Exception e) {
            }

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    if (mPaused) {
                        break;
                    }
                    mItems.add(new Item(cursor.getInt(cursor.getColumnIndex(TrashStore.Local.Columns._ID)),
                            cursor.getLong(cursor.getColumnIndex(TrashStore.Local.Columns.DELETED_TIME)),
                            cursor.getString(cursor.getColumnIndex(TrashStore.Local.Columns.TRASH_FILE_PATH))));
                }
            }

            Utils.closeSilently(cursor);

            for (Item item : mItems) {
                if (mPaused) {
                    break;
                }
                if (TrashItem.getLeftDay(item.deletedTime) <= 0) {
                    delete(item.id, item.path);
                }
            }

            mItems.clear();
            Log.d(TAG, "Task run E. cost " + (System.currentTimeMillis() - t) + " ms");
        }

        private class Item {
            int id;
            long deletedTime;
            String path;

            Item(int id, long deletedTime, String path) {
                this.id = id;
                this.deletedTime = deletedTime;
                this.path = path;
            }
        }
    }

    /**
     * 是否正在删除或恢复
     *
     * @return true if 正在删除或恢复
     */
    public boolean isBusy() {
        return mIsBusy;
    }

    /**
     * @param busy 是否正在删除或恢复
     */
    public void setBusy(boolean busy) {
        this.mIsBusy = busy;
    }
}
