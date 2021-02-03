package com.android.gallery3d.v2.trash;

import android.content.ContentValues;
import android.provider.MediaStore;
import android.util.Log;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaItem;
import com.sprd.frameworks.StandardFrameworks;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * @author baolin.li
 */
public class TrashStoreValue implements Serializable {
    private static final String TAG = TrashStoreValue.class.getSimpleName();
    private static final long serialVersionUID = -5601508971026540982L;

    private boolean isImage;

    private int _id;
    private String _data;
    private long _size;
    private String _display_name;
    private String mime_type;
    private String title;
    private long date_added;
    private long date_modified;
    private long datetaken;
    private double latitude;
    private double longitude;
    private int orientation;
    private int bucket_id;
    private String bucket_display_name;
    private int width;
    private int height;
    private int is_drm;
    private int file_flag;
    private long duration;
    private String resolution;
    private String album;

    public TrashStoreValue(boolean isImage, ContentValues values) {
        this.isImage = isImage;
        this._id = asInt(values.getAsInteger(MediaStore.Images.ImageColumns._ID));
        this._data = asString(values.getAsString(MediaStore.MediaColumns.DATA));
        this._size = asLong(values.getAsLong(MediaStore.MediaColumns.SIZE));
        this._display_name = asString(values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME));
        this.mime_type = asString(values.getAsString(MediaStore.MediaColumns.MIME_TYPE));
        this.title = asString(values.getAsString(MediaStore.MediaColumns.TITLE));
        this.date_added = asLong(values.getAsLong(MediaStore.MediaColumns.DATE_ADDED));
        this.date_modified = asLong(values.getAsLong(MediaStore.MediaColumns.DATE_MODIFIED));
        this.datetaken = asLong(values.getAsLong(MediaStore.Images.ImageColumns.DATE_TAKEN));
        this.latitude = asDouble(values.getAsDouble(MediaStore.Images.ImageColumns.LATITUDE));
        this.longitude = asDouble(values.getAsDouble(MediaStore.Images.ImageColumns.LONGITUDE));
        this.orientation = asInt(values.getAsInteger(MediaStore.Images.ImageColumns.ORIENTATION));
        this.bucket_id = asInt(values.getAsInteger(MediaStore.Images.ImageColumns.BUCKET_ID));
        this.bucket_display_name = asString(values.getAsString(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME));
        this.width = asInt(values.getAsInteger(MediaStore.MediaColumns.WIDTH));
        this.height = asInt(values.getAsInteger(MediaStore.MediaColumns.HEIGHT));
        if (StandardFrameworks.getInstances().isSupportIsDrm()) {
            this.is_drm = asInt(values.getAsInteger(MediaItem.COLUMN_IS_DRM));
        }
        if (StandardFrameworks.getInstances().isSupportFileFlag()) {
            this.file_flag = asInt(values.getAsInteger(MediaItem.COLUMN_FILE_FLAG));
        }
        this.duration = asLong(values.getAsLong(MediaStore.Video.VideoColumns.DURATION));
        this.resolution = asString(values.getAsString(MediaStore.Video.VideoColumns.RESOLUTION));
        this.album = asString(values.getAsString(MediaStore.Video.VideoColumns.ALBUM));
    }

    public static String asString(String obj) {
        return obj == null ? "" : obj;
    }

    public static int asInt(Integer obj) {
        return obj == null ? 0 : obj;
    }

    public static long asLong(Long obj) {
        return obj == null ? 0 : obj;
    }

    public static double asDouble(Double obj) {
        return obj == null ? 0 : obj;
    }

    public byte[] toByte() {
        byte[] out = null;
        ByteArrayOutputStream bos = null;
        ObjectOutputStream oos = null;
        try {
            bos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(bos);
            oos.writeObject(this);
            out = bos.toByteArray();
        } catch (IOException e) {
            Log.e(TAG, "toByte -- " + e.toString());
        } finally {
            Utils.closeSilently(bos);
            Utils.closeSilently(oos);
        }
        return out;
    }

    public static TrashStoreValue fromByte(byte[] bytes) {
        TrashStoreValue out = null;
        ByteArrayInputStream bis = null;
        ObjectInputStream ois = null;
        try {
            bis = new ByteArrayInputStream(bytes);
            ois = new ObjectInputStream(bis);
            out = (TrashStoreValue) ois.readObject();
        } catch (ClassNotFoundException | IOException e) {
            Log.e(TAG, "fromByte -- " + e.toString());
        } finally {
            Utils.closeSilently(bis);
            Utils.closeSilently(ois);
        }
        return out;
    }

    public static ContentValues values(TrashStoreValue item) {
        ContentValues values = new ContentValues();
        if (item == null) {
            return values;
        }
        if (item.isImage) {
            values.put(MediaStore.Images.ImageColumns._ID, item._id);
            // 0
            values.put(MediaStore.Images.ImageColumns.DATA, item._data);
            // 1
            values.put(MediaStore.Images.ImageColumns.SIZE, item._size);
            // 2
            values.put(MediaStore.Images.ImageColumns.DISPLAY_NAME, item._display_name);
            // 3
            values.put(MediaStore.Images.ImageColumns.MIME_TYPE, item.mime_type);
            // 4
            values.put(MediaStore.Images.ImageColumns.TITLE, item.title);
            // 5
            values.put(MediaStore.Images.ImageColumns.DATE_ADDED, item.date_added);
            // 6
            values.put(MediaStore.Images.ImageColumns.DATE_MODIFIED, item.date_modified);
            // 7
            values.put(MediaStore.Images.ImageColumns.LATITUDE, item.latitude);
            // 8
            values.put(MediaStore.Images.ImageColumns.LONGITUDE, item.longitude);
            // 9
            values.put(MediaStore.Images.ImageColumns.DATE_TAKEN, item.datetaken);
            // 10
            values.put(MediaStore.Images.ImageColumns.ORIENTATION, item.orientation);
            // 11
            values.put(MediaStore.Images.ImageColumns.BUCKET_ID, item.bucket_id);
            // 12
            values.put(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME, item.bucket_display_name);
            // 13
            values.put(MediaStore.Images.ImageColumns.WIDTH, item.width);
            // 14
            values.put(MediaStore.Images.ImageColumns.HEIGHT, item.height);
            // 15
            if (StandardFrameworks.getInstances().isSupportIsDrm()) {
                values.put(MediaItem.COLUMN_IS_DRM, item.is_drm);
            }
            // 16
            if (StandardFrameworks.getInstances().isSupportFileFlag()) {
                values.put(MediaItem.COLUMN_FILE_FLAG, item.file_flag);
            }
        } else {
            values.put(MediaStore.Video.VideoColumns._ID, item._id);
            // 0
            values.put(MediaStore.Video.VideoColumns.DATA, item._data);
            // 1
            values.put(MediaStore.Video.VideoColumns.SIZE, item._size);
            // 2
            values.put(MediaStore.Video.VideoColumns.DISPLAY_NAME, item._display_name);
            // 3
            values.put(MediaStore.Video.VideoColumns.MIME_TYPE, item.mime_type);
            // 4
            values.put(MediaStore.Video.VideoColumns.TITLE, item.title);
            // 5
            values.put(MediaStore.Video.VideoColumns.DATE_ADDED, item.date_added);
            // 6
            values.put(MediaStore.Video.VideoColumns.DATE_MODIFIED, item.date_modified);
            // 7
            values.put(MediaStore.Video.VideoColumns.DURATION, item.duration);
            // 8
            values.put(MediaStore.Video.VideoColumns.RESOLUTION, item.resolution);
            // 9
            values.put(MediaStore.Video.VideoColumns.LATITUDE, item.latitude);
            // 10
            values.put(MediaStore.Video.VideoColumns.LONGITUDE, item.longitude);
            // 11
            values.put(MediaStore.Video.VideoColumns.DATE_TAKEN, item.datetaken);
            // 12
            values.put(MediaStore.Video.VideoColumns.WIDTH, item.width);
            // 13
            values.put(MediaStore.Video.VideoColumns.HEIGHT, item.height);
            // 14
            values.put(MediaStore.Video.VideoColumns.BUCKET_ID, item.bucket_id);
            // 15
            values.put(MediaStore.Video.VideoColumns.ALBUM, item.album);
            // 16
            values.put(MediaStore.Video.VideoColumns.BUCKET_DISPLAY_NAME, item.bucket_display_name);
            // 17
            if (StandardFrameworks.getInstances().isSupportIsDrm()) {
                values.put(MediaItem.COLUMN_IS_DRM, item.is_drm);
            }
        }
        return values;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getMimeType() {
        return mime_type;
    }

    public int getOrientation() {
        return orientation;
    }

    public long getDateModified() {
        return date_modified;
    }

    public long getDuration() {
        return duration;
    }

    public boolean isDrm() {
        return is_drm == 1;
    }

    public long getDatetaken() {
        return datetaken;
    }
}
