package com.android.gallery3d.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;

import com.android.gallery3d.ui.SelectionManager;
import com.android.gallery3d.util.MediaSetUtils;
import com.sprd.frameworks.StandardFrameworks;

import java.util.HashSet;
import java.util.Set;

public class SprdMediaLoader {
    private static final String TAG = SprdMediaLoader.class.getSimpleName();
    public static final boolean RE_QUERY = true;

    public interface MediaConsumer {
        void consume(Path path, long dateInMs, long modifyDateInMs, double latitude, double longtitude);
    }

    public static void enumerateTotalMedias(Context context, MediaConsumer consumer) {
        enumerateTotalImages(context, consumer);
        enumerateTotalVideos(context, consumer);
    }

    public static void enumerateTotalVideos(Context context, MediaConsumer consumer) {
        if (consumer == null) {
            return;
        }
        String whereClause = whereClauseForHideAlbums(context);
        Cursor cursor;
        int id;
        cursor = context.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                new String[]{
                        MediaStore.Video.VideoColumns._ID,
                        MediaStore.Video.VideoColumns.DATE_TAKEN,
                        MediaStore.Video.VideoColumns.LATITUDE,
                        MediaStore.Video.VideoColumns.LONGITUDE,
                        MediaStore.Images.ImageColumns.DATE_MODIFIED,
                }, whereClause, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                id = cursor.getInt(cursor.getColumnIndex(MediaStore.Video.VideoColumns._ID));
                consumer.consume(Path.fromString("/local/video/item/" + id),
                        cursor.getLong(cursor.getColumnIndex(MediaStore.Video.VideoColumns.DATE_TAKEN)),
                        cursor.getLong(cursor.getColumnIndex(MediaStore.Video.VideoColumns.DATE_MODIFIED)),
                        cursor.getDouble(cursor.getColumnIndex(MediaStore.Video.VideoColumns.LATITUDE)),
                        cursor.getDouble(cursor.getColumnIndex(MediaStore.Video.VideoColumns.LONGITUDE)));
            }
        }
        if (cursor != null) {
            cursor.close();
        }
    }

    private static void enumerateTotalImages(Context context, MediaConsumer consumer) {
        if (consumer == null) {
            return;
        }
        String whereClause = whereClauseForHideAlbums(context);
        Cursor cursor;
        int id;
        if (StandardFrameworks.getInstances().isSupportBurstImage()) {
            if (whereClause == null) {
                whereClause = "(file_flag != " + LocalImage.IMG_TYPE_MODE_BURST + " or file_flag is null)";
            } else {
                whereClause += ") and (file_flag != " + LocalImage.IMG_TYPE_MODE_BURST + " or file_flag is null";
            }
        }
        cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{
                        MediaStore.Images.ImageColumns._ID,
                        MediaStore.Images.ImageColumns.DATE_TAKEN,
                        MediaStore.Images.ImageColumns.LATITUDE,
                        MediaStore.Images.ImageColumns.LONGITUDE,
                        MediaStore.Images.ImageColumns.DATE_MODIFIED,
                }, whereClause, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                id = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.ImageColumns._ID));
                consumer.consume(Path.fromString("/local/image/item/" + id),
                        cursor.getLong(cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_TAKEN)),
                        cursor.getLong(cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_MODIFIED)),
                        cursor.getDouble(cursor.getColumnIndex(MediaStore.Images.ImageColumns.LATITUDE)),
                        cursor.getDouble(cursor.getColumnIndex(MediaStore.Images.ImageColumns.LONGITUDE)));
            }
        }
        if (cursor != null) {
            cursor.close();
        }
    }

    public static void enumerateLocalImages(Context context, MediaConsumer consumer) {
        if (consumer == null) {
            return;
        }
        String whereClause = MediaStore.Images.ImageColumns.BUCKET_ID + " = ? or " + MediaStore.Images.ImageColumns.BUCKET_ID +
                " = ? ";
        Cursor cursor;
        int id;
        if (StandardFrameworks.getInstances().isSupportBurstImage()) {
            whereClause += ") and (file_flag != " + LocalImage.IMG_TYPE_MODE_BURST + " or file_flag is null";
        }
        String[] whereArgs = new String[]{
                String.valueOf(MediaSetUtils.CAMERA_BUCKET_ID), //internal DCIM/Camera path
                String.valueOf(MediaSetUtils.EXTERNAL_CAMERA_BUCKET_ID)                      //External DCIM/CAmera path
        };
        cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{
                        MediaStore.Images.ImageColumns._ID,
                        MediaStore.Images.ImageColumns.DATE_TAKEN,
                        MediaStore.Images.ImageColumns.LATITUDE,
                        MediaStore.Images.ImageColumns.LONGITUDE,
                        MediaStore.Images.ImageColumns.DATE_MODIFIED,
                }, whereClause, whereArgs, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                id = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.ImageColumns._ID));
                consumer.consume(Path.fromString("/local/image/item/" + id),
                        cursor.getLong(cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_TAKEN)),
                        cursor.getLong(cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_MODIFIED)),
                        cursor.getDouble(cursor.getColumnIndex(MediaStore.Images.ImageColumns.LATITUDE)),
                        cursor.getDouble(cursor.getColumnIndex(MediaStore.Images.ImageColumns.LONGITUDE)));
            }
        }
        if (cursor != null) {
            cursor.close();
        }
    }

    private static String whereClauseForHideAlbums(Context context) {
        String whereClause = null;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> localBucketIdSet = DataManager.getLocalAlbumBuckedIdSet();
        Set<String> otherBucketIdSet = new HashSet<String>();
        for (String s : prefs.getStringSet(SelectionManager.KEY_SELECT_ALBUM_ITEMS, new HashSet<String>())) {
            otherBucketIdSet.add(s.substring(s.lastIndexOf("/") + 1));
        }
        boolean isOnlyLocalAlbums = prefs.getBoolean(SelectionManager.KEY_SELECT_LOCAL_ALBUMS, false);
        boolean isHideOtherAlbums = prefs.getBoolean(SelectionManager.KEY_SELECT_ALBUM_FLAG, false);

        if (isOnlyLocalAlbums && localBucketIdSet.size() > 0) {
            StringBuilder sb = new StringBuilder("bucket_id in (");
            for (String bucket : localBucketIdSet) {
                sb.append(bucket + ",");
            }
            sb.deleteCharAt(sb.length() - 1);
            sb.append(")");
            whereClause = sb.toString();
        } else if (isHideOtherAlbums && otherBucketIdSet.size() > 0) {
            StringBuilder sb = new StringBuilder("bucket_id not in (");
            for (String bucket : otherBucketIdSet) {
                sb.append(bucket + ",");
            }
            sb.deleteCharAt(sb.length() - 1);
            sb.append(")");
            whereClause = sb.toString();
        }
        Log.d(TAG, "whereClauseForHideAlbums = " + whereClause);
        return whereClause;
    }
}
